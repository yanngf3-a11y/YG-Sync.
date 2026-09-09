package com.ygsync.controller.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ygsync.controller.data.Receiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ControllerSyncService : Service() {

    companion object {

        const val ACTION_START =
            "com.ygsync.controller.START_SYNC"

        const val ACTION_STOP =
            "com.ygsync.controller.STOP_SYNC"

        private const val CHANNEL_ID =
            "ygsync_controller"

        private const val NOTIFICATION_ID =
            8765

        private const val PING_INTERVAL_MS =
            5000L

        private var instance:
            ControllerSyncService? = null

        fun getInstance():
            ControllerSyncService? =
            instance
    }

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.Main.immediate
        )

    private val connections =
        ConcurrentHashMap<String, ReceiverConnection>()

    private val receivers =
        ConcurrentHashMap<String, Receiver>()

    private val _connectionStates =
        MutableStateFlow<Map<String, Boolean>>(
            emptyMap()
        )

    val connectionStates:
        StateFlow<Map<String, Boolean>> =
        _connectionStates.asStateFlow()

    private val _latencies =
        MutableStateFlow<Map<String, Long>>(
            emptyMap()
        )

    val latencies:
        StateFlow<Map<String, Long>> =
        _latencies.asStateFlow()

    private val _receivers =
        MutableStateFlow<List<Receiver>>(
            emptyList()
        )

    val receiverList:
        StateFlow<List<Receiver>> =
        _receivers.asStateFlow()

    private val _diagnostic =
        MutableStateFlow(
            "Preparando conexión..."
        )

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
            createNotification(
                "YG Sync activo"
            )
        )

        _started.value = true

        _diagnostic.value =
            "🟢 Servicio de conexión activo"

        startPingLoop()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {
                stopService()
                return START_NOT_STICKY
            }

            ACTION_START,
            null -> {
                _diagnostic.value =
                    "🟢 YG Sync manteniendo conexión"
            }
        }

        return START_STICKY
    }

    fun registerReceiver(
        receiver: Receiver
    ) {

        receivers[receiver.id] =
            receiver

        publishReceivers()

        connectToReceiver(
            receiver
        )
    }

    fun registerReceivers(
        discovered: List<Receiver>
    ) {

        for (receiver in discovered) {

            receivers[receiver.id] =
                receiver
        }

        publishReceivers()

        for (receiver in discovered) {

            connectToReceiver(
                receiver
            )
        }
    }

    private fun connectToReceiver(
        receiver: Receiver
    ) {

        val existing =
            connections[receiver.id]

        if (
            existing != null &&
            existing.isConnected()
        ) {

            updateState(
                receiver.id,
                true,
                null
            )

            return
        }

        existing?.disconnect()

        val connection =
            ReceiverConnection(
                receiver
            )

        connections[receiver.id] =
            connection

        serviceScope.launch {

            try {

                _diagnostic.value =
                    "🔗 Conectando con ${receiver.name}..."

                val connected =
                    withContext(
                        Dispatchers.IO
                    ) {
                        connection.connect()
                    }

                if (!connected) {

                    connection.disconnect()

                    connections.remove(
                        receiver.id
                    )

                    updateState(
                        receiver.id,
                        false,
                        null
                    )

                    _diagnostic.value =
                        "❌ No se pudo conectar con ${receiver.name}"

                    return@launch
                }

                updateState(
                    receiver.id,
                    true,
                    null
                )

                _diagnostic.value =
                    "🟢 Conectado: ${receiver.name}"

                val latency =
                    withContext(
                        Dispatchers.IO
                    ) {
                        connection.ping()
                    }

                if (latency != null) {

                    updateState(
                        receiver.id,
                        true,
                        latency
                    )

                    _diagnostic.value =
                        "🟢 ${receiver.name} conectada · ${latency} ms"

                } else {

                    /*
                     * No destruimos inmediatamente
                     * la conexión si el primer PING
                     * falla.
                     *
                     * El ciclo de mantenimiento
                     * intentará recuperarla.
                     */
                    updateState(
                        receiver.id,
                        connection.isConnected(),
                        null
                    )

                    _diagnostic.value =
                        "🟠 TCP conectado; esperando respuesta del receptor"
                }

            } catch (exception: Exception) {

                connection.disconnect()

                connections.remove(
                    receiver.id
                )

                updateState(
                    receiver.id,
                    false,
                    null
                )

                _diagnostic.value =
                    "❌ Error de conexión: ${
                        exception.message
                            ?: "error desconocido"
                    }"
            }
        }
    }

    suspend fun sendCommand(
        command: String
    ): Int {

        var successCount = 0

        val currentConnections =
            connections.toMap()

        for (entry in currentConnections) {

            val connection =
                entry.value

            if (
                !connection.isConnected()
            ) {
                continue
            }

            try {

                val success =
                    withContext(
                        Dispatchers.IO
                    ) {
                        connection.send(
                            command
                        )
                    }

                if (success) {
                    successCount++
                }

            } catch (_: Exception) {
            }
        }

        return successCount
    }

    fun sendCommandAsync(
        command: String,
        callback:
            (Int, Int) -> Unit
    ) {

        serviceScope.launch {

            val total =
                connections
                    .count {
                        it.value.isConnected()
                    }

            if (total == 0) {

                _diagnostic.value =
                    "⚠️ No hay pantallas conectadas"

                callback(
                    0,
                    0
                )

                return@launch
            }

            val success =
                sendCommand(
                    command
                )

            _diagnostic.value =
                if (success == total) {

                    "🟢 Comando enviado · $success pantalla(s)"

                } else {

                    "🟠 Comando enviado · $success/$total respondieron"
                }

            callback(
                success,
                total
            )
        }
    }

    private fun startPingLoop() {

        serviceScope.launch {

            while (true) {

                delay(
                    PING_INTERVAL_MS
                )

                maintainConnections()
            }
        }
    }

    private suspend fun maintainConnections() {

        val current =
            connections.toMap()

        for (entry in current) {

            val id =
                entry.key

            val connection =
                entry.value

            try {

                if (
                    !connection.isConnected()
                ) {

                    updateState(
                        id,
                        false,
                        null
                    )

                    val receiver =
                        receivers[id]

                    if (receiver != null) {

                        connectToReceiver(
                            receiver
                        )
                    }

                    continue
                }

                val latency =
                    withContext(
                        Dispatchers.IO
                    ) {
                        connection.ping()
                    }

                if (latency != null) {

                    updateState(
                        id,
                        true,
                        latency
                    )

                } else {

                    /*
                     * No cerramos el WebSocket
                     * solamente porque un PING
                     * no respondió.
                     */
                    updateState(
                        id,
                        connection.isConnected(),
                        null
                    )
                }

            } catch (_: Exception) {

                updateState(
                    id,
                    false,
                    null
                )

                try {
                    connection.disconnect()
                } catch (_: Exception) {
                }

                connections.remove(
                    id
                )

                val receiver =
                    receivers[id]

                if (receiver != null) {

                    connectToReceiver(
                        receiver
                    )
                }
            }
        }
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

    private fun updateState(
        id: String,
        connected: Boolean,
        latency: Long?
    ) {

        val states =
            _connectionStates.value
                .toMutableMap()

        states[id] =
            connected

        _connectionStates.value =
            states

        if (latency != null) {

            val current =
                _latencies.value
                    .toMutableMap()

            current[id] =
                latency

            _latencies.value =
                current

        } else {

            val current =
                _latencies.value
                    .toMutableMap()

            current.remove(id)

            _latencies.value =
                current
        }
    }

    private fun publishReceivers() {

        _receivers.value =
            receivers.values
                .sortedBy {
                    it.name
                }
    }

    private fun createNotificationChannel() {

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.O
        ) {

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "YG Sync",
                    NotificationManager
                        .IMPORTANCE_LOW
                )

            channel.description =
                "Mantiene las conexiones de YG Sync activas"

            manager.createNotificationChannel(
                channel
            )
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
            .setContentText(
                text
            )
            .setSmallIcon(
                android.R.drawable
                    .stat_sys_data_bluetooth
            )
            .setOngoing(true)
            .setCategory(
                NotificationCompat
                    .CATEGORY_SERVICE
            )
            .build()
    }

    private fun stopService() {

        for (connection in connections.values) {

            try {
                connection.disconnect()
            } catch (_: Exception) {
            }
        }

        connections.clear()

        _connectionStates.value =
            emptyMap()

        _latencies.value =
            emptyMap()

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    override fun onDestroy() {

        for (connection in connections.values) {

            try {
                connection.disconnect()
            } catch (_: Exception) {
            }
        }

        connections.clear()

        serviceScope.cancel()

        instance = null

        _started.value = false

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
