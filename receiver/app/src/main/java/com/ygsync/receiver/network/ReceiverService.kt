package com.ygsync.receiver.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class ReceiverService(
    context: Context,
    private val port: Int = 8765
) {

    companion object {
        const val SERVICE_TYPE = "_ygsync._tcp."
        const val SERVICE_NAME = "YG Sync Receiver"
    }

    private val nsdManager =
        context.getSystemService(
            Context.NSD_SERVICE
        ) as NsdManager

    private var registrationListener:
        NsdManager.RegistrationListener? = null

    fun start() {

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

    fun stop() {

        val listener =
            registrationListener
                ?: return

        try {

            nsdManager.unregisterService(
                listener
            )

        } catch (_: Exception) {
        }

        registrationListener = null
    }
}
