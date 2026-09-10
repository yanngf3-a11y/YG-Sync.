package com.ygsync.controller.network

import android.util.Log
import com.ygsync.controller.data.Receiver
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReceiverConnection(
    private val address: String,
    private val port: Int,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onLatency: (Long) -> Unit = {},
    private val onDiagnostic: (String) -> Unit = {}
) {

    companion object {
        private const val TAG = "YG_SYNC_CONNECTION"
        private const val CONNECT_TIMEOUT_MS = 5000L
        private const val RESPONSE_TIMEOUT_MS = 5000L
        private const val READY_TIMEOUT_MS = 30000L
    }

    /*
     * Constructor compatible con ControllerSyncService.
     */
    constructor(
        receiver: Receiver,
        onConnected: () -> Unit = {},
        onDisconnected: () -> Unit = {},
        onLatency: (Long) -> Unit = {},
        onDiagnostic: (String) -> Unit = {}
    ) : this(
        address = receiver.address,
        port = receiver.port,
        onConnected = onConnected,
        onDisconnected = onDisconnected,
        onLatency = onLatency,
        onDiagnostic = onDiagnostic
    )

    private var webSocket: WebSocketClient? = null

    @Volatile
    private var connected = false

    /*
     * Cada comando que espera respuesta tiene su propio latch.
     *
     * Esto reemplaza completamente wait()/notifyAll()
     * y evita los errores de inferencia de Kotlin.
     */
    private data class PendingResponse(
        val latch: CountDownLatch = CountDownLatch(1),
        @Volatile var response: JSONObject? = null
    )

    private val responseLock = Any()

    private val responses =
        mutableMapOf<String, PendingResponse>()

    fun connect(): Boolean {
        disconnect()

        return try {
            val uri = URI("ws://$address:$port")

            val connectionLatch = CountDownLatch(1)

            var connectionResult = false

            val client =
                object : WebSocketClient(uri) {

                    override fun onOpen(
                        handshake: ServerHandshake?
                    ) {
                        Log.d(
                            TAG,
                            "Conectado a $address:$port"
                        )

                        connected = true
                        connectionResult = true

                        onDiagnostic(
                            "Conectado a $address:$port"
                        )

                        onConnected()

                        connectionLatch.countDown()
                    }

                    override fun onMessage(
                        message: String?
                    ) {
                        if (message == null) {
                            return
                        }

                        Log.d(
                            TAG,
                            "Mensaje recibido: $message"
                        )

                        onDiagnostic(
                            "RX: $message"
                        )

                        try {
                            val json =
                                JSONObject(message)

                            val commandId =
                                json.optString(
                                    "commandId",
                                    ""
                                )

                            if (commandId.isNotEmpty()) {
                                var pending: PendingResponse? = null

                                synchronized(responseLock) {
                                    pending =
                                        responses[commandId]
                                }

                                pending?.let {
                                    it.response = json
                                    it.latch.countDown()
                                }
                            }

                        } catch (e: Exception) {

                            Log.e(
                                TAG,
                                "Error procesando respuesta",
                                e
                            )

                            onDiagnostic(
                                "Error RX: ${e.message ?: "desconocido"}"
                            )
                        }
                    }

                    override fun onClose(
                        code: Int,
                        reason: String?,
                        remote: Boolean
                    ) {
                        Log.d(
                            TAG,
                            "Desconectado de $address:$port"
                        )

                        connected = false

                        onDiagnostic(
                            "Desconectado de $address:$port"
                        )

                        onDisconnected()
                    }

                    override fun onError(
                        ex: Exception?
                    ) {
                        Log.e(
                            TAG,
                            "Error WebSocket",
                            ex
                        )

                        connected = false

                        onDiagnostic(
                            "Error WebSocket: ${
                                ex?.message ?: "desconocido"
                            }"
                        )

                        connectionLatch.countDown()
                    }
                }

            client.connect()

            val completed =
                connectionLatch.await(
                    CONNECT_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS
                )

            if (!completed || !connectionResult) {

                try {
                    client.close()
                } catch (_: Exception) {
                }

                connected = false

                onDiagnostic(
                    "Timeout conectando a $address:$port"
                )

                return false
            }

            webSocket = client

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error conectando a $address:$port",
                e
            )

            connected = false

            onDiagnostic(
                "Error conexión: ${e.message ?: "desconocido"}"
            )

            false
        }
    }

    fun ping(): Boolean {

        if (!isConnected()) {
            return false
        }

        val commandId =
            UUID.randomUUID().toString()

        val json =
            createMessage(
                type = "ping",
                commandId = commandId,
                payload = JSONObject()
            )

        val start =
            System.currentTimeMillis()

        val result =
            sendAndWait(
                json,
                commandId,
                RESPONSE_TIMEOUT_MS
            ) { response ->

                response.optString(
                    "type",
                    ""
                ) == "pong" &&
                        isSuccessfulResponse(response)
            }

        if (result) {
            val latency =
                System.currentTimeMillis() - start

            onLatency(latency)
        }

        return result
    }

    fun send(
        command: String
    ): Boolean {

        if (!isConnected()) {
            return false
        }

        return try {

            val json =
                convertLegacyCommand(command)
                    ?: return false

            webSocket?.send(
                json.toString()
            )

            onDiagnostic(
                "TX: $json"
            )

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error enviando comando: $command",
                e
            )

            onDiagnostic(
                "Error TX: ${e.message ?: "desconocido"}"
            )

            false
        }
    }

    fun sendAndWait(
        command: String,
        timeoutMs: Long = RESPONSE_TIMEOUT_MS
    ): Boolean {

        if (!isConnected()) {
            return false
        }

        return try {

            val json =
                convertLegacyCommand(command)
                    ?: return false

            val commandId =
                json.optString(
                    "commandId",
                    ""
                )

            if (commandId.isEmpty()) {
                return false
            }

            sendAndWait(
                json,
                commandId,
                timeoutMs
            ) { response ->

                isSuccessfulResponse(response)
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error esperando ACK",
                e
            )

            false
        }
    }

    fun loadVideoAndWaitReady(
        videoId: String,
        timeoutMs: Long = READY_TIMEOUT_MS
    ): Boolean {

        if (!isConnected()) {
            return false
        }

        val cleanVideoId =
            videoId.trim()

        if (cleanVideoId.isEmpty()) {
            return false
        }

        val commandId =
            UUID.randomUUID().toString()

        val payload =
            JSONObject()

        payload.put(
            "videoId",
            cleanVideoId
        )

        val json =
            createMessage(
                type = "open",
                commandId = commandId,
                payload = payload
            )

        Log.d(
            TAG,
            "Solicitando video: $cleanVideoId"
        )

        onDiagnostic(
            "LOAD: $cleanVideoId"
        )

        return try {

            sendAndWait(
                json,
                commandId,
                timeoutMs
            ) { response ->

                val type =
                    response.optString(
                        "type",
                        ""
                    )

                if (type != "ready") {
                    return@sendAndWait false
                }

                if (
                    !isSuccessfulResponse(
                        response
                    )
                ) {
                    return@sendAndWait false
                }

                val responsePayload =
                    response.optJSONObject(
                        "payload"
                    )

                val responseVideoId =
                    responsePayload
                        ?.optString(
                            "videoId",
                            ""
                        )
                        ?.trim()

                responseVideoId == cleanVideoId
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error esperando READY de $cleanVideoId",
                e
            )

            onDiagnostic(
                "READY ERROR: ${
                    e.message ?: "desconocido"
                }"
            )

            false
        }
    }

    fun getStatus(): JSONObject? {

        if (!isConnected()) {
            return null
        }

        val commandId =
            UUID.randomUUID().toString()

        val json =
            createMessage(
                type = "getStatus",
                commandId = commandId,
                payload = JSONObject()
            )

        return try {

            sendAndWaitForJson(
                json,
                commandId,
                RESPONSE_TIMEOUT_MS
            ) { response ->

                response.optString(
                    "type",
                    ""
                ) == "status" &&
                        isSuccessfulResponse(response)
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error obteniendo status",
                e
            )

            null
        }
    }

    fun sendJson(
        json: JSONObject
    ): Boolean {

        if (!isConnected()) {
            return false
        }

        return try {

            webSocket?.send(
                json.toString()
            )

            onDiagnostic(
                "TX JSON: $json"
            )

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error enviando JSON",
                e
            )

            false
        }
    }

    fun isConnected(): Boolean {
        return connected &&
                webSocket?.isOpen == true
    }

    fun disconnect() {

        connected = false

        try {
            webSocket?.close()
        } catch (_: Exception) {
        }

        webSocket = null

        synchronized(responseLock) {

            responses.values.forEach {
                it.response = null
                it.latch.countDown()
            }

            responses.clear()
        }
    }

    private fun sendAndWait(
        json: JSONObject,
        commandId: String,
        timeoutMs: Long,
        validator: (JSONObject) -> Boolean
    ): Boolean {

        val response =
            sendAndWaitForJson(
                json,
                commandId,
                timeoutMs,
                validator
            )

        return response != null
    }

    private fun sendAndWaitForJson(
        json: JSONObject,
        commandId: String,
        timeoutMs: Long,
        validator: (JSONObject) -> Boolean
    ): JSONObject? {

        if (!isConnected()) {
            return null
        }

        val pending =
            PendingResponse()

        synchronized(responseLock) {
            responses[commandId] =
                pending
        }

        return try {

            webSocket?.send(
                json.toString()
            )

            onDiagnostic(
                "TX: $json"
            )

            val completed =
                pending.latch.await(
                    timeoutMs,
                    TimeUnit.MILLISECONDS
                )

            if (!completed) {

                Log.w(
                    TAG,
                    "Timeout esperando respuesta: $commandId"
                )

                return null
            }

            val response =
                pending.response
                    ?: return null

            if (!validator(response)) {
                return null
            }

            response

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error esperando respuesta",
                e
            )

            null

        } finally {

            synchronized(responseLock) {
                responses.remove(commandId)
            }
        }
    }

    private fun convertLegacyCommand(
        command: String
    ): JSONObject? {

        val clean =
            command.trim()

        if (clean.isEmpty()) {
            return null
        }

        val commandId =
            UUID.randomUUID().toString()

        val parts =
            clean.split(
                "|",
                limit = 2
            )

        val type =
            parts[0]
                .trim()
                .uppercase()

        val payload =
            JSONObject()

        return when (type) {

            "PLAY" -> {

                createMessage(
                    "play",
                    commandId,
                    payload
                )
            }

            "PAUSE" -> {

                createMessage(
                    "pause",
                    commandId,
                    payload
                )
            }

            "STOP" -> {

                createMessage(
                    "stop",
                    commandId,
                    payload
                )
            }

            "LOAD_VIDEO" -> {

                if (parts.size < 2) {
                    return null
                }

                val videoId =
                    parts[1].trim()

                if (videoId.isEmpty()) {
                    return null
                }

                payload.put(
                    "videoId",
                    videoId
                )

                createMessage(
                    "open",
                    commandId,
                    payload
                )
            }

            "SEEK" -> {

                if (parts.size < 2) {
                    return null
                }

                val position =
                    parts[1]
                        .trim()
                        .toLongOrNull()
                        ?: return null

                payload.put(
                    "positionMs",
                    maxOf(
                        0L,
                        position
                    )
                )

                createMessage(
                    "seek",
                    commandId,
                    payload
                )
            }

            "SET_VOLUME" -> {

                if (parts.size < 2) {
                    return null
                }

                val volume =
                    parts[1]
                        .trim()
                        .toFloatOrNull()
                        ?: return null

                payload.put(
                    "volume",
                    volume.coerceIn(
                        0f,
                        1f
                    )
                )

                createMessage(
                    "setVolume",
                    commandId,
                    payload
                )
            }

            "GET_STATUS" -> {

                createMessage(
                    "getStatus",
                    commandId,
                    payload
                )
            }

            "NEXT" -> {

                createMessage(
                    "next",
                    commandId,
                    payload
                )
            }

            "PREVIOUS" -> {

                createMessage(
                    "previous",
                    commandId,
                    payload
                )
            }

            "SYNC" -> {

                createMessage(
                    "sync",
                    commandId,
                    payload
                )
            }

            else -> null
        }
    }

    private fun createMessage(
        type: String,
        commandId: String,
        payload: JSONObject
    ): JSONObject {

        val json =
            JSONObject()

        json.put(
            "type",
            type
        )

        json.put(
            "commandId",
            commandId
        )

        json.put(
            "senderId",
            "ygsync-controller"
        )

        json.put(
            "timestamp",
            System.currentTimeMillis()
        )

        json.put(
            "payload",
            payload
        )

        return json
    }

    private fun isSuccessfulResponse(
        response: JSONObject
    ): Boolean {

        if (
            response.optBoolean(
                "success",
                false
            )
        ) {
            return true
        }

        val payload =
            response.optJSONObject(
                "payload"
            )

        return payload?.optBoolean(
            "success",
            false
        ) ?: false
    }
}
