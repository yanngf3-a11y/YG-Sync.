package com.ygsync.receiver.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class ReceiverService(
    context: Context,
    private val port: Int = 8765
) {

    companion object {
        const val SERVICE_TYPE = "_ygsync._tcp."
        const val SERVICE_NAME = "YG Sync Receiver"

        const val DISCOVERY_PORT = 8766
        const val DISCOVERY_REQUEST = "YG_SYNC_DISCOVER"
        const val DISCOVERY_RESPONSE = "YG_SYNC_RECEIVER"
    }

    private val nsdManager =
        context.getSystemService(
            Context.NSD_SERVICE
        ) as NsdManager

    private val scope =
        CoroutineScope(
            Dispatchers.IO + SupervisorJob()
        )

    private var registrationListener:
        NsdManager.RegistrationListener? = null

    private var discoveryJob: Job? = null

    fun start() {

        startNsd()

        startUdpDiscovery()
    }

    private fun startNsd() {

        if (registrationListener != null) {
            return
        }

        val serviceInfo =
            NsdServiceInfo().apply {
                serviceName = SERVICE_NAME
                serviceType = SERVICE_TYPE
                setPort(port)
            }

        val listener =
            object : NsdManager.RegistrationListener {

                override fun onServiceRegistered(
                    serviceInfo: NsdServiceInfo
                ) {
                    registrationListener = this
                }

                override fun onRegistrationFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int
                ) {
                    registrationListener = null
                }

                override fun onServiceUnregistered(
                    serviceInfo: NsdServiceInfo
                ) {
                    registrationListener = null
                }

                override fun onUnregistrationFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int
                ) {
                    registrationListener = null
                }
            }

        registrationListener = listener

        try {

            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                listener
            )

        } catch (_: Exception) {

            registrationListener = null
        }
    }

    private fun startUdpDiscovery() {

        if (discoveryJob?.isActive == true) {
            return
        }

        discoveryJob =
            scope.launch {

                var socket: DatagramSocket? = null

                try {

                    socket =
                        DatagramSocket(
                            DISCOVERY_PORT
                        ).apply {
                            reuseAddress = true
                            broadcast = true
                            soTimeout = 2000
                        }

                    val buffer =
                        ByteArray(1024)

                    while (isActive) {

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
                                message != DISCOVERY_REQUEST
                            ) {
                                continue
                            }

                            val response =
                                "$DISCOVERY_RESPONSE|$SERVICE_NAME|$port"

                            val responseBytes =
                                response.toByteArray(
                                    Charsets.UTF_8
                                )

                            val responsePacket =
                                DatagramPacket(
                                    responseBytes,
                                    responseBytes.size,
                                    packet.address,
                                    packet.port
                                )

                            socket.send(
                                responsePacket
                            )

                        } catch (_: java.net.SocketTimeoutException) {

                            // Permitir comprobar isActive
                            // periódicamente.
                        } catch (_: Exception) {

                            if (!isActive) {
                                break
                            }
                        }
                    }

                } catch (_: Exception) {

                    // El descubrimiento UDP no debe
                    // detener el Receiver TCP.

                } finally {

                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }
                }
            }
    }

    fun stop() {

        discoveryJob?.cancel()
        discoveryJob = null

        val listener =
            registrationListener

        if (listener != null) {

            try {

                nsdManager.unregisterService(
                    listener
                )

            } catch (_: Exception) {
            }
        }

        registrationListener = null
    }
}
