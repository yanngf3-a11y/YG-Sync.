package com.ygsync.controller.network

import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReceiverConnection(
    private val address: String,
    private val port: Int
) {

    companion object {
        private const val TAG = "YG_SYNC_CONNECTION"
        private const val CONNECT_TIMEOUT_MS = 5000L
        private const val RESPONSE_TIMEOUT_MS = 5000L
        private const val READY_TIMEOUT_MS = 30000L
    }

    private var webSocket: WebSocketClient? = null

    @Volatile
    private var connected = false

    private val responseLock = Any()

    private val responses =
        mutableMapOf<String, JSONObject>()

    fun connect(): Boolean {
        disconnect()

        return try {
            val uri =
                URI(
                    "ws://$address:$port"
                )

            val connectionLatch =
                CountDownLatch(1)

            var connectionResult =
                false

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

                        try {
                            val json =
                                JSONObject(message)

                            val commandId =
                                json.optString(
                                    "commandId",
                                    ""
                                )

                            if (commandId.isNotEmpty()) {
                                synchronized(responseLock) {
                                    responses[commandId] =
                                        json

                                    responseLock.notifyAll()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(
                                TAG,
                                "Error procesando respuesta",
                                e
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

        return sendAndWait(
            json,
            commandId,
            RESPONSE_TIMEOUT_MS
        ) { response ->

            response.optString(
                "type",
                ""
            ) == "pong"
                    &&
                    isSuccessfulResponse(
                        response
                    )
        }
    }

    fun send(
        command: String
    ): Boolean {

        if (!isConnected()) {
            return false
        }

        return try {

            val json =
                convertLegacyCommand(
                    command
                )

            if (json == null) {
                Log.e(
                    TAG,
                    "Comando inválido: $command"
                )

                return false
            }

            webSocket?.send(
                json.toString()
            )

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error enviando comando: $command",
                e
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
                convertLegacyCommand(
                    command
                )

            if (json == null) {
                return false
            }

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

                isSuccessfulResponse(
                    response
                )
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

        return try {

            webSocket?.send(
                json.toString()
            )

            waitForResponse(
                commandId,
                timeoutMs
            ) { response ->

                val type =
                    response.optString(
                        "type",
                        ""
                    )

                if (type != "ready") {
                    return@waitForResponse false
                }

                if (
                    !isSuccessfulResponse(
                        response
                    )
                ) {
                    return@waitForResponse false
                }

                val responsePayload =
                    response.optJSONObject(
                        "payload"
                    )

                val responseVideoId =
                    responsePayload?.optString(
                        "videoId",
                        ""
                    )?.trim()

                responseVideoId ==
                        cleanVideoId
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error esperando READY de $cleanVideoId",
                e
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

            webSocket?.send(
                json.toString()
            )

            waitForResponse(
                commandId,
                RESPONSE_TIMEOUT_MS
            ) { response ->

                response.optString(
                    "type",
                    ""
                ) == "status"
                        &&
                        isSuccessfulResponse(
                            response
                        )
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
            responses.clear()
            responseLock.notifyAll()
        }
    }

    private fun sendAndWait(
        json: JSONObject,
        commandId: String,
        timeoutMs: Long,
        validator: (JSONObject) -> Boolean
    ): Boolean {

        try {

            webSocket?.send(
                json.toString()
            )

            val response =
                waitForResponse(
                    commandId,
                    timeoutMs,
                    validator
                )

            return response != null

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error sendAndWait",
                e
            )

            return false
        }
    }

    private fun waitForResponse(
        commandId: String,
        timeoutMs: Long,
        validator: (JSONObject) -> Boolean
    ): JSONObject? {

        val start =
            System.currentTimeMillis()

        synchronized(responseLock) {

            responses.remove(
                commandId
            )

            while (true) {

                val response =
                    responses.remove(
                        commandId
                    )

                if (
                    response != null &&
                    validator(response)
                ) {
                    return response
                }

                val elapsed =
                    System.currentTimeMillis()
                            - start

                val remaining =
                    timeoutMs - elapsed

                if (remaining <= 0) {
                    return null
                }

                try {
                    responseLock.wait(
                        remaining
                    )
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
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

        when (type) {

            "PLAY" -> {
                return createMessage(
                    "play",
                    commandId,
                    payload
                )
            }

            "PAUSE" -> {
                return createMessage(
                    "pause",
                    commandId,
                    payload
                )
            }

            "STOP" -> {
                return createMessage(
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

                return createMessage(
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

                return createMessage(
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

                return createMessage(
                    "setVolume",
                    commandId,
                    payload
                )
            }

            "GET_STATUS" -> {

                return createMessage(
                    "getStatus",
                    commandId,
                    payload
                )
            }

            "NEXT" -> {

                return createMessage(
                    "next",
                    commandId,
                    payload
                )
            }

            "PREVIOUS" -> {

                return createMessage(
                    "previous",
                    commandId,
                    payload
                )
            }

            else -> {
                return null
            }
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
