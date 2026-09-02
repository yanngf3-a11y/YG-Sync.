package com.ygsync.receiver

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ygsync.receiver.network.ReceiverServer
import com.ygsync.receiver.network.ReceiverService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import java.net.Socket

private val Blue = Color(0xFF2563EB)
private val SkyBlue = Color(0xFF38BDF8)
private val Background = Color(0xFFF5F8FC)
private val TextDark = Color(0xFF172033)
private val TextSecondary = Color(0xFF718096)
private val CardWhite = Color.White
private val Success = Color(0xFF22C55E)
private val SoftBlue = Color(0xFFEAF2FF)
private val ErrorRed = Color(0xFFEF4444)

private const val RECEIVER_PORT = 8765

class MainActivity : ComponentActivity() {

    private val receiverScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private lateinit var receiverServer: ReceiverServer
    private lateinit var receiverService: ReceiverService

    private var serverStarted by mutableStateOf(false)
    private var localIp by mutableStateOf("Buscando IP...")
    private var lastConnection by mutableStateOf("Ninguna conexión recibida")
    private var lastCommand by mutableStateOf("Ningún comando recibido")
    private var serverError by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        localIp = getLocalIpAddress()

        receiverServer = ReceiverServer(
            port = RECEIVER_PORT
        )

        receiverService = ReceiverService(
            context = applicationContext,
            port = RECEIVER_PORT
        )

        receiverServer.start(
            scope = receiverScope
        ) { message, socket ->

            runOnUiThread {

                lastConnection =
                    "Conexión recibida desde ${socket.inetAddress.hostAddress}"

                lastCommand =
                    message
            }

            handleMessage(
                message = message,
                socket = socket
            )
        }

        receiverService.start()

        serverStarted = true

        setContent {
            YGSyncReceiverApp(
                serverStarted = serverStarted,
                localIp = localIp,
                lastConnection = lastConnection,
                lastCommand = lastCommand,
                serverError = serverError
            )
        }
    }

    private fun handleMessage(
        message: String,
        socket: Socket
    ) {

        when {

            message == "PING" -> {

                receiverServer.send(
                    socket = socket,
                    message = "PONG"
                )
            }

            message == "PLAY" -> {
            }

            message == "PAUSE" -> {
            }

            message == "STOP" -> {
            }

            message.startsWith("SEEK:") -> {
            }

            message.startsWith("VOLUME:") -> {
            }

            message.startsWith("LOAD_VIDEO:") -> {
            }
        }
    }

    private fun getLocalIpAddress(): String {

        return try {

            val connectivityManager =
                getSystemService(
                    Context.CONNECTIVITY_SERVICE
                ) as ConnectivityManager

            val activeNetwork =
                connectivityManager.activeNetwork
                    ?: return "No disponible"

            val linkProperties: LinkProperties =
                connectivityManager.getLinkProperties(
                    activeNetwork
                )
                    ?: return "No disponible"

            linkProperties.linkAddresses
                .map { it.address.hostAddress }
                .firstOrNull {
                    it != null &&
                        !it.startsWith("127.") &&
                        !it.contains(":")
                }
                ?: "No disponible"

        } catch (_: Exception) {

            "No disponible"
        }
    }

    private fun stopReceiver() {

        receiverService.stop()
        receiverServer.stop()

        if (receiverScope.isActive) {
            receiverScope.cancel()
        }
    }

    override fun onDestroy() {

        stopReceiver()

        super.onDestroy()
    }
}

@Composable
fun YGSyncReceiverApp(
    serverStarted: Boolean,
    localIp: String,
    lastConnection: String,
    lastCommand: String,
    serverError: String
) {

    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {

            ReceiverScreen(
                serverStarted = serverStarted,
                localIp = localIp,
                lastConnection = lastConnection,
                lastCommand = lastCommand,
                serverError = serverError
            )
        }
    }
}

@Composable
fun ReceiverScreen(
    serverStarted: Boolean,
    localIp: String,
    lastConnection: String,
    lastCommand: String,
    serverError: String
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Blue,
                            SkyBlue
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {

            Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(58.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        Text(
            text = "YG Sync",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )

        Text(
            text = "Receiver",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Blue
        )

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        ReceiverDiagnosticCard(
            serverStarted = serverStarted,
            localIp = localIp,
            lastConnection = lastConnection,
            lastCommand = lastCommand,
            serverError = serverError
        )
    }
}

@Composable
fun ReceiverDiagnosticCard(
    serverStarted: Boolean,
    localIp: String,
    lastConnection: String,
    lastCommand: String,
    serverError: String
) {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = CardWhite,
        shadowElevation = 4.dp
    ) {

        Column(
            modifier = Modifier.padding(28.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (serverStarted) {
                                Success
                            } else {
                                ErrorRed
                            }
                        )
                )

                Spacer(
                    modifier = Modifier.size(10.dp)
                )

                Text(
                    text =
                        if (serverStarted) {
                            "Receiver activo"
                        } else {
                            "Error del Receiver"
                        },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            Spacer(
                modifier = Modifier.height(22.dp)
            )

            DiagnosticRow(
                icon = Icons.Default.Wifi,
                title = "Red local",
                value = localIp
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            DiagnosticRow(
                icon = Icons.Default.NetworkCheck,
                title = "Servidor TCP",
                value =
                    if (serverStarted) {
                        "Escuchando en puerto $RECEIVER_PORT"
                    } else {
                        "No iniciado"
                    }
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            DiagnosticRow(
                icon = Icons.Default.Devices,
                title = "Master",
                value = lastConnection
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            DiagnosticRow(
                icon = Icons.Default.CheckCircle,
                title = "Último comando",
                value = lastCommand
            )

            if (serverError.isNotBlank()) {

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Text(
                    text = serverError,
                    fontSize = 13.sp,
                    color = ErrorRed
                )
            }
        }
    }
}

@Composable
fun DiagnosticRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(
                    RoundedCornerShape(13.dp)
                )
                .background(SoftBlue),
            contentAlignment = Alignment.Center
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Blue,
                modifier = Modifier.size(23.dp)
            )
        }

        Spacer(
            modifier = Modifier.size(14.dp)
        )

        Column {

            Text(
                text = title,
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(
                modifier = Modifier.height(2.dp)
            )

            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
            )
        }
    }
}
