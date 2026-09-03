package com.ygsync.controller.network

import com.ygsync.controller.data.Receiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ReceiverConnection(
    private val receiver: Receiver
) {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5000L
        private const val RESPONSE_TIMEOUT_MS = 3000L
    }

    private var webSocket: WebSocketClient? = null

    private val connected =
        AtomicBoolean(false)

    private val connectionLock =
        Any()

    private fun createClient(): WebSocketClient {

        val uri =
            URI(
                "ws://${receiver.address}:${receiver.port}"
            )

        return object : WebSocketClient(uri) {

            override fun onOpen(
                handshake: ServerHandshake?
            ) {

                connected.set(true)
            }

            override fun onMessage(
                message: String
            ) {
                synchronized(responseLock) {

                    lastResponse =
                        message

                    responseLock.notifyAll()
                }
            }

            override fun onClose(
                code: Int,
                reason: String?,
                remote: Boolean
            ) {

                connected.set(false)

                synchronized(responseLock) {

                    lastResponse =
                        null

                    responseLock.notifyAll()
                }
            }

            override fun onError(
                exception: Exception?
            ) {

                connected.set(false)

                synchronized(responseLock) {

                    lastResponse =
                        null

                    responseLock.notifyAll()
                }
            }
        }
    }

    private val responseLock =
        Object()

    private var lastResponse: String? = null

    suspend fun connect(): Boolean =
        withContext(Dispatchers.IO) {

            disconnect()

            synchronized(connectionLock) {

                try {

                    val client =
                        createClient()

                    webSocket =
                        client

                    client.connect()

                    val startTime =
                        System.currentTimeMillis()

                    while (
                        !connected.get() &&
                        System.currentTimeMillis() -
                            startTime <
                            CONNECT_TIMEOUT_MS
                    ) {

                        Thread.sleep(25)
                    }

                    if (!connected.get()) {

                        try {
                            client.close()
                        } catch (_: Exception) {
                        }

                        webSocket =
                            null

                        return@withContext false
                    }

                    true

                } catch (_: Exception) {

                    disconnect()

                    false
                }
            }
        }

    suspend fun ping(): Long? =
        withContext(Dispatchers.IO) {

            val client =
                webSocket

            if (
                client == null ||
                !connected.get() ||
                !client.isOpen
            ) {
                return@withContext null
            }

            try {

                val commandId =
                    UUID.randomUUID()
                        .toString()

                val message =
                    JSONObject()
                        .apply {

                            put(
                                "type",
                                "ping"
                            )

                            put(
                                "commandId",
                                commandId
                            )

                            put(
                                "senderId",
                                "ygsync-controller"
                            )

                            put(
                                "timestamp",
                                System.currentTimeMillis()
                            )

                            put(
                                "payload",
                                JSONObject()
                            )
                        }
                        .toString()

                synchronized(responseLock) {

                    lastResponse =
                        null
                }

                val startTime =
                    System.currentTimeMillis()

                client.send(message)

                val response =
                    waitForResponse(
                        commandId,
                        RESPONSE_TIMEOUT_MS
                    )

                if (response == null) {

                    /*
                     * El servidor WebSocket actual todavía
                     * debe implementar la respuesta "pong".
                     * No cerramos inmediatamente la conexión
                     * para permitir la migración progresiva.
                     */
                    return@withContext null
                }

                val elapsed =
                    System.currentTimeMillis() -
                        startTime

                if (
                    isSuccessfulResponse(
                        response,
                        commandId
                    )
                ) {
                    elapsed
                } else {
                    null
                }

            } catch (_: Exception) {

                connected.set(false)

                null
            }
        }

    suspend fun send(
        message: String
    ): Boolean =
        withContext(Dispatchers.IO) {

            val client =
                webSocket

            if (
                client == null ||
                !connected.get() ||
                !client.isOpen
            ) {
                return@withContext false
            }

            try {

                val json =
                    convertLegacyCommand(
                        message
                    )

                client.send(
                    json.toString()
                )

                true

            } catch (_: Exception) {

                connected.set(false)

                false
            }
        }

    suspend fun sendJson(
        message: JSONObject
    ): Boolean =
        withContext(Dispatchers.IO) {

            val client =
                webSocket

            if (
                client == null ||
                !connected.get() ||
                !client.isOpen
            ) {
                return@withContext false
            }

            try {

                client.send(
                    message.toString()
                )

                true

            } catch (_: Exception) {

                connected.set(false)

                false
            }
        }

    fun isConnected(): Boolean {

        val client =
            webSocket

        return connected.get() &&
            client != null &&
            client.isOpen
    }

    fun disconnect() {

        connected.set(false)

        val client =
            webSocket

        webSocket =
            null

        synchronized(responseLock) {

            lastResponse =
                null

            responseLock.notifyAll()
        }

        try {

            client?.close()

        } catch (_: Exception) {
        }
    }

    private fun waitForResponse(
        commandId: String,
        timeoutMs: Long
    ): String? {

        val deadline =
            System.currentTimeMillis() +
                timeoutMs

        synchronized(responseLock) {

            while (true) {

                val response =
                    lastResponse

                if (
                    response != null &&
                    responseContainsCommandId(
                        response,
                        commandId
                    )
                ) {
                    return response
                }

                val remaining =
                    deadline -
                        System.currentTimeMillis()

                if (remaining <= 0) {
                    return null
                }

                try {

                    responseLock.wait(
                        remaining
                    )

                } catch (_: InterruptedException) {

                    Thread.currentThread()
                        .interrupt()

                    return null
                }
            }
        }
    }

    private fun responseContainsCommandId(
        response: String,
        commandId: String
    ): Boolean {

        return try {

            val json =
                JSONObject(response)

            json.optString(
                "commandId",
                ""
            ) == commandId

        } catch (_: Exception) {

            false
        }
    }

    private fun isSuccessfulResponse(
        response: String,
        commandId: String
    ): Boolean {

        return try {

            val json =
                JSONObject(response)

            if (
                json.optString(
                    "commandId",
                    ""
                ) != commandId
            ) {
                return false
            }

            json.optBoolean(
                "success",
                json.optJSONObject(
                    "payload"
                )?.optBoolean(
                    "success",
                    false
                ) ?: false
            )

        } catch (_: Exception) {

            false
        }
    }

    private fun convertLegacyCommand(
        command: String
    ): JSONObject {

        val cleanCommand =
            command.trim()

        val commandId =
            UUID.randomUUID()
                .toString()

        val payload =
            JSONObject()

        val type =
            when {

                cleanCommand.equals(
                    "PLAY",
                    ignoreCase = true
                ) ->
                    "play"

                cleanCommand.equals(
                    "PAUSE",
                    ignoreCase = true
                ) ->
                    "pause"

                cleanCommand.equals(
                    "STOP",
                    ignoreCase = true
                ) ->
                    "stop"

                cleanCommand.startsWith(
                    "LOAD_VIDEO|",
                    ignoreCase = true
                ) -> {

                    val videoId =
                        cleanCommand
                            .substringAfter(
                                "|"
                            )
                            .trim()

                    payload.put(
                        "videoId",
                        videoId
                    )

                    "open"
                }

                cleanCommand.startsWith(
                    "SEEK|",
                    ignoreCase = true
                ) -> {

                    val position =
                        cleanCommand
                            .substringAfter(
                                "|"
                            )
                            .trim()
                            .toLongOrNull()
                            ?: 0L

                    payload.put(
                        "positionMs",
                        position
                    )

                    "seek"
                }

                cleanCommand.startsWith(
                    "SET_VOLUME|",
                    ignoreCase = true
                ) -> {

                    val volume =
                        cleanCommand
                            .substringAfter(
                                "|"
                            )
                            .trim()
                            .toFloatOrNull()
                            ?: 1f

                    payload.put(
                        "volume",
                        volume
                    )

                    "setVolume"
                }

                cleanCommand.equals(
                    "GET_STATUS",
                    ignoreCase = true
                ) ->
                    "getStatus"

                else ->
                    cleanCommand.lowercase()
            }

        return JSONObject().apply {

            put(
                "type",
                type
            )

            put(
                "commandId",
                commandId
            )

            put(
                "senderId",
                "ygsync-controller"
            )

            put(
                "timestamp",
                System.currentTimeMillis()
            )

            put(
                "payload",
                payload
            )
        }
    }
}
