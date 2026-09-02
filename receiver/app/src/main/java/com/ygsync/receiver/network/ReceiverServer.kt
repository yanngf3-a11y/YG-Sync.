package com.ygsync.receiver.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class ReceiverServer(
    private val port: Int = 8765
) {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    fun start(
        scope: CoroutineScope,
        onMessage: (String, Socket) -> Unit
    ) {

        if (serverJob?.isActive == true) {
            return
        }

        serverJob = scope.launch(Dispatchers.IO) {

            try {

                val newServerSocket =
                    ServerSocket(port)

                newServerSocket.reuseAddress = true

                serverSocket = newServerSocket

                while (isActive) {

                    try {

                        val socket =
                            newServerSocket.accept()

                        launch {

                            handleClient(
                                socket = socket,
                                onMessage = onMessage
                            )
                        }

                    } catch (_: Exception) {

                        if (!isActive) {
                            break
                        }
                    }
                }

            } catch (_: Exception) {

                // El servidor no pudo iniciar
            }
        }
    }

    private fun handleClient(
        socket: Socket,
        onMessage: (String, Socket) -> Unit
    ) {

        try {

            socket.keepAlive = true
            socket.soTimeout = 0

            val reader =
                BufferedReader(
                    InputStreamReader(
                        socket.getInputStream()
                    )
                )

            while (true) {

                val message =
                    reader.readLine()
                        ?: break

                if (message.isNotBlank()) {

                    try {

                        onMessage(
                            message,
                            socket
                        )

                    } catch (_: Exception) {

                        // Un comando incorrecto no debe
                        // cerrar el Receiver.
                    }
                }
            }

        } catch (_: Exception) {

            // El cliente se desconectó.
            // No cerrar el servidor.

        } finally {

            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    fun send(
        socket: Socket,
        message: String
    ): Boolean {

        return try {

            if (
                socket.isClosed ||
                !socket.isConnected
            ) {
                return false
            }

            val writer =
                PrintWriter(
                    socket.getOutputStream(),
                    true
                )

            writer.println(message)

            !writer.checkError()

        } catch (_: Exception) {

            false
        }
    }

    fun stop() {

        serverJob?.cancel()
        serverJob = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }

        serverSocket = null
    }
}
