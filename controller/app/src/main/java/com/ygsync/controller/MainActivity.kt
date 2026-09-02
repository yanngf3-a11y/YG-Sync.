package com.ygsync.controller

import android.content.Context
import android.net.wifi.WifiManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ygsync.controller.data.Receiver
import com.ygsync.controller.network.ReceiverConnection
import com.ygsync.controller.network.ReceiverDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
private val SoftBlue = Color(0xFFEAF2FF)

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

    val connections = remember {
        mutableStateMapOf<String, ReceiverConnection>()
    }

    val latencies = remember {
        mutableStateMapOf<String, Long>()
    }

    val connectionStates = remember {
        mutableStateMapOf<String, Boolean>()
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

    fun connectToReceiver(receiver: Receiver) {

        if (connections.containsKey(receiver.id)) {
            return
        }

        val connection = ReceiverConnection(receiver)

        connections[receiver.id] = connection

        CoroutineScope(Dispatchers.Main).launch {

            diagnostic =
                "🔗 Conectando con ${receiver.name}..."

            val connected = connection.connect()

            if (!connected) {

                connectionStates[receiver.id] = false

                diagnostic =
                    "❌ No se pudo conectar con ${receiver.name}"

                connections.remove(receiver.id)

                return@launch
            }

            connectionStates[receiver.id] = true

            diagnostic =
                "🟢 Conectado: ${receiver.name}"

            val latency = connection.ping()

            if (latency != null) {

                latencies[receiver.id] = latency

                diagnostic =
                    "🟢 ${receiver.name} conectado · ${latency} ms"

            } else {

                connectionStates[receiver.id] = false

                diagnostic =
                    "🟠 Conexión creada, pero PING falló"
            }
        }
    }

    fun startDiscovery() {

        if (discovering) {
            return
        }

        discovering = true
        discoveryError = false

        diagnostic =
            "🔎 Buscando servicio YG Sync..."

        CoroutineScope(Dispatchers.Main).launch {

            try {

                val wifiManager =
                    context.applicationContext
                        .getSystemService(
                            Context.WIFI_SERVICE
                        ) as WifiManager

                val multicastLock =
                    wifiManager.createMulticastLock(
                        "YGSyncDiscovery"
                    )

                multicastLock.setReferenceCounted(false)
                multicastLock.acquire()

                try {

                    discovery
                        .discoverReceivers()
                        .collect { receiver ->

                            val existingIndex =
                                receivers.indexOfFirst {
                                    it.id == receiver.id
                                }

                            if (existingIndex == -1) {

                                receivers.add(receiver)

                            } else {

                                receivers[existingIndex] =
                                    receiver
                            }

                            discovering = false

                            diagnostic =
                                "✅ Pantalla encontrada: ${receiver.name}"

                            connectToReceiver(receiver)
                        }

                } finally {

                    if (multicastLock.isHeld) {
                        multicastLock.release()
                    }
                }

            } catch (exception: Exception) {

                discovering = false
                discoveryError = true

                diagnostic =
                    "❌ Error de descubrimiento: ${
                        exception.message
                            ?: "error desconocido"
                    }"
            }
        }
    }

    LaunchedEffect(Unit) {
        startDiscovery()
    }

    LaunchedEffect(connections.keys.toList()) {

        while (true) {

            delay(5000)

            connections.forEach { (id, connection) ->

                if (!connection.isConnected()) {

                    connectionStates[id] = false

                } else {

                    val latency = connection.ping()

                    if (latency != null) {

                        connectionStates[id] = true
                        latencies[id] = latency

                    } else {

                        connectionStates[id] = false
                    }
                }
            }
        }
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
                connectedCount =
                    connectionStates.values.count { it },
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
                action =
                    if (discovering) {
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    items(
                        items = receivers,
                        key = { it.id }
                    ) { receiver ->

                        ScreenCard(
                            receiver = receiver,
                            connected =
                                connectionStates[
                                    receiver.id
                                ] == true,
                            latency =
                                latencies[
                                    receiver.id
                                ]
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
                .clip(
                    RoundedCornerShape(16.dp)
                )
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
                modifier = Modifier.size(28.dp)
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
    connectedCount: Int,
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
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SoftBlue),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = Blue,
                    modifier = Modifier.size(25.dp)
                )
            }

            Spacer(
                modifier = Modifier.size(14.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        if (discovering) {
                            "Buscando pantallas"
                        } else {
                            "Red local"
                        },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Text(
                    text =
                        if (discovering) {
                            "Explorando la red local..."
                        } else {
                            "$connectedCount de $receiverCount conectadas"
                        },
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Spacer(
                modifier = Modifier.size(8.dp)
            )

            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            connectedCount > 0 -> Success
                            discovering -> Warning
                            else -> Color.LightGray
                        }
                    )
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
                fontSize = 12.sp,
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
                modifier = Modifier.height(4.dp)
            )

            Text(
                text =
                    "Conecta una pantalla para comenzar.",
                fontSize = 14.sp,
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
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )

        Spacer(
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
        shape = RoundedCornerShape(18.dp),
        color =
            if (isError) {
                Color(0xFFFFF1F2)
            } else {
                Color(0xFFEFF6FF)
            }
    ) {

        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            fontSize = 13.sp,
            color =
                if (isError) {
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
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(
            modifier = Modifier.height(50.dp)
        )

        Icon(
            imageVector = Icons.Default.Devices,
            contentDescription = null,
            tint = Blue,
            modifier = Modifier.size(54.dp)
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text =
                if (discovering) {
                    "Buscando pantallas..."
                } else {
                    "No se encontraron pantallas"
                },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text =
                "Asegúrate de que el Fire TV y este teléfono estén en la misma red Wi-Fi.",
            fontSize = 13.sp,
            color = TextSecondary
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Button(
            onClick = onRefresh,
            colors = ButtonDefaults.buttonColors(
                containerColor = Blue
            )
        ) {

            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null
            )

            Spacer(
                modifier = Modifier.size(8.dp)
            )

            Text(
                text = "Buscar nuevamente"
            )
        }
    }
}

@Composable
fun ScreenCard(
    receiver: Receiver,
    connected: Boolean,
    latency: Long?
) {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = CardWhite,
        shadowElevation = 3.dp
    ) {

        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(
                        RoundedCornerShape(15.dp)
                    )
                    .background(SoftBlue),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = Blue,
                    modifier = Modifier.size(25.dp)
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

                Text(
                    text = receiver.address,
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text =
                        when {
                            connected && latency != null ->
                                "Conectada · ${latency} ms"

                            connected ->
                                "Conectada"

                            else ->
                                "Desconectada"
                        },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (connected) {
                            Success
                        } else {
                            TextSecondary
                        }
                )
            }

            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(
                        if (connected) {
                            Success
                        } else {
                            Color.LightGray
                        }
                    )
            )
        }
    }
}

@Composable
fun AddScreenButton() {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SoftBlue
    ) {

        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Blue
            )

            Spacer(
                modifier = Modifier.size(10.dp)
            )

            Text(
                text = "Agregar otra pantalla",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Blue
            )
        }
    }
}
