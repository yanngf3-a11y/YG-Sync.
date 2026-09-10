package com.ygsync.controller.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ygsync.controller.data.Receiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class ControllerSyncService : Service() {

    companion object {

        private const val TAG =
            "YG_SYNC_SERVICE"

        private const val CHANNEL_ID =
            "ygsync_controller"

        private const val CHANNEL_NAME =
            "YG Sync Controller"

        private const val NOTIFICATION_ID =
            8765

        private const val PING_INTERVAL_MS =
            5000L

        private const val RECONNECT_DELAY_MS =
            3000L

        @Volatile
        private var instance:
                ControllerSyncService? = null

        fun getInstance():
                ControllerSyncService? {
            return instance
        }
    }

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )

    private var maintenanceJob:
            Job? = null

    private val connections =
        ConcurrentHashMap<
                String,
                ReceiverConnection
                >()

    private val registeredReceivers =
        ConcurrentHashMap<
                String,
                Receiver
                >()

    private val _receivers =
        MutableStateFlow<
                List<Receiver>
                >(emptyList())

    val receivers:
            StateFlow<List<Receiver>> =
        _receivers.asStateFlow()

    private val _connectionStates =
        MutableStateFlow<
                Map<String, Boolean>
                >(emptyMap())

    val connectionStates:
            StateFlow<Map<String, Boolean>> =
        _connectionStates.asStateFlow()

    private val _latencies =
        MutableStateFlow<
                Map<String, Long>
                >(emptyMap())

    val latencies:
            StateFlow<Map<String, Long>> =
        _latencies.asStateFlow()

    private val _diagnostic =
        MutableStateFlow("YG Sync detenido")

    val diagnostic:
            StateFlow<String> =
        _diagnostic.asStateFlow()

    private val _started =
        MutableStateFlow(false)

    val started:
            StateFlow<Boolean> =
        _started.asStateFlow()

    override fun onCreate() {
        super.onCreate()

        instance = this

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        _started.value = true

        updateDiagnostic(
            "YG SYNC — SERVICIO ACTIVO"
        )

        startMaintenanceLoop()

        Log.d(
            TAG,
            "Servicio iniciado"
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.d(
            TAG,
            "onStartCommand()"
        )

        return START_STICKY
    }

    fun registerReceiver(
        receiver: Receiver
    ) {

        registeredReceivers[
            receiver.id
        ] = receiver

        updateReceiverList()

        serviceScope.launch {
            connectToReceiver(
                receiver
            )
        }
    }

    fun registerReceivers(
        receivers: List<Receiver>
    ) {

        receivers.forEach {
            registeredReceivers[
                it.id
            ] = it
        }

        updateReceiverList()

        receivers.forEach {
            serviceScope.launch {
                connectToReceiver(it)
            }
        }
    }

    private suspend fun connectToReceiver(
        receiver: Receiver
    ) {

        val existing =
            connections[receiver.id]

        if (
            existing != null &&
            existing.isConnected()
        ) {
            updateConnectionState(
                receiver.id,
                true
            )

            return
        }

        updateDiagnostic(
            "Conectando a ${receiver.name}..."
        )

        Log.d(
            TAG,
            "Conectando a "
                    + receiver.address
                    + ":"
                    + receiver.port
        )

        val connection =
            ReceiverConnection(
                receiver.address,
                receiver.port
            )

        val success =
            try {
                connection.connect()
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error conectando a ${receiver.id}",
                    e
                )
                false
            }

        if (!success) {

            connection.disconnect()

            connections.remove(
                receiver.id
            )

            updateConnectionState(
                receiver.id,
                false
            )

            updateDiagnostic(
                "No se pudo conectar a ${receiver.name}"
            )

            return
        }

        connections[
            receiver.id
        ] = connection

        val pingStart =
            System.currentTimeMillis()

        val pingSuccess =
            try {
                connection.ping()
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error en ping inicial",
                    e
                )
                false
            }

        val latency =
            System.currentTimeMillis()
                    - pingStart

        if (!pingSuccess) {

            Log.e(
                TAG,
                "Ping falló para ${receiver.name}"
            )

            connection.disconnect()

            connections.remove(
                receiver.id
            )

            updateConnectionState(
                receiver.id,
                false
            )

            updateDiagnostic(
                "Conexión rechazada por ${receiver.name}"
            )

            return
        }

        _latencies.updateValue(
            receiver.id,
            latency
        )

        updateConnectionState(
            receiver.id,
            true
        )

        updateDiagnostic(
            "Conectado: ${receiver.name}"
        )

        Log.d(
            TAG,
            "Conexión establecida con ${receiver.name}"
        )
    }

    fun sendCommand(
        command: String
    ): Boolean {

        var successCount =
            0

        connections.forEach { (id, connection) ->

            if (!connection.isConnected()) {
                return@forEach
            }

            try {

                if (connection.send(command)) {
                    successCount++
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Error enviando comando a $id",
                    e
                )
            }
        }

        return successCount > 0
    }

    fun sendCommandAsync(
        command: String,
        callback: (
            success: Int,
            total: Int
        ) -> Unit
    ) {

        serviceScope.launch {

            var successCount =
                0

            var total =
                0

            connections.forEach { (id, connection) ->

                if (!connection.isConnected()) {
                    return@forEach
                }

                total++

                try {

                    if (connection.send(command)) {
                        successCount++
                    }

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Error enviando a $id",
                        e
                    )
                }
            }

            launch(
                Dispatchers.Main
            ) {
                callback(
                    successCount,
                    total
                )
            }
        }
    }

    suspend fun loadVideoAndWaitReady(
        videoId: String
    ): Boolean {

        val cleanVideoId =
            videoId.trim()

        if (cleanVideoId.isEmpty()) {
            return false
        }

        val activeConnections =
            connections.values
                .filter {
                    it.isConnected()
                }

        if (activeConnections.isEmpty()) {

            updateDiagnostic(
                "No hay receptores conectados"
            )

            return false
        }

        updateDiagnostic(
            "Cargando video en ${activeConnections.size} receptor(es)..."
        )

        val results =
            activeConnections.map { connection ->

                serviceScope.asyncResult {

                    try {
                        connection
                            .loadVideoAndWaitReady(
                                cleanVideoId
                            )
                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Error cargando $cleanVideoId",
                            e
                        )

                        false
                    }
                }
            }

        val readyCount =
            results.count {
                it.await()
            }

        val success =
            readyCount ==
                    activeConnections.size

        if (success) {

            updateDiagnostic(
                "VIDEO READY — $cleanVideoId"
            )

        } else {

            updateDiagnostic(
                "VIDEO READY: $readyCount/"
                        + activeConnections.size
            )
        }

        return success
    }

    fun getConnection(
        receiverId: String
    ): ReceiverConnection? {
        return connections[
            receiverId
        ]
    }

    fun isReceiverConnected(
        receiverId: String
    ): Boolean {

        return connections[
            receiverId
        ]?.isConnected() == true
    }

    fun disconnectReceiver(
        receiverId: String
    ) {

        try {
            connections[
                receiverId
            ]?.disconnect()
        } catch (_: Exception) {
        }

        connections.remove(
            receiverId
        )

        updateConnectionState(
            receiverId,
            false
        )
    }

    fun stopService() {

        maintenanceJob?.cancel()

        connections.values.forEach {
            try {
                it.disconnect()
            } catch (_: Exception) {
            }
        }

        connections.clear()

        stopSelf()
    }

    private fun startMaintenanceLoop() {

        maintenanceJob?.cancel()

        maintenanceJob =
            serviceScope.launch {

                while (isActive) {

                    delay(
                        PING_INTERVAL_MS
                    )

                    maintainConnections()
                }
            }
    }

    private suspend fun maintainConnections() {

        registeredReceivers
            .values
            .forEach { receiver ->

                val connection =
                    connections[
                        receiver.id
                    ]

                if (
                    connection == null ||
                    !connection.isConnected()
                ) {

                    connectToReceiver(
                        receiver
                    )

                    return@forEach
                }

                val start =
                    System.currentTimeMillis()

                val success =
                    try {
                        connection.ping()
                    } catch (e: Exception) {
                        false
                    }

                val latency =
                    System.currentTimeMillis()
                            - start

                if (success) {

                    _latencies.updateValue(
                        receiver.id,
                        latency
                    )

                    updateConnectionState(
                        receiver.id,
                        true
                    )

                } else {

                    Log.d(
                        TAG,
                        "Ping falló: ${receiver.name}"
                    )

                    try {
                        connection.disconnect()
                    } catch (_: Exception) {
                    }

                    connections.remove(
                        receiver.id
                    )

                    updateConnectionState(
                        receiver.id,
                        false
                    )
                }
            }
    }

    private fun updateConnectionState(
        receiverId: String,
        connected: Boolean
    ) {

        _connectionStates.update {
            it.toMutableMap().apply {
                this[
                    receiverId
                ] = connected
            }
        }

        updateReceiverList()
    }

    private fun updateReceiverList() {

        val updated =
            registeredReceivers
                .values
                .map { receiver ->

                    receiver.copy(
                        connected =
                            connections[
                                receiver.id
                            ]?.isConnected() == true,

                        latency =
                            _latencies.value[
                                receiver.id
                            ] ?: 0L
                    )
                }
                .sortedBy {
                    it.name
                }

        _receivers.value =
            updated
    }

    private fun updateDiagnostic(
        message: String
    ) {

        _diagnostic.value =
            message

        Log.d(
            TAG,
            message
        )
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager
                    .IMPORTANCE_LOW
            )

        channel.description =
            "Servicio de sincronización YG Sync"

        channel.setShowBadge(false)

        manager.createNotificationChannel(
            channel
        )
    }

    private fun createNotification():
            Notification {

        return NotificationCompat
            .Builder(
                this,
                CHANNEL_ID
            )
            .setContentTitle(
                "YG Sync Controller"
            )
            .setContentText(
                "Controlando receptores"
            )
            .setSmallIcon(
                android.R.drawable.ic_media_play
            )
            .setOngoing(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .build()
    }

    override fun onDestroy() {

        Log.d(
            TAG,
            "onDestroy()"
        )

        maintenanceJob?.cancel()

        connections.values.forEach {
            try {
                it.disconnect()
            } catch (_: Exception) {
            }
        }

        connections.clear()

        _started.value =
            false

        instance = null

        serviceScope.coroutineContext
            .cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    private fun <T> CoroutineScope.asyncResult(
        block: suspend () -> T
    ): kotlinx.coroutines.Deferred<T> {
        return kotlinx.coroutines.async(
            Dispatchers.IO
        ) {
            block()
        }
    }

    private fun <K, V> MutableStateFlow<Map<K, V>>.updateValue(
        key: K,
        value: V
    ) {
        this.value =
            this.value.toMutableMap().apply {
                this[key] = value
            }
    }
}
