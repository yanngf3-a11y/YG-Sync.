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

            fun resolveService(
                serviceInfo: NsdServiceInfo
            ) {

                nsdManager.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {

                        override fun onResolveFailed(
                            serviceInfo: NsdServiceInfo,
                            errorCode: Int
                        ) {
                        }

                        override fun onServiceResolved(
                            resolvedServiceInfo: NsdServiceInfo
                        ) {

                            val host =
                                resolvedServiceInfo.host
                                    ?: return

                            val address =
                                host.hostAddress
                                    ?: return

                            val port =
                                resolvedServiceInfo.port

                            val name =
                                resolvedServiceInfo.serviceName

                            val receiver =
                                Receiver(
                                    id = "$address:$port",
                                    name = name,
                                    address = address,
                                    port = port
                                )

                            trySend(receiver)
                        }
                    }
                )
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

                        if (
                            serviceInfo.serviceType
                                .contains("_ygsync._tcp")
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

                close(exception)
            }

            awaitClose {

                try {

                    nsdManager.stopServiceDiscovery(
                        listener
                    )

                } catch (_: Exception) {
                }
            }
        }
}
