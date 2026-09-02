package com.ygsync.controller.network

import com.ygsync.controller.data.Receiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class ReceiverConnection(
    private val receiver: Receiver
) {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    suspend fun connect(): Boolean =
        withContext(Dispatchers.IO) {

            try {

                if (socket?.isConnected == true &&
                    socket?.isClosed == false
                ) {
                    return@withContext true
                }

                val newSocket = Socket()

                newSocket.connect(
                    java.net.InetSocketAddress(
                        receiver.address,
                        receiver.port
                    ),
                    3000
                )

                newSocket.soTimeout = 3000

                socket = newSocket

                writer = PrintWriter(
                    newSocket.getOutputStream(),
                    true
                )

                reader = BufferedReader(
                    InputStreamReader(
                        newSocket.getInputStream()
                    )
                )

                true

            } catch (_: Exception) {

                disconnect()

                false
            }
        }

    suspend fun ping(): Long? =
        withContext(Dispatchers.IO) {

            val currentSocket = socket
                ?: return@withContext null

            val currentWriter = writer
                ?: return@withContext null

            val currentReader = reader
                ?: return@withContext null

            try {

                val startTime =
                    System.currentTimeMillis()

                currentWriter.println("PING")

                val response =
                    currentReader.readLine()

                val endTime =
                    System.currentTimeMillis()

                if (response == "PONG") {

                    endTime - startTime

                } else {

                    null
                }

            } catch (_: Exception) {

                disconnect()

                null
            }
        }

    suspend fun send(
        message: String
    ): Boolean =
        withContext(Dispatchers.IO) {

            val currentWriter = writer
                ?: return@withContext false

            try {

                currentWriter.println(message)

                true

            } catch (_: Exception) {

                disconnect()

                false
            }
        }

    fun isConnected(): Boolean {

        return socket?.isConnected == true &&
            socket?.isClosed == false
    }

    fun disconnect() {

        try {
            reader?.close()
        } catch (_: Exception) {
        }

        try {
            writer?.close()
        } catch (_: Exception) {
        }

        try {
            socket?.close()
        } catch (_: Exception) {
        }

        reader = null
        writer = null
        socket = null
    }
}
