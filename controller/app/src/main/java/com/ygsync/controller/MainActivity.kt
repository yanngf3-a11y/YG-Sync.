package com.ygsync.controller

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ygsync.controller.data.Receiver
import com.ygsync.controller.network.ControllerSyncService
import com.ygsync.controller.network.ReceiverDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Blue = Color(0xFF2563EB)
private val SkyBlue = Color(0xFF38BDF8)
private val Background = Color(0xFFF5F8FC)
private val TextDark = Color(0xFF172033)
private val TextSecondary = Color(0xFF718096)
private val CardWhite = Color.White
private val Success = Color(0xFF22C55E)
private val ErrorRed = Color(0xFFEF4444)
private val SoftBlue = Color(0xFFEAF2FF)

class MainActivity : ComponentActivity() {

override fun onCreate(
    savedInstanceState: Bundle?
) {
    super.onCreate(savedInstanceState)

    startControllerService()

    setContent {
        YGSyncApp()
    }
}

private fun startControllerService() {

    val intent =
        Intent(
            this,
            ControllerSyncService::class.java
        ).apply {
            action =
                ControllerSyncService.ACTION_START
        }

    try {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            startForegroundService(
                intent
            )

        } else {

            startService(
                intent
            )
        }

    } catch (_: Exception) {
    }
}

}

@Composable
fun YGSyncApp() {

val context =
    LocalContext.current

var service by remember {
    mutableStateOf(
        ControllerSyncService.getInstance()
    )
}

/*
 * El servicio puede tardar unos milisegundos
 * en crearse después de iniciar la Activity.
 *
 * Esperamos hasta que exista para que la
 * interfaz pueda observar sus estados.
 */
LaunchedEffect(Unit) {

    repeat(20) {

        val current =
            ControllerSyncService
                .getInstance()

        if (current != null) {

            service =
                current

            return@LaunchedEffect
        }

        delay(100)
    }
}

val receiverState =
    if (service != null) {

        service!!
            .receiverList
            .collectAsState()

    } else {

        remember {
            mutableStateOf(
                emptyList<Receiver>()
            )
        }
    }

val connectionState =
    if (service != null) {

        service!!
            .connectionStates
            .collectAsState()

    } else {

        remember {
            mutableStateOf(
                emptyMap<String, Boolean>()
            )
        }
    }

val latencyState =
    if (service != null) {

        service!!
            .latencies
            .collectAsState()

    } else {

        remember {
            mutableStateOf(
                emptyMap<String, Long>()
            )
        }
    }

val serviceDiagnostic =
    if (service != null) {

        service!!
            .diagnostic
            .collectAsState()

    } else {

        remember {
            mutableStateOf(
                "Iniciando servicio..."
            )
        }
    }

var discovering by remember {
    mutableStateOf(false)
}

var discoveryError by remember {
    mutableStateOf(false)
}

var manualIp by remember {
    mutableStateOf("")
}

var manualConnecting by remember {
    mutableStateOf(false)
}

var videoId by remember {
    mutableStateOf("")
}

var currentVideo by remember {
    mutableStateOf("")
}

var commandRunning by remember {
    mutableStateOf(false)
}

val discovery =
    remember(context) {
        ReceiverDiscovery(context)
    }

fun startDiscovery() {

    discovering =
        true

    discoveryError =
        false

    val syncService =
        ControllerSyncService
            .getInstance()

    if (syncService == null) {

        discovering =
            false

        discoveryError =
            true

        return
    }

    val wifiManager =
        context.applicationContext
            .getSystemService(
                Context.WIFI_SERVICE
            ) as WifiManager

    CoroutineScope(
        Dispatchers.Main.immediate
    ).launch {

        var multicastLock:
            WifiManager.MulticastLock? =
            null

        try {

            multicastLock =
                wifiManager
                    .createMulticastLock(
                        "YGSyncDiscovery"
                    )

            multicastLock
                .setReferenceCounted(
                    false
                )

            multicastLock.acquire()

            withContext(
                Dispatchers.IO
            ) {

                discovery
                    .discoverReceivers()
                    .collect { receiver ->

                        withContext(
                            Dispatchers.Main
                        ) {

                            syncService
                                .registerReceiver(
                                    receiver
                                )

                            discovering =
                                false

                            discoveryError =
                                false
                        }
                    }
            }

        } catch (_: Exception) {

            discovering =
                false

            discoveryError =
                true

        } finally {

            try {

                if (
                    multicastLock
                        ?.isHeld == true
                ) {

                    multicastLock
                        .release()
                }

            } catch (_: Exception) {
            }
        }
    }
}

fun connectManual() {

    val ip =
        manualIp.trim()

    if (ip.isBlank()) {
        return
    }

    if (manualConnecting) {
        return
    }

    val syncService =
        ControllerSyncService
            .getInstance()

    if (syncService == null) {
        return
    }

    manualConnecting =
        true

    val receiver =
        Receiver(
            id = "$ip:8765",
            name = "Fire TV",
            address = ip,
            port = 8765
        )

    syncService.registerReceiver(
        receiver
    )

    CoroutineScope(
        Dispatchers.Main.immediate
    ).launch {

        delay(1000)

        manualConnecting =
            false
    }
}

fun sendCommand(
    command: String,
    successMessage: String
) {

    if (commandRunning) {
        return
    }

    val syncService =
        ControllerSyncService
            .getInstance()

    if (syncService == null) {

        return
    }

    commandRunning =
        true

    syncService.sendCommandAsync(
        command
    ) { success, total ->

        commandRunning =
            false

        if (total == 0) {

            return@sendCommandAsync
        }

        /*
         * La conexión permanece en el
         * ControllerSyncService.
         *
         * La Activity solo actualiza
         * la interfaz.
         */
    }
}

fun loadVideo() {

    val cleanVideoId =
        videoId.trim()

    if (cleanVideoId.isBlank()) {
        return
    }

    currentVideo =
        cleanVideoId

    sendCommand(
        command =
            "LOAD_VIDEO|$cleanVideoId",
        successMessage =
            "Video enviado"
    )
}

/*
 * Primera búsqueda automática.
 */
LaunchedEffect(
    service
) {

    if (service == null) {
        return@LaunchedEffect
    }

    delay(300)

    if (
        service!!
            .receiverList
            .value
            .isEmpty()
    ) {

        startDiscovery()
    }
}

Surface(
    modifier =
        Modifier.fillMaxSize(),
    color =
        Background
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp)
    ) {

        Header()

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        ManualConnectionCard(
            ip =
                manualIp,
            onIpChange = {
                manualIp =
                    it
            },
            onConnect = {
                connectManual()
            },
            connecting =
                manualConnecting
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        ConnectionSummary(
            receiverCount =
                receiverState.value.size,
            connectedCount =
                connectionState.value
                    .values
                    .count { it },
            discovering =
                discovering
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        NowPlayingCard(
            videoId =
                videoId,
            currentVideo =
                currentVideo,
            onVideoIdChange = {
                videoId =
                    it
            },
            onLoad = {
                loadVideo()
            },
            onPlay = {
                sendCommand(
                    command =
                        "PLAY",
                    successMessage =
                        "PLAY enviado"
                )
            },
            onPause = {
                sendCommand(
                    command =
                        "PAUSE",
                    successMessage =
                        "PAUSE enviado"
                )
            },
            onStop = {
                sendCommand(
                    command =
                        "STOP",
                    successMessage =
                        "STOP enviado"
                )
            },
            commandRunning =
                commandRunning
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        SectionHeader(
            title =
                "Pantallas",
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
            modifier =
                Modifier.height(12.dp)
        )

        DiagnosticCard(
            message =
                serviceDiagnostic.value,
            isError =
                discoveryError
        )

        Spacer(
            modifier =
                Modifier.height(14.dp)
        )

        if (
            receiverState.value.isEmpty()
        ) {

            EmptyState(
                discovering =
                    discovering,
                onRefresh = {
                    startDiscovery()
                }
            )

        } else {

            LazyColumn(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                items(
                    items =
                        receiverState.value,
                    key = {
                        it.id
                    }
                ) { receiver ->

                    ScreenCard(
                        receiver =
                            receiver,
                        connected =
                            connectionState
                                .value[
                                    receiver.id
                                ] == true,
                        latency =
                            latencyState
                                .value[
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
    modifier =
        Modifier.fillMaxWidth(),
    verticalAlignment =
        Alignment.CenterVertically
) {

    Box(
        modifier =
            Modifier
                .size(52.dp)
                .background(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    Blue,
                                    SkyBlue
                                )
                        ),
                    shape =
                        RoundedCornerShape(
                            16.dp
                        )
                ),
        contentAlignment =
            Alignment.Center
    ) {

        Icon(
            imageVector =
                Icons.Default.Devices,
            contentDescription =
                null,
            tint =
                Color.White,
            modifier =
                Modifier.size(28.dp)
        )
    }

    Spacer(
        modifier =
            Modifier.size(14.dp)
    )

    Column {

        Text(
            text =
                "YG Sync",
            fontSize =
                27.sp,
            fontWeight =
                FontWeight.Bold,
            color =
                TextDark
        )

        Text(
            text =
                "Control Center",
            fontSize =
                14.sp,
            color =
                TextSecondary
        )
    }
}

}

@Composable
fun ManualConnectionCard(
ip: String,
onIpChange: (String) -> Unit,
onConnect: () -> Unit,
connecting: Boolean
) {

Surface(
    modifier =
        Modifier.fillMaxWidth(),
    shape =
        RoundedCornerShape(22.dp),
    color =
        CardWhite,
    shadowElevation =
        3.dp
) {

    Column(
        modifier =
            Modifier.padding(20.dp)
    ) {

        Text(
            text =
                "CONEXIÓN DIRECTA",
            fontSize =
                12.sp,
            fontWeight =
                FontWeight.Bold,
            color =
                Blue
        )

        Spacer(
            modifier =
                Modifier.height(6.dp)
        )

        Text(
            text =
                "Probar conexión con Fire TV",
            fontSize =
                17.sp,
            fontWeight =
                FontWeight.Bold,
            color =
                TextDark
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        OutlinedTextField(
            value =
                ip,
            onValueChange =
                onIpChange,
            modifier =
                Modifier.fillMaxWidth(),
            singleLine =
                true,
            label = {
                Text("Dirección IP")
            },
            placeholder = {
                Text("192.168.100.15")
            },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        KeyboardType.Uri
                )
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        Button(
            onClick =
                onConnect,
            modifier =
                Modifier.fillMaxWidth(),
            enabled =
                !connecting,
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            Blue
                    ),
            shape =
                RoundedCornerShape(
                    14.dp
                )
        ) {

            Text(
                text =
                    if (connecting) {
                        "Conectando..."
                    } else {
                        "Conectar y probar PING"
                    }
            )
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                "Puerto automático: 8765",
            fontSize =
                12.sp,
            color =
                TextSecondary
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
    modifier =
        Modifier.fillMaxWidth(),
    shape =
        RoundedCornerShape(22.dp),
    color =
        CardWhite,
    shadowElevation =
        3.dp
) {

    Row(
        modifier =
            Modifier.padding(20.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .background(
                        color =
                            SoftBlue,
                        shape =
                            CircleShape
                    ),
            contentAlignment =
                Alignment.Center
        ) {

            Icon(
                imageVector =
                    Icons.Default.Wifi,
                contentDescription =
                    null,
                tint =
                    Blue,
                modifier =
                    Modifier.size(25.dp)
            )
        }

        Spacer(
            modifier =
                Modifier.size(14.dp)
        )

        Column {

            Text(
                text =
                    if (discovering) {
                        "Buscando pantallas"
                    } else {
                        "Red local"
                    },
                fontSize =
                    16.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    TextDark
            )

            Text(
                text =
                    if (discovering) {
                        "Explorando la red local..."
                    } else {
                        "$connectedCount de $receiverCount conectadas"
                    },
                fontSize =
                    13.sp,
                color =
                    TextSecondary
            )
        }
    }
}

}

@Composable
fun NowPlayingCard(
videoId: String,
currentVideo: String,
onVideoIdChange: (String) -> Unit,
onLoad: () -> Unit,
onPlay: () -> Unit,
onPause: () -> Unit,
onStop: () -> Unit,
commandRunning: Boolean
) {

Surface(
    modifier =
        Modifier.fillMaxWidth(),
    shape =
        RoundedCornerShape(24.dp),
    color =
        CardWhite,
    shadowElevation =
        3.dp
) {

    Column(
        modifier =
            Modifier.padding(22.dp)
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                imageVector =
                    Icons.Default.VideoLibrary,
                contentDescription =
                    null,
                tint =
                    Blue,
                modifier =
                    Modifier.size(22.dp)
            )

            Spacer(
                modifier =
                    Modifier.size(8.dp)
            )

            Text(
                text =
                    "CONTROL DE REPRODUCCIÓN",
                fontSize =
                    12.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    Blue
            )
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                if (currentVideo.isBlank()) {
                    "Selecciona un video de YouTube"
                } else {
                    "Video cargado: $currentVideo"
                },
            fontSize =
                18.sp,
            fontWeight =
                FontWeight.Bold,
            color =
                TextDark
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        OutlinedTextField(
            value =
                videoId,
            onValueChange =
                onVideoIdChange,
            modifier =
                Modifier.fillMaxWidth(),
            singleLine =
                true,
            enabled =
                !commandRunning,
            label = {
                Text(
                    "ID del video de YouTube"
                )
            },
            placeholder = {
                Text(
                    "Ejemplo: dQw4w9WgXcQ"
                )
            },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        KeyboardType.Ascii
                )
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        Button(
            onClick =
                onLoad,
            modifier =
                Modifier.fillMaxWidth(),
            enabled =
                !commandRunning &&
                        videoId.isNotBlank(),
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            Blue
                    ),
            shape =
                RoundedCornerShape(
                    14.dp
                )
        ) {

            Icon(
                imageVector =
                    Icons.Default.VideoLibrary,
                contentDescription =
                    null
            )

            Spacer(
                modifier =
                    Modifier.size(8.dp)
            )

            Text(
                text =
                    if (commandRunning) {
                        "Enviando..."
                    } else {
                        "Cargar video en pantallas"
                    }
            )
        }

        Spacer(
            modifier =
                Modifier.height(10.dp)
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Button(
                onClick =
                    onPlay,
                modifier =
                    Modifier.weight(1f),
                enabled =
                    !commandRunning,
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                Success
                        ),
                shape =
                    RoundedCornerShape(
                        14.dp
                    )
            ) {

                Icon(
                    imageVector =
                        Icons.Default.PlayArrow,
                    contentDescription =
                        null
                )

                Spacer(
                    modifier =
                        Modifier.size(4.dp)
                )

                Text("Play")
            }

            Button(
                onClick =
                    onPause,
                modifier =
                    Modifier.weight(1f),
                enabled =
                    !commandRunning,
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                Blue
                        ),
                shape =
                    RoundedCornerShape(
                        14.dp
                    )
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Pause,
                    contentDescription =
                        null
                )

                Spacer(
                    modifier =
                        Modifier.size(4.dp)
                )

                Text("Pausa")
            }

            Button(
                onClick =
                    onStop,
                modifier =
                    Modifier.weight(1f),
                enabled =
                    !commandRunning,
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                ErrorRed
                        ),
                shape =
                    RoundedCornerShape(
                        14.dp
                    )
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Stop,
                    contentDescription =
                        null
                )

                Spacer(
                    modifier =
                        Modifier.size(4.dp)
                )

                Text("Stop")
            }
        }
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
    modifier =
        Modifier.fillMaxWidth(),
    verticalAlignment =
        Alignment.CenterVertically
) {

    Text(
        text =
            title,
        fontSize =
            21.sp,
        fontWeight =
            FontWeight.Bold,
        color =
            TextDark
    )

    Spacer(
        modifier =
            Modifier.size(20.dp)
    )

    Row(
        modifier =
            Modifier
                .clickable {
                    onClick()
                }
                .padding(6.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text =
                action,
            fontSize =
                13.sp,
            fontWeight =
                FontWeight.SemiBold,
            color =
                Blue
        )

        Spacer(
            modifier =
                Modifier.size(4.dp)
        )

        Icon(
            imageVector =
                Icons.Default.Refresh,
            contentDescription =
                "Actualizar",
            tint =
                Blue,
            modifier =
                Modifier.size(18.dp)
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
    modifier =
        Modifier.fillMaxWidth(),
    shape =
        RoundedCornerShape(18.dp),
    color =
        if (isError) {
            Color(0xFFFFF1F2)
        } else {
            Color(0xFFEFF6FF)
        }
) {

    Text(
        text =
            message,
        modifier =
            Modifier.padding(16.dp),
        fontSize =
            13.sp,
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
    modifier =
        Modifier.fillMaxWidth(),
    horizontalAlignment =
        Alignment.CenterHorizontally
) {

    Spacer(
        modifier =
            Modifier.height(30.dp)
    )

    Box(
        modifier =
            Modifier
                .size(72.dp)
                .background(
                    color =
                        SoftBlue,
                    shape =
                        CircleShape
                ),
        contentAlignment =
            Alignment.Center
    ) {

        Icon(
            imageVector =
                Icons.Default.Devices,
            contentDescription =
                null,
            tint =
                Blue,
            modifier =
                Modifier.size(38.dp)
        )
    }

    Spacer(
        modifier =
            Modifier.height(16.dp)
    )

    Text(
        text =
            if (discovering) {
                "Buscando pantallas..."
            } else {
                "No se encontraron pantallas"
            },
        fontSize =
            18.sp,
        fontWeight =
            FontWeight.Bold,
        color =
            TextDark
    )

    Spacer(
        modifier =
            Modifier.height(8.dp)
    )

    Text(
        text =
            "Puedes probar la IP manualmente arriba.",
        fontSize =
            13.sp,
        color =
            TextSecondary
    )

    Spacer(
        modifier =
            Modifier.height(16.dp)
    )

    Button(
        onClick =
            onRefresh,
        colors =
            ButtonDefaults
                .buttonColors(
                    containerColor =
                        Blue
                ),
        shape =
            RoundedCornerShape(
                14.dp
            )
    ) {

        Icon(
            imageVector =
                Icons.Default.Refresh,
            contentDescription =
                null
        )

        Spacer(
            modifier =
                Modifier.size(8.dp)
        )

        Text(
            text =
                "Buscar nuevamente"
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
    modifier =
        Modifier.fillMaxWidth(),
    shape =
        RoundedCornerShape(22.dp),
    color =
        CardWhite,
    shadowElevation =
        3.dp
) {

    Row(
        modifier =
            Modifier.padding(18.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .background(
                        color =
                            SoftBlue,
                        shape =
                            RoundedCornerShape(
                                15.dp
                            )
                    ),
            contentAlignment =
                Alignment.Center
        ) {

            Icon(
                imageVector =
                    Icons.Default.Devices,
                contentDescription =
                    null,
                tint =
                    Blue,
                modifier =
                    Modifier.size(25.dp)
            )
        }

        Spacer(
            modifier =
                Modifier.size(14.dp)
        )

        Column {

            Text(
                text =
                    receiver.name,
                fontSize =
                    16.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    TextDark
            )

            Text(
                text =
                    receiver.address,
                fontSize =
                    12.sp,
                color =
                    TextSecondary
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    when {

                        connected &&
                                latency != null ->
                            "🟢 Conectada · ${latency} ms"

                        connected ->
                            "🟢 Conectada"

                        else ->
                            "⚪ Desconectada"
                    },
                fontSize =
                    13.sp,
                fontWeight =
                    FontWeight.SemiBold,
                color =
                    if (connected) {
                        Success
                    } else {
                        TextSecondary
                    }
            )
        }
    }
}

}

@Composable
fun AddScreenButton() {

Surface(
    modifier =
        Modifier.fillMaxWidth(),
    shape =
        RoundedCornerShape(20.dp),
    color =
        SoftBlue
) {

    Row(
        modifier =
            Modifier.padding(18.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector =
                Icons.Default.Add,
            contentDescription =
                null,
            tint =
                Blue
        )

        Spacer(
            modifier =
                Modifier.size(10.dp)
        )

        Text(
            text =
                "Agregar otra pantalla",
            fontSize =
                15.sp,
            fontWeight =
                FontWeight.SemiBold,
            color =
                Blue
        )
    }
}

}
