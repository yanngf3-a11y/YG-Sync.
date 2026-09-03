package com.ygsync.controller.network

import android.content.Context
import com.ygsync.controller.data.Receiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class ReceiverDiscovery(
    private val context: Context
) {

    companion object {
        const val DISCOVERY_PORT = 8766
        const val DISCOVERY_REQUEST = "YG_SYNC_DISCOVER"
        const val DISCOVERY_RESPONSE = "YG_SYNC_RECEIVER"

        private const val DISCOVERY_TIME_MS = 4000L
    }

    fun discoverReceivers(): Flow<Receiver> =
        flow {

            val receivers =
                withContext(Dispatchers.IO) {
                    discoverUdp()
                }

            for (receiver in receivers) {
                emit(receiver)
            }
        }

    private fun discoverUdp(): List<Receiver> {

        val found =
            linkedMapOf<String, Receiver>()

        var socket: DatagramSocket? = null

        try {

            socket =
                DatagramSocket(null).apply {

                    reuseAddress = true

                    broadcast = true

                    bind(
                        InetSocketAddress(
                            0
                        )
                    )

                    soTimeout = 500
                }

            val requestBytes =
                DISCOVERY_REQUEST.toByteArray(
                    Charsets.UTF_8
                )

            val broadcastAddresses =
                getBroadcastAddresses()

            for (address in broadcastAddresses) {

                try {

                    val packet =
                        DatagramPacket(
                            requestBytes,
                            requestBytes.size,
                            address,
                            DISCOVERY_PORT
                        )

                    socket.send(packet)

                } catch (_: Exception) {
                }
            }

            val startTime =
                System.currentTimeMillis()

            val buffer =
                ByteArray(2048)

            while (
                System.currentTimeMillis() -
                    startTime <
                    DISCOVERY_TIME_MS
            ) {

                try {

                    val packet =
                        DatagramPacket(
                            buffer,
                            buffer.size
                        )

                    socket.receive(packet)

                    val message =
                        String(
                            packet.data,
                            packet.offset,
                            packet.length,
                            Charsets.UTF_8
                        ).trim()

                    if (
                        !message.startsWith(
                            "$DISCOVERY_RESPONSE|"
                        )
                    ) {
                        continue
                    }

                    val parts =
                        message.split(
                            "|"
                        )

                    if (parts.size < 3) {
                        continue
                    }

                    val name =
                        parts[1].ifBlank {
                            "YG Sync Receiver"
                        }

                    val port =
                        parts[2].toIntOrNull()
                            ?: continue

                    if (port <= 0) {
                        continue
                    }

                    val address =
                        packet.address.hostAddress
                            ?: continue

                    val id =
                        "$address:$port"

                    if (!found.containsKey(id)) {

                        found[id] =
                            Receiver(
                                id = id,
                                name = name,
                                address = address,
                                port = port
                            )
                    }

                } catch (_: java.net.SocketTimeoutException) {

                    // Seguir esperando respuestas.
                } catch (_: Exception) {
                }
            }

        } catch (_: Exception) {

            // El descubrimiento automático
            // no debe cerrar el Controller.

        } finally {

            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }

        return found.values.toList()
    }

    private fun getBroadcastAddresses():
        List<InetAddress> {

        val addresses =
            mutableListOf<InetAddress>()

        try {

            val interfaces =
                java.net.NetworkInterface
                    .getNetworkInterfaces()

            while (interfaces.hasMoreElements()) {

                val networkInterface =
                    interfaces.nextElement()

                if (
                    networkInterface.isLoopback ||
                    !networkInterface.isUp
                ) {
                    continue
                }

                for (
                    interfaceAddress
                    in networkInterface.interfaceAddresses
                ) {

                    val broadcast =
                        interfaceAddress.broadcast
                            ?: continue

                    addresses.add(
                        broadcast
                    )
                }
            }

        } catch (_: Exception) {
        }

        if (addresses.isEmpty()) {

            try {

                addresses.add(
                    InetAddress.getByName(
                        "255.255.255.255"
                    )
                )

            } catch (_: Exception) {
            }
        }

        return addresses.distinctBy {
            it.hostAddress
        }
    }
}
