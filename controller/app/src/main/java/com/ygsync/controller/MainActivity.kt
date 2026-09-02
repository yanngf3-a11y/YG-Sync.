package com.ygsync.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ygsync.controller.data.Receiver
import com.ygsync.controller.network.ReceiverDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private val Blue = Color(0xFF2563EB)
private val SkyBlue = Color(0xFF38BDF8)
private val Background = Color(0xFFF5F8FC)
private val TextDark = Color(0xFF172033)
private val TextSecondary = Color(0xFF718096)
private val CardWhite = Color.White
private val Success = Color(0xFF22C55E)
private val Warning = Color(0xFFF59E0B)
private val ErrorRed = Color(0xFFEF4444)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YGSyncApp()
        }
    }
}

@Composable
fun YGSyncApp() {

    val context = LocalContext.current

    val receivers = remember {
        mutableStateListOf<Receiver>()
    }

    var discovering by remember {
        mutableStateOf(false)
    }

    var diagnostic by remember {
        mutableStateOf("Preparando descubrimiento...")
    }

    var discoveryError by remember {
        mutableStateOf(false)
    }

    val discovery = remember(context) {
        ReceiverDiscovery(context)
    }

    fun startDiscovery() {

        if (discovering) {
            return
        }

        discovering = true
        discoveryError = false
        diagnostic = "🔎 Buscando servicio YG Sync..."

        CoroutineScope(Dispatchers.Main).launch {

            try {

                discovery.discoverReceivers()
                    .collect { receiver ->

                        val existingIndex =
                            receivers.indexOfFirst {
                                it.id == receiver.id
                            }

                        if (existingIndex == -1) {

                            receivers.add(receiver)

                        } else {

                            receivers[existingIndex] = receiver
                        }

                        discovering = false
                        diagnostic =
                            "✅ Pantalla encontrada: ${receiver.name}"
                    }

            } catch (exception: Exception) {

                discovering = false
                discoveryError = true

                diagnostic =
                    "❌ Error NSD: ${exception.message ?: "error desconocido"}"
            }
        }
    }

    LaunchedEffect(Unit) {
        startDiscovery()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {

            Header()

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            ConnectionSummary(
                receiverCount = receivers.size,
                discovering = discovering
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            NowPlayingCard()

            Spacer(
                modifier = Modifier.height(28.dp)
            )

            SectionHeader(
                title = "Pantallas",
                action = if (discovering) {
                    "Buscando..."
                } else {
                    "Actualizar"
                },
                onClick = {
                    startDiscovery()
                }
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            DiagnosticCard(
                message = diagnostic,
                isError = discoveryError
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            if (receivers.isEmpty()) {

                EmptyState(
                    discovering = discovering,
                    onRefresh = {
                        startDiscovery()
                    }
                )

            } else {

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    items(
                        items = receivers,
                        key = { it.id }
                    ) { receiver ->

                        ScreenCard(
                            receiver = receiver
                        )
                    }

                    item {
                        AddScreenButton()
                    }
                }
            }
        }
    }
}

@Composable
fun Header() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(52.dp)
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
                modifier = Modifier.size(29.dp)
            )
        }

        Spacer(
            modifier = Modifier.size(14.dp)
        )

        Column {

            Text(
                text = "YG Sync",
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )

            Text(
                text = "Control Center",
                fontSize = 14.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun ConnectionSummary(
    receiverCount: Int,
    discovering: Boolean
) {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = CardWhite,
        shadowElevation = 3.dp
    ) {

        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (receiverCount > 0) {
                            Success
                        } else {
                            Warning
                        }
                    )
            )

            Spacer(
                modifier = Modifier.size(12.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = if (receiverCount > 0) {
                        "Sistema conectado"
                    } else {
                        "Buscando pantallas"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Text(
                    text = if (discovering) {
                        "Explorando la red local..."
                    } else {
                        "$receiverCount pantalla(s) encontrada(s)"
                    },
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = if (receiverCount > 0) {
                    Success
                } else {
                    Warning
                },
                modifier = Modifier.size(25.dp)
            )
        }
    }
}

@Composable
fun NowPlayingCard() {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CardWhite,
        shadowElevation = 3.dp
    ) {

        Column(
            modifier = Modifier.padding(22.dp)
        ) {

            Text(
                text = "REPRODUCCIÓN ACTUAL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Blue
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "Ningún contenido reproduciéndose",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Text(
                text = "Conecta una pantalla para comenzar.",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    action: String,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.weight(1f)
        )

        Row(
            modifier = Modifier.clickable {
                onClick()
            },
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = Blue,
                modifier = Modifier.size(18.dp)
            )

            Spacer(
                modifier = Modifier.size(5.dp)
            )

            Text(
                text = action,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Blue
            )
        }
    }
}

@Composable
fun DiagnosticCard(
    message: String,
    isError: Boolean
) {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isError) {
            Color(0xFFFFF1F2)
        } else {
            Color(0xFFEFF6FF)
        }
    ) {

        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            fontSize = 13.sp,
            color = if (isError) {
                ErrorRed
            } else {
                TextDark
            }
        )
    }
}

@Composable
fun EmptyState(
    discovering: Boolean,
    onRefresh: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 35.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color(0xFFEAF2FF)),
            contentAlignment = Alignment.Center
        ) {

            Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                tint = Blue,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(15.dp)
        )

        Text(
            text = if (discovering) {
                "Buscando pantallas..."
            } else {
                "No se encontraron pantallas"
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = "Asegúrate de que el Fire TV y este teléfono estén en la misma red Wi-Fi.",
            fontSize = 13.sp,
            color = TextSecondary
        )

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        Surface(
            modifier = Modifier.clickable {
                onRefresh()
            },
            shape = RoundedCornerShape(14.dp),
            color = Blue
        ) {

            Row(
                modifier = Modifier.padding(
                    horizontal = 18.dp,
                    vertical = 11.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(
                    modifier = Modifier.size(7.dp)
                )

                Text(
                    text = "Buscar nuevamente",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun ScreenCard(
    receiver: Receiver
) {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardWhite,
        shadowElevation = 2.dp
    ) {

        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(
                        RoundedCornerShape(14.dp)
                    )
                    .background(Color(0xFFEAF2FF)),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = Blue,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(
                modifier = Modifier.size(14.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = receiver.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Spacer(
                    modifier = Modifier.height(3.dp)
                )

                Text(
                    text = "${receiver.address}:${receiver.port}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (receiver.connected) {
                            Success
                        } else {
                            Warning
                        }
                    )
            )
        }
    }
}

@Composable
fun AddScreenButton() {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 15.dp)
            .clickable { },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = Blue,
            modifier = Modifier.size(20.dp)
        )

        Spacer(
            modifier = Modifier.size(7.dp)
        )

        Text(
            text = "Agregar pantalla",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Blue
        )
    }
}
