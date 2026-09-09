package com.ygsync.controller.network

import android.content.Context
import com.ygsync.controller.data.Receiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

class ReceiverDiscovery(
    private val context: Context
) {

    companion object {

        const val DISCOVERY_PORT = 8766

        const val DISCOVERY_REQUEST =
            "YG_SYNC_DISCOVER"

        const val DISCOVERY_RESPONSE =
            "YG_SYNC_RECEIVER"

        private const val DISCOVERY_TIME_MS =
            5000L

        private const val SOCKET_TIMEOUT_MS =
            500

        private const val RECEIVE_BUFFER_SIZE =
            4096
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

            log(
                "YG Sync: iniciando descubrimiento UDP"
            )

            log(
                "YG Sync: puerto UDP=$DISCOVERY_PORT"
            )

            socket =
                DatagramSocket().apply {

                    reuseAddress = true

                    broadcast = true

                    soTimeout =
                        SOCKET_TIMEOUT_MS
                }

            log(
                "YG Sync: socket UDP creado correctamente"
            )

            val requestBytes =
                DISCOVERY_REQUEST.toByteArray(
                    Charsets.UTF_8
                )

            val broadcastAddresses =
                getBroadcastAddresses()

            log(
                "YG Sync: broadcasts encontrados="
                        + broadcastAddresses.size
            )

            for (
                address in broadcastAddresses
            ) {

                log(
                    "YG Sync: broadcast disponible="
                            + address.hostAddress
                )
            }

            /*
             * Enviar a todos los broadcasts reales
             * encontrados en las interfaces de red.
             */
            for (
                address in broadcastAddresses
            ) {

                sendDiscoveryRequest(
                    socket = socket,
                    address = address,
                    requestBytes = requestBytes
                )
            }

            /*
             * Prueba adicional con broadcast global.
             */
            try {

                val globalBroadcast =
                    InetAddress.getByName(
                        "255.255.255.255"
                    )

                if (
                    broadcastAddresses.none {
                        it.hostAddress ==
                                globalBroadcast.hostAddress
                    }
                ) {

                    sendDiscoveryRequest(
                        socket = socket,
                        address = globalBroadcast,
                        requestBytes = requestBytes
                    )
                }

            } catch (exception: Exception) {

                log(
                    "YG Sync: error con broadcast global: "
                            + safeMessage(exception)
                )
            }

            log(
                "YG Sync: solicitudes UDP enviadas"
            )

            val startTime =
                System.currentTimeMillis()

            val buffer =
                ByteArray(
                    RECEIVE_BUFFER_SIZE
                )

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

                    val remoteAddress =
                        packet.address
                            ?.hostAddress
                            ?: "desconocido"

                    val remotePort =
                        packet.port

                    val message =
                        String(
                            packet.data,
                            packet.offset,
                            packet.length,
                            Charsets.UTF_8
                        ).trim()

                    log(
                        "YG Sync: UDP recibido desde "
                                + remoteAddress
                                + ":"
                                + remotePort
                    )

                    log(
                        "YG Sync: respuesta=\""
                                + message
                                + "\""
                    )

                    if (
                        !message.startsWith(
                            "$DISCOVERY_RESPONSE|"
                        )
                    ) {

                        log(
                            "YG Sync: respuesta UDP ignorada"
                        )

                        continue
                    }

                    val parts =
                        message.split("|")

                    if (parts.size < 3) {

                        log(
                            "YG Sync: respuesta inválida, "
                                    + "faltan campos"
                        )

                        continue
                    }

                    val name =
                        parts[1]
                            .trim()
                            .ifBlank {
                                "YG Sync Receiver"
                            }

                    val port =
                        parts[2]
                            .trim()
                            .toIntOrNull()

                    if (
                        port == null ||
                        port <= 0 ||
                        port > 65535
                    ) {

                        log(
                            "YG Sync: puerto inválido="
                                    + parts[2]
                        )

                        continue
                    }

                    /*
                     * La IP correcta para TCP es la IP
                     * desde la cual respondió el Receiver.
                     */
                    val address =
                        remoteAddress

                    val id =
                        "$address:$port"

                    if (
                        !found.containsKey(id)
                    ) {

                        val receiver =
                            Receiver(
                                id = id,
                                name = name,
                                address = address,
                                port = port
                            )

                        found[id] =
                            receiver

                        log(
                            "YG Sync: RECEIVER ENCONTRADO"
                        )

                        log(
                            "YG Sync: nombre="
                                    + name
                        )

                        log(
                            "YG Sync: dirección="
                                    + address
                        )

                        log(
                            "YG Sync: puerto="
                                    + port
                        )

                    } else {

                        log(
                            "YG Sync: receiver duplicado="
                                    + id
                        )
                    }

                } catch (
                    _: SocketTimeoutException
                ) {

                    /*
                     * Normal: permite seguir esperando
                     * más respuestas.
                     */

                } catch (exception: Exception) {

                    log(
                        "YG Sync: error recibiendo UDP: "
                                + safeMessage(exception)
                    )
                }
            }

            log(
                "YG Sync: descubrimiento finalizado"
            )

            log(
                "YG Sync: receivers encontrados="
                        + found.size
            )

        } catch (exception: Exception) {

            log(
                "YG Sync: ERROR GENERAL UDP: "
                        + safeMessage(exception)
            )

        } finally {

            try {
                socket?.close()
            } catch (_: Exception) {
            }

            log(
                "YG Sync: socket UDP cerrado"
            )
        }

        return found.values.toList()
    }

    private fun sendDiscoveryRequest(
        socket: DatagramSocket,
        address: InetAddress,
        requestBytes: ByteArray
    ) {

        try {

            val packet =
                DatagramPacket(
                    requestBytes,
                    requestBytes.size,
                    address,
                    DISCOVERY_PORT
                )

            log(
                "YG Sync: enviando UDP "
                        + DISCOVERY_REQUEST
                        + " → "
                        + address.hostAddress
                        + ":"
                        + DISCOVERY_PORT
            )

            socket.send(packet)

            log(
                "YG Sync: UDP enviado correctamente → "
                        + address.hostAddress
            )

        } catch (exception: Exception) {

            log(
                "YG Sync: ERROR enviando UDP → "
                        + address.hostAddress
                        + ": "
                        + safeMessage(exception)
            )
        }
    }

    private fun getBroadcastAddresses():
        List<InetAddress> {

        val addresses =
            mutableListOf<InetAddress>()

        try {

            val interfaces =
                NetworkInterface
                    .getNetworkInterfaces()

            while (
                interfaces.hasMoreElements()
            ) {

                val networkInterface =
                    interfaces.nextElement()

                try {

                    if (
                        networkInterface.isLoopback ||
                        !networkInterface.isUp
                    ) {
                        continue
                    }

                } catch (_: Exception) {

                    continue
                }

                log(
                    "YG Sync: interfaz encontrada="
                            + networkInterface.name
                )

                for (
                    interfaceAddress
                    in networkInterface.interfaceAddresses
                ) {

                    try {

                        val address =
                            interfaceAddress.address

                        if (
                            address !is Inet4Address
                        ) {
                            continue
                        }

                        log(
                            "YG Sync: IPv4="
                                    + address.hostAddress
                        )

                        val broadcast =
                            interfaceAddress.broadcast

                        if (
                            broadcast != null
                        ) {

                            log(
                                "YG Sync: broadcast="
                                        + broadcast.hostAddress
                            )

                            addresses.add(
                                broadcast
                            )
                        }

                    } catch (exception: Exception) {

                        log(
                            "YG Sync: error leyendo "
                                    + "dirección de interfaz: "
                                    + safeMessage(exception)
                        )
                    }
                }
            }

        } catch (exception: Exception) {

            log(
                "YG Sync: error obteniendo "
                        + "interfaces de red: "
                        + safeMessage(exception)
            )
        }

        if (addresses.isEmpty()) {

            log(
                "YG Sync: no se encontraron broadcasts "
                        + "de interfaces"
            )

            try {

                addresses.add(
                    InetAddress.getByName(
                        "255.255.255.255"
                    )
                )

            } catch (exception: Exception) {

                log(
                    "YG Sync: error creando "
                            + "broadcast global: "
                            + safeMessage(exception)
                )
            }
        }

        val unique =
            addresses.distinctBy {
                it.hostAddress
            }

        log(
            "YG Sync: broadcasts únicos="
                    + unique.size
        )

        return unique
    }

    private fun log(
        message: String
    ) {

        android.util.Log.d(
            "YG_SYNC_DISCOVERY",
            message
        )
    }

    private fun safeMessage(
        exception: Exception?
    ): String {

        if (exception == null) {
            return "desconocido"
        }

        val message =
            exception.message

        if (
            message == null ||
            message.trim().isEmpty()
        ) {

            return exception
                .javaClass
                .simpleName
        }

        return message
    }
}
