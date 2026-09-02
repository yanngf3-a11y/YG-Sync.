package com.ygsync.receiver

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
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.ygsync.receiver.network.ReceiverServer
import com.ygsync.receiver.network.ReceiverService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Blue = Color(0xFF2563EB)
private val SkyBlue = Color(0xFF38BDF8)
private val Background = Color(0xFFF5F8FC)
private val TextDark = Color(0xFF172033)
private val TextSecondary = Color(0xFF718096)
private val CardWhite = Color.White
private val Success = Color(0xFF22C55E)

class MainActivity : ComponentActivity() {

    private val receiverScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main
        )

    private lateinit var receiverServer: ReceiverServer
    private lateinit var receiverService: ReceiverService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        receiverServer = ReceiverServer(
            port = 8765
        )

        receiverService = ReceiverService(
            context = applicationContext,
            port = 8765
        )

        receiverServer.start(
            scope = receiverScope
        ) { message ->

            handleMessage(message)
        }

        receiverService.start()

        setContent {

            YGSyncReceiverApp(
                onConnectionMessage = {}
            )
        }
    }

    private fun handleMessage(
        message: String
    ) {

        when {

            message.equals(
                "PING",
                ignoreCase = true
            ) -> {
                // Responderemos con PONG
                // cuando implementemos el protocolo.
            }

            message.equals(
                "PLAY",
                ignoreCase = true
            ) -> {
                // Próximamente.
            }

            message.equals(
                "PAUSE",
                ignoreCase = true
            ) -> {
                // Próximamente.
            }

            message.equals(
                "STOP",
                ignoreCase = true
            ) -> {
                // Próximamente.
            }

            else -> {
                // Comando todavía no implementado.
            }
        }
    }

    override fun onDestroy() {

        receiverService.stop()

        receiverServer.stop()

        receiverScope.coroutineContext.cancel()

        super.onDestroy()
    }
}

@Composable
fun YGSyncReceiverApp(
    onConnectionMessage: () -> Unit
) {

    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {

                ReceiverScreen()
            }
        }
    }
}

@Composable
fun ReceiverScreen() {

    val context = LocalContext.current

    var receiverReady by remember {
        mutableStateOf(true)
    }

    DisposableEffect(context) {

        onDispose {
            receiverReady = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = "Receiver",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Blue
        )

        Spacer(
            modifier = Modifier.height(32.dp)
        )

        ReceiverStatusCard(
            ready = receiverReady
        )
    }
}

@Composable
fun ReceiverStatusCard(
    ready: Boolean
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(26.dp)
            ),
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
                            if (ready) {
                                Success
                            } else {
                                TextSecondary
                            }
                        )
                )

                Spacer(
                    modifier = Modifier.size(10.dp)
                )

                Text(
                    text = if (ready) {
                        "Receiver activo"
                    } else {
                        "Receiver detenido"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            StatusRow(
                icon = Icons.Default.Wifi,
                title = "Red local",
                value = "Servicio YG Sync activo"
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            StatusRow(
                icon = Icons.Default.Devices,
                title = "Master",
                value = "Esperando conexión"
            )
        }
    }
}

@Composable
fun StatusRow(
    icon: ImageVector,
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
                .background(
                    Color(0xFFEAF2FF)
                ),
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

        Column(
            modifier = Modifier.weight(1f)
        ) {

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
