package com.ygsync.controller.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.ygsync.controller.data.Receiver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ReceiverDiscovery(
    context: Context
) {

    private val nsdManager =
        context.getSystemService(
            Context.NSD_SERVICE
        ) as NsdManager

    companion object {
        const val SERVICE_TYPE = "_ygsync._tcp."
    }

    fun discoverReceivers(): Flow<Receiver> =
        callbackFlow {

            val resolvedIds =
                mutableSetOf<String>()

            var stopped = false

            fun resolveService(
                serviceInfo: NsdServiceInfo
            ) {

                if (stopped) {
                    return
                }

                try {

                    nsdManager.resolveService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {

                            override fun onResolveFailed(
                                serviceInfo: NsdServiceInfo,
                                errorCode: Int
                            ) {
                                // El servicio puede desaparecer
                                // mientras se está resolviendo.
                            }

                            override fun onServiceResolved(
                                resolvedServiceInfo: NsdServiceInfo
                            ) {

                                if (stopped) {
                                    return
                                }

                                val host =
                                    resolvedServiceInfo.host
                                        ?: return

                                val address =
                                    host.hostAddress
                                        ?: return

                                val port =
                                    resolvedServiceInfo.port

                                if (address.isBlank()) {
                                    return
                                }

                                if (port <= 0) {
                                    return
                                }

                                val id =
                                    "$address:$port"

                                if (
                                    !resolvedIds.add(id)
                                ) {
                                    return
                                }

                                val name =
                                    resolvedServiceInfo.serviceName
                                        .ifBlank {
                                            "YG Sync Receiver"
                                        }

                                val receiver =
                                    Receiver(
                                        id = id,
                                        name = name,
                                        address = address,
                                        port = port
                                    )

                                trySend(receiver)
                            }
                        }
                    )

                } catch (_: Exception) {
                    // No interrumpir la búsqueda.
                }
            }

            val listener =
                object : NsdManager.DiscoveryListener {

                    override fun onDiscoveryStarted(
                        serviceType: String
                    ) {
                    }

                    override fun onServiceFound(
                        serviceInfo: NsdServiceInfo
                    ) {

                        if (stopped) {
                            return
                        }

                        val type =
                            serviceInfo.serviceType
                                ?: return

                        if (
                            type.contains(
                                "_ygsync._tcp",
                                ignoreCase = true
                            )
                        ) {
                            resolveService(
                                serviceInfo
                            )
                        }
                    }

                    override fun onServiceLost(
                        serviceInfo: NsdServiceInfo
                    ) {
                    }

                    override fun onDiscoveryStopped(
                        serviceType: String
                    ) {
                    }

                    override fun onStartDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int
                    ) {

                        stopped = true

                        close(
                            IllegalStateException(
                                "NSD_START_ERROR:$errorCode"
                            )
                        )
                    }

                    override fun onStopDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int
                    ) {
                    }
                }

            try {

                nsdManager.discoverServices(
                    SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener
                )

            } catch (exception: Exception) {

                stopped = true

                close(exception)
            }

            awaitClose {

                stopped = true

                try {
                    nsdManager.stopServiceDiscovery(
                        listener
                    )
                } catch (_: Exception) {
                }
            }
        }
}
