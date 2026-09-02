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

                serverSocket = ServerSocket(port)

                while (isActive) {

                    val socket = serverSocket?.accept()
                        ?: break

                    launch {

                        handleClient(
                            socket = socket,
                            onMessage = onMessage
                        )
                    }
                }

            } catch (_: Exception) {
                // El servidor se detuvo.
            }
        }
    }

    private fun handleClient(
        socket: Socket,
        onMessage: (String, Socket) -> Unit
    ) {

        socket.use {

            try {

                val reader = BufferedReader(
                    InputStreamReader(
                        socket.getInputStream()
                    )
                )

                while (true) {

                    val message =
                        reader.readLine()
                            ?: break

                    if (message.isNotBlank()) {

                        onMessage(
                            message,
                            socket
                        )
                    }
                }

            } catch (_: Exception) {
                // El cliente cerró la conexión.
            }
        }
    }

    fun send(
        socket: Socket,
        message: String
    ) {

        try {

            val writer = PrintWriter(
                socket.getOutputStream(),
                true
            )

            writer.println(message)

        } catch (_: Exception) {
            // No se pudo enviar el mensaje.
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
