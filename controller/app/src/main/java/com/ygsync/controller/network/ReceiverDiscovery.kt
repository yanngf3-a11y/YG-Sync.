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
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    companion object {
        const val SERVICE_TYPE = "_ygsync._tcp."
    }

    fun discoverReceivers(): Flow<Receiver> = callbackFlow {

        val listener = object : NsdManager.DiscoveryListener {

            override fun onDiscoveryStarted(serviceType: String) {
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {

                if (serviceInfo.serviceType != SERVICE_TYPE) {
                    return
                }

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

                            val host = resolvedServiceInfo.host
                            val port = resolvedServiceInfo.port

                            val receiver = Receiver(
                                id = resolvedServiceInfo.serviceName,
                                name = resolvedServiceInfo.serviceName,
                                address = host.hostAddress ?: "",
                                port = port
                            )

                            trySend(receiver)
                        }
                    }
                )
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            }

            override fun onDiscoveryStopped(serviceType: String) {
            }

            override fun onStartDiscoveryFailed(
                serviceType: String,
                errorCode: Int
            ) {
                close(
                    IllegalStateException(
                        "No se pudo iniciar el descubrimiento: $errorCode"
                    )
                )
            }

            override fun onStopDiscoveryFailed(
                serviceType: String,
                errorCode: Int
            ) {
            }
        }

        nsdManager.discoverServices(
            SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            listener
        )

        awaitClose {
            nsdManager.stopServiceDiscovery(listener)
        }
    }
}
