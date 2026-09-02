package com.ygsync.receiver.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

class ReceiverServer(
    private val port: Int = 8765
) {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    fun start(
        scope: CoroutineScope,
        onMessage: (String) -> Unit
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

                    handleClient(
                        socket = socket,
                        onMessage = onMessage
                    )
                }

            } catch (_: Exception) {

                // El servidor se detuvo o el puerto no está disponible.

            }
        }
    }

    private fun handleClient(
        socket: Socket,
        onMessage: (String) -> Unit
    ) {

        socket.use {

            try {

                val reader = BufferedReader(
                    InputStreamReader(
                        socket.getInputStream()
                    )
                )

                while (true) {

                    val message = reader.readLine()
                        ?: break

                    if (message.isNotBlank()) {
                        onMessage(message)
                    }
                }

            } catch (_: Exception) {

                // El cliente cerró la conexión.
            }
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
