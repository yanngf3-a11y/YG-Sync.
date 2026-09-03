package com.ygsync.controller.network

import com.ygsync.controller.data.Receiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class ReceiverConnection(
    private val receiver: Receiver
) {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    suspend fun connect(): Boolean =
        withContext(Dispatchers.IO) {

            disconnect()

            try {

                val newSocket = Socket()

                newSocket.connect(
                    InetSocketAddress(
                        receiver.address,
                        receiver.port
                    ),
                    3000
                )

                newSocket.soTimeout = 0
                newSocket.keepAlive = true
                newSocket.tcpNoDelay = true

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

            val currentSocket =
                socket

            val currentWriter =
                writer

            val currentReader =
                reader

            if (
                currentSocket == null ||
                currentWriter == null ||
                currentReader == null
            ) {
                return@withContext null
            }

            try {

                if (
                    currentSocket.isClosed ||
                    !currentSocket.isConnected
                ) {
                    disconnect()
                    return@withContext null
                }

                val startTime =
                    System.currentTimeMillis()

                currentWriter.println("PING")

                if (currentWriter.checkError()) {
                    disconnect()
                    return@withContext null
                }

                var pongReceived = false

                while (!pongReceived) {

                    val response =
                        currentReader.readLine()

                    if (response == null) {

                        disconnect()

                        return@withContext null
                    }

                    if (response == "PONG") {
                        pongReceived = true
                    }
                }

                System.currentTimeMillis() - startTime

            } catch (_: Exception) {

                disconnect()

                null
            }
        }

    suspend fun send(
        message: String
    ): Boolean =
        withContext(Dispatchers.IO) {

            val currentSocket =
                socket

            val currentWriter =
                writer

            if (
                currentSocket == null ||
                currentWriter == null
            ) {
                return@withContext false
            }

            try {

                if (
                    currentSocket.isClosed ||
                    !currentSocket.isConnected
                ) {
                    disconnect()
                    return@withContext false
                }

                currentWriter.println(message)

                if (currentWriter.checkError()) {

                    disconnect()

                    return@withContext false
                }

                true

            } catch (_: Exception) {

                disconnect()

                false
            }
        }

    fun isConnected(): Boolean {

        val currentSocket =
            socket

        return currentSocket != null &&
            currentSocket.isConnected &&
            !currentSocket.isClosed
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
