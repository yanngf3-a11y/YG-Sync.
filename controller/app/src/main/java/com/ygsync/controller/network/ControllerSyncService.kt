package com.ygsync.controller.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ygsync.controller.R
import com.ygsync.controller.data.Receiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ControllerSyncService : Service() {

    companion object {
        const val ACTION_START = "com.ygsync.controller.action.START"
        private const val CHANNEL_ID = "ygsync_controller"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        private var instance: ControllerSyncService? = null

        fun getInstance(): ControllerSyncService? = instance
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(
        Dispatchers.Main.immediate + serviceJob
    )

    private val connections =
        ConcurrentHashMap<String, ReceiverConnection>()

    private val _receiverList = MutableStateFlow<List<Receiver>>(emptyList())
    val receiverList: StateFlow<List<Receiver>> = _receiverList.asStateFlow()

    private val _connectionStates =
        MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val connectionStates: StateFlow<Map<String, Boolean>> =
        _connectionStates.asStateFlow()

    private val _latencies =
        MutableStateFlow<Map<String, Long>>(emptyMap())
    val latencies: StateFlow<Map<String, Long>> =
        _latencies.asStateFlow()

    private val _diagnostic =
        MutableStateFlow("YG SYNC — Servicio iniciado")
    val diagnostic: StateFlow<String> =
        _diagnostic.asStateFlow()

    override fun onCreate() {
        super.onCreate()

        instance = this

        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            createNotification("YG Sync Controller activo")
        )

        updateDiagnostic("YG SYNC — CONTROLADOR ACTIVO")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_START,
            null -> {
                updateDiagnostic("YG SYNC — SERVICIO LISTO")
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        connections.values.forEach {
            try {
                it.disconnect()
            } catch (_: Exception) {
            }
        }

        connections.clear()

        serviceJob.cancel()

        instance = null

        super.onDestroy()
    }

    /**
     * Registra una pantalla y crea su conexión persistente.
     */
    fun registerReceiver(receiver: Receiver) {

        val existing = _receiverList.value
            .firstOrNull { it.id == receiver.id }

        if (existing == null) {
            _receiverList.value =
                _receiverList.value + receiver
        } else {
            _receiverList.value =
                _receiverList.value.map {
                    if (it.id == receiver.id) receiver else it
                }
        }

        if (!connections.containsKey(receiver.id)) {

            val connection = ReceiverConnection(
                receiver = receiver,
                onConnected = {
                    updateConnectionState(
                        receiver.id,
                        true
                    )

                    updateDiagnostic(
                        "YG SYNC — CONECTADO: ${receiver.name}"
                    )
                },
                onDisconnected = {
                    updateConnectionState(
                        receiver.id,
                        false
                    )

                    updateDiagnostic(
                        "YG SYNC — DESCONECTADO: ${receiver.name}"
                    )
                },
                onLatency = { latency ->
                    updateLatency(
                        receiver.id,
                        latency
                    )
                },
                onDiagnostic = { message ->
                    updateDiagnostic(message)
                }
            )

            connections[receiver.id] = connection

            connectReceiver(receiver.id)
        }
    }

    /**
     * Conecta una pantalla sin bloquear la interfaz.
     */
    private fun connectReceiver(receiverId: String) {

        serviceScope.launch(Dispatchers.IO) {

            val connection = connections[receiverId]
                ?: return@launch

            try {

                updateDiagnostic(
                    "YG SYNC — CONECTANDO: $receiverId"
                )

                connection.connect()

            } catch (e: Exception) {

                updateConnectionState(
                    receiverId,
                    false
                )

                updateDiagnostic(
                    "YG SYNC — ERROR DE CONEXIÓN: ${e.message ?: "desconocido"}"
                )
            }
        }
    }

    /**
     * Envía un comando a una pantalla.
     */
    fun sendCommand(
        receiverId: String,
        command: String
    ): Boolean {

        val connection =
            connections[receiverId]
                ?: return false

        return try {

            connection.send(command)

            true

        } catch (e: Exception) {

            updateDiagnostic(
                "YG SYNC — ERROR ENVIANDO A $receiverId: ${e.message}"
            )

            false
        }
    }

    /**
     * Envía un comando a todas las pantallas conectadas.
     */
    fun sendCommandAsync(
        command: String,
        callback: (Boolean, Int) -> Unit
    ) {

        serviceScope.launch {

            val receivers =
                _receiverList.value.toList()

            if (receivers.isEmpty()) {

                callback(false, 0)

                updateDiagnostic(
                    "YG SYNC — NO HAY PANTALLAS"
                )

                return@launch
            }

            val results =
                withContext(Dispatchers.IO) {

                    receivers.map { receiver ->

                        async {

                            val connection =
                                connections[receiver.id]

                            if (
                                connection == null ||
                                !connection.isConnected()
                            ) {
                                false
                            } else {
                                try {
                                    connection.send(command)
                                    true
                                } catch (_: Exception) {
                                    false
                                }
                            }
                        }

                    }.awaitAll()
                }

            val successful =
                results.count { it }

            callback(
                successful == receivers.size,
                successful
            )

            updateDiagnostic(
                "YG SYNC — COMANDO ENVIADO: $successful/${receivers.size}"
            )
        }
    }

    /**
     * LOAD VIDEO:
     *
     * Envía OPEN a todas las pantallas al mismo tiempo
     * y espera a que cada receptor confirme READY.
     */
    suspend fun loadVideoAndWaitReady(
        videoId: String
    ): Boolean {

        val cleanVideoId =
            videoId.trim()

        if (cleanVideoId.isEmpty()) {

            updateDiagnostic(
                "YG SYNC — VIDEO ID VACÍO"
            )

            return false
        }

        val receivers =
            _receiverList.value.toList()

        if (receivers.isEmpty()) {

            updateDiagnostic(
                "YG SYNC — NO HAY PANTALLAS CONECTADAS"
            )

            return false
        }

        updateDiagnostic(
            "YG SYNC — CARGANDO VIDEO: $cleanVideoId"
        )

        val results =
            withContext(Dispatchers.IO) {

                receivers.map { receiver ->

                    async {

                        val connection =
                            connections[receiver.id]

                        if (
                            connection == null ||
                            !connection.isConnected()
                        ) {
                            false
                        } else {
                            try {

                                connection
                                    .loadVideoAndWaitReady(
                                        cleanVideoId
                                    )

                            } catch (e: Exception) {

                                updateDiagnostic(
                                    "YG SYNC — ERROR ${receiver.name}: ${e.message}"
                                )

                                false
                            }
                        }
                    }

                }.awaitAll()
            }

        val readyCount =
            results.count { it }

        val allReady =
            readyCount == receivers.size

        if (allReady) {

            updateDiagnostic(
                "YG SYNC — READY: $readyCount/${receivers.size}"
            )

        } else {

            updateDiagnostic(
                "YG SYNC — READY INCOMPLETO: $readyCount/${receivers.size}"
            )
        }

        return allReady
    }

    /**
     * Envía PLAY simultáneamente.
     */
    fun playAll() {

        sendCommandAsync(
            command = "PLAY"
        ) { _, total ->

            updateDiagnostic(
                "YG SYNC — PLAY ENVIADO A $total PANTALLAS"
            )
        }
    }

    /**
     * Envía PAUSE simultáneamente.
     */
    fun pauseAll() {

        sendCommandAsync(
            command = "PAUSE"
        ) { _, total ->

            updateDiagnostic(
                "YG SYNC — PAUSE ENVIADO A $total PANTALLAS"
            )
        }
    }

    /**
     * Envía STOP simultáneamente.
     */
    fun stopAll() {

        sendCommandAsync(
            command = "STOP"
        ) { _, total ->

            updateDiagnostic(
                "YG SYNC — STOP ENVIADO A $total PANTALLAS"
            )
        }
    }

    /**
     * Solicita estado a todas las pantallas.
     */
    fun getStatusAll() {

        sendCommandAsync(
            command = "GET_STATUS"
        ) { _, total ->

            updateDiagnostic(
                "YG SYNC — STATUS SOLICITADO: $total PANTALLAS"
            )
        }
    }

    /**
     * Desconecta una pantalla.
     */
    fun disconnectReceiver(
        receiverId: String
    ) {

        try {
            connections[receiverId]?.disconnect()
        } catch (_: Exception) {
        }

        connections.remove(receiverId)

        _receiverList.value =
            _receiverList.value.filter {
                it.id != receiverId
            }

        _connectionStates.value =
            _connectionStates.value - receiverId

        _latencies.value =
            _latencies.value - receiverId

        updateDiagnostic(
            "YG SYNC — PANTALLA ELIMINADA: $receiverId"
        )
    }

    /**
     * Devuelve una conexión concreta.
     */
    fun getConnection(
        receiverId: String
    ): ReceiverConnection? {
        return connections[receiverId]
    }

    /**
     * Comprueba si una pantalla está conectada.
     */
    fun isConnected(
        receiverId: String
    ): Boolean {

        return connections[receiverId]
            ?.isConnected()
            ?: false
    }

    private fun updateConnectionState(
        receiverId: String,
        connected: Boolean
    ) {

        _connectionStates.value =
            _connectionStates.value.toMutableMap().apply {
                this[receiverId] = connected
            }

        _receiverList.value =
            _receiverList.value.map {

                if (it.id == receiverId) {
                    it.copy(
                        connected = connected
                    )
                } else {
                    it
                }
            }
    }

    private fun updateLatency(
        receiverId: String,
        latency: Long
    ) {

        _latencies.value =
            _latencies.value.toMutableMap().apply {
                this[receiverId] = latency
            }

        _receiverList.value =
            _receiverList.value.map {

                if (it.id == receiverId) {
                    it.copy(
                        latency = latency
                    )
                } else {
                    it
                }
            }
    }

    private fun updateDiagnostic(
        message: String
    ) {

        _diagnostic.value = message
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "YG Sync Controller",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {

                    description =
                        "Conexión permanente con las pantallas YG Sync"

                    setShowBadge(false)
                }

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        text: String
    ): Notification {

        return NotificationCompat
            .Builder(
                this,
                CHANNEL_ID
            )
            .setContentTitle(
                "YG Sync Controller"
            )
            .setContentText(text)
            .setSmallIcon(
                R.drawable.yg_sync_control
            )
            .setOngoing(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .build()
    }
}
