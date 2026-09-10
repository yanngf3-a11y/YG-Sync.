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
        private const val RESPONSE_TIMEOUT_MS = 3000L
        private const val READY_TIMEOUT_MS = 10000L
    }

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

    private data class PendingResponse(
        val latch: CountDownLatch = CountDownLatch(1),
        @Volatile var response: JSONObject? = null,
        val validator: (JSONObject) -> Boolean
    )

    private val responseLock = Any()

    private val responses =
        mutableMapOf<String, PendingResponse>()

    /*
     * ---------------------------------------------------------
     * CONEXIÓN
     * ---------------------------------------------------------
     */

    fun connect(): Boolean {

        disconnect()

        return try {

            val uri =
                URI("ws://$address:$port")

            val connectionLatch =
                CountDownLatch(1)

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

                            if (commandId.isEmpty()) {
                                return
                            }

                            val pending =
                                synchronized(responseLock) {
                                    responses[commandId]
                                }

                            if (pending == null) {

                                Log.d(
                                    TAG,
                                    "Respuesta sin espera activa: $commandId"
                                )

                                return
                            }

                            /*
                             * -------------------------------------------------
                             * IMPORTANTE
                             * -------------------------------------------------
                             *
                             * Un comando OPEN produce dos respuestas:
                             *
                             * 1. ACK inmediato
                             * 2. READY cuando SmartTube realmente cargó
                             *    el video.
                             *
                             * Antes el ACK liberaba el latch aunque el
                             * validator esperaba READY.
                             *
                             * Ahora solamente liberamos la espera cuando
                             * la respuesta cumple el validator.
                             */

                            val accepted =
                                pending.validator(json)

                            if (accepted) {

                                Log.d(
                                    TAG,
                                    "Respuesta aceptada: $json"
                                )

                                pending.response = json

                                pending.latch.countDown()

                                return
                            }

                            /*
                             * Los errores sí terminan inmediatamente
                             * la espera, aunque no pasen el validator.
                             */
                            val type =
                                json.optString(
                                    "type",
                                    ""
                                )

                            if (
                                type == "error"
                            ) {

                                Log.e(
                                    TAG,
                                    "Receiver devolvió ERROR: $json"
                                )

                                pending.response = json

                                pending.latch.countDown()

                                return
                            }

                            /*
                             * ACK u otra respuesta intermedia:
                             *
                             * NO liberamos el latch.
                             *
                             * La entrada permanece registrada para
                             * recibir posteriormente READY.
                             */
                            Log.d(
                                TAG,
                                "Respuesta intermedia ignorada: type=$type commandId=$commandId"
                            )

                        } catch (e: Exception) {

                            Log.e(
                                TAG,
                                "Error procesando respuesta",
                                e
                            )

                            onDiagnostic(
                                "Error RX: ${
                                    e.message ?: "desconocido"
                                }"
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

            if (
                !completed ||
                !connectionResult
            ) {

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
                "Error conexión: ${
                    e.message ?: "desconocido"
                }"
            )

            false
        }
    }

    /*
     * ---------------------------------------------------------
     * PING
     * ---------------------------------------------------------
     */

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
                        isSuccessfulResponse(
                            response
                        )
            }

        if (result) {

            val latency =
                System.currentTimeMillis() - start

            onLatency(latency)
        }

        return result
    }

    /*
     * ---------------------------------------------------------
     * ENVÍO SIMPLE
     * ---------------------------------------------------------
     */

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
                "Error TX: ${
                    e.message ?: "desconocido"
                }"
            )

            false
        }
    }

    /*
     * ---------------------------------------------------------
     * ENVÍO + ACK
     * ---------------------------------------------------------
     */

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

    /*
     * ---------------------------------------------------------
     * LOAD VIDEO + READY REAL
     * ---------------------------------------------------------
     */

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

            val startTime =
                System.currentTimeMillis()

            val response =
                sendAndWaitForJson(
                    json = json,
                    commandId = commandId,
                    timeoutMs = timeoutMs
                ) { response ->

                    val type =
                        response.optString(
                            "type",
                            ""
                        )

                    /*
                     * Para OPEN solamente READY confirma
                     * que SmartTube cambió al video.
                     */
                    if (type != "ready") {
                        return@sendAndWaitForJson false
                    }

                    if (
                        !isSuccessfulResponse(
                            response
                        )
                    ) {
                        return@sendAndWaitForJson false
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

            if (response == null) {

                val elapsed =
                    System.currentTimeMillis() - startTime

                onDiagnostic(
                    "READY TIMEOUT: ${
                        elapsed
                    }ms — $cleanVideoId"
                )

                return false
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

            val valid =
                responseVideoId == cleanVideoId

            if (valid) {

                val elapsed =
                    System.currentTimeMillis() - startTime

                onDiagnostic(
                    "READY OK: ${
                        elapsed
                    }ms — $cleanVideoId"
                )
            }

            valid

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

    /*
     * ---------------------------------------------------------
     * STATUS
     * ---------------------------------------------------------
     */

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

    /*
     * ---------------------------------------------------------
     * ENVÍO JSON DIRECTO
     * ---------------------------------------------------------
     */

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

            onDiagnostic(
                "Error TX JSON: ${
                    e.message ?: "desconocido"
                }"
            )

            false
        }
    }

    /*
     * ---------------------------------------------------------
     * ESTADO
     * ---------------------------------------------------------
     */

    fun isConnected(): Boolean {

        return connected &&
                webSocket?.isOpen == true
    }

    /*
     * ---------------------------------------------------------
     * DESCONECTAR
     * ---------------------------------------------------------
     */

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

    /*
     * ---------------------------------------------------------
     * ENVÍO INTERNO + ESPERA
     * ---------------------------------------------------------
     */

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
            PendingResponse(
                validator = validator
            )

        synchronized(responseLock) {

            responses[commandId] =
                pending
        }

        return try {

            val socket =
                webSocket

            if (
                socket == null ||
                !socket.isOpen
            ) {

                return null
            }

            socket.send(
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

            /*
             * Un ERROR puede haber despertado la espera.
             * Nunca lo consideramos éxito.
             */
            if (
                response.optString(
                    "type",
                    ""
                ) == "error"
            ) {

                Log.e(
                    TAG,
                    "Respuesta ERROR: $response"
                )

                return null
            }

            if (!validator(response)) {

                Log.w(
                    TAG,
                    "Respuesta no válida: $response"
                )

                return null
            }

            response

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error esperando respuesta",
                e
            )

            onDiagnostic(
                "ERROR WAIT: ${
                    e.message ?: "desconocido"
                }"
            )

            null

        } finally {

            synchronized(responseLock) {

                responses.remove(
                    commandId
                )
            }
        }
    }

    /*
     * ---------------------------------------------------------
     * CONVERSIÓN DE COMANDOS LEGACY
     * ---------------------------------------------------------
     */

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

    /*
     * ---------------------------------------------------------
     * CREAR MENSAJE
     * ---------------------------------------------------------
     */

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

    /*
     * ---------------------------------------------------------
     * RESPUESTA EXITOSA
     * ---------------------------------------------------------
     */

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
