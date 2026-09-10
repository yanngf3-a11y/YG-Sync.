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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
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
private val Background = Color(0xFFF4F7FB)
private val TextDark = Color(0xFF172033)
private val TextSecondary = Color(0xFF718096)
private val CardWhite = Color.White
private val Success = Color(0xFF22C55E)
private val ErrorRed = Color(0xFFEF4444)
private val SoftBlue = Color(0xFFEAF2FF)
private val SoftGreen = Color(0xFFEAFBF0)
private val SoftRed = Color(0xFFFFF1F2)
private val Border = Color(0xFFE2E8F0)

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
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
        }
    }
}

@Composable
fun YGSyncApp() {

    val context =
        androidx.compose.ui.platform.LocalContext.current

    var service by remember {
        mutableStateOf(
            ControllerSyncService.getInstance()
        )
    }

    LaunchedEffect(Unit) {
        repeat(40) {
            val current =
                ControllerSyncService.getInstance()

            if (current != null) {
                service = current
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

    var globalVolume by remember {
        mutableFloatStateOf(1.0f)
    }

    var muted by remember {
        mutableStateOf(false)
    }

    val discovery =
        remember(context) {
            ReceiverDiscovery(context)
        }

    fun startDiscovery() {

        if (discovering) return

        discovering = true
        discoveryError = false

        val syncService =
            ControllerSyncService.getInstance()

        if (syncService == null) {
            discovering = false
            discoveryError = true
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
                WifiManager.MulticastLock? = null

            try {

                multicastLock =
                    wifiManager
                        .createMulticastLock(
                            "YGSyncDiscovery"
                        )

                multicastLock
                    .setReferenceCounted(false)

                multicastLock.acquire()

                withContext(Dispatchers.IO) {

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

                                discoveryError =
                                    false
                            }
                        }
                }

            } catch (_: Exception) {

                discoveryError = true

            } finally {

                discovering = false

                try {
                    if (
                        multicastLock
                            ?.isHeld == true
                    ) {
                        multicastLock.release()
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun connectManual() {

        val ip =
            manualIp.trim()

        if (ip.isBlank()) return
        if (manualConnecting) return

        val syncService =
            ControllerSyncService.getInstance()
                ?: return

        manualConnecting = true

        val receiver =
            Receiver(
                id = "$ip:8765",
                name = "Pantalla manual",
                address = ip,
                port = 8765
            )

        syncService.registerReceiver(
            receiver
        )

        CoroutineScope(
            Dispatchers.Main.immediate
        ).launch {
            delay(1200)
            manualConnecting = false
        }
    }

    fun sendCommand(
        command: String
    ) {

        if (commandRunning) return

        val syncService =
            ControllerSyncService.getInstance()
                ?: return

        commandRunning = true

        syncService.sendCommandAsync(
            command
        ) { _, _ ->
            commandRunning = false
        }
    }

    fun sendIndividualCommand(
        receiver: Receiver,
        command: String
    ) {

        val syncService =
            ControllerSyncService.getInstance()
                ?: return

        CoroutineScope(
            Dispatchers.Main.immediate
        ).launch {

            withContext(Dispatchers.IO) {

                syncService.sendCommand(
                    receiver.id,
                    command
                )
            }
        }
    }

    fun extractVideoId(
        input: String
    ): String {

        val clean =
            input.trim()

        return when {

            clean.contains("youtu.be/") ->
                clean
                    .substringAfter("youtu.be/")
                    .substringBefore("?")
                    .substringBefore("&")
                    .trim()

            clean.contains("youtube.com/watch?v=") ->
                clean
                    .substringAfter("watch?v=")
                    .substringBefore("&")
                    .trim()

            clean.contains("youtube.com/shorts/") ->
                clean
                    .substringAfter("youtube.com/shorts/")
                    .substringBefore("?")
                    .substringBefore("&")
                    .trim()

            else ->
                clean
        }
    }

    fun loadVideo() {

        val cleanVideoId =
            extractVideoId(videoId)

        if (cleanVideoId.isBlank()) {
            return
        }

        val syncService =
            ControllerSyncService.getInstance()
                ?: return

        videoId =
            cleanVideoId

        commandRunning = true

        CoroutineScope(
            Dispatchers.Main.immediate
        ).launch {

            val ready =
                syncService
                    .loadVideoAndWaitReady(
                        cleanVideoId
                    )

            commandRunning = false

            if (ready) {
                currentVideo =
                    cleanVideoId
            }
        }
    }

    fun setGlobalVolume(
        value: Float
    ) {

        globalVolume =
            value.coerceIn(0f, 1f)

        muted =
            globalVolume <= 0f

        val volume =
            if (muted) {
                0f
            } else {
                globalVolume
            }

        sendCommand(
            "SET_VOLUME|$volume"
        )
    }

    fun toggleMute() {

        if (muted) {

            muted = false

            val restored =
                if (globalVolume <= 0f) {
                    1f
                } else {
                    globalVolume
                }

            setGlobalVolume(restored)

        } else {

            muted = true

            sendCommand(
                "SET_VOLUME|0.0"
            )
        }
    }

    LaunchedEffect(service) {

        if (service == null) {
            return@LaunchedEffect
        }

        delay(500)

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
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                ),
        color =
            Background
    ) {

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 18.dp,
                    bottom = 32.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {

            item {
                Header()
            }

            item {
                ManualConnectionCard(
                    ip = manualIp,
                    onIpChange = {
                        manualIp = it
                    },
                    onConnect = {
                        connectManual()
                    },
                    connecting =
                        manualConnecting
                )
            }

            item {
                ConnectionSummary(
                    receiverCount =
                        receiverState.value.size,
                    connectedCount =
                        connectionState
                            .value
                            .values
                            .count { it },
                    discovering =
                        discovering
                )
            }

            item {
                GlobalControlCard(
                    commandRunning =
                        commandRunning,
                    volume =
                        globalVolume,
                    muted =
                        muted,
                    onPlay = {
                        sendCommand("PLAY")
                    },
                    onPause = {
                        sendCommand("PAUSE")
                    },
                    onStop = {
                        sendCommand("STOP")
                    },
                    onPrevious = {
                        sendCommand("PREVIOUS")
                    },
                    onNext = {
                        sendCommand("NEXT")
                    },
                    onSync = {
                        sendCommand("SYNC")
                    },
                    onVolumeChange = {
                        setGlobalVolume(it)
                    },
                    onMute = {
                        toggleMute()
                    }
                )
            }

            item {
                NowPlayingCard(
                    videoId =
                        videoId,
                    currentVideo =
                        currentVideo,
                    onVideoIdChange = {
                        videoId = it
                    },
                    onLoad = {
                        loadVideo()
                    },
                    commandRunning =
                        commandRunning
                )
            }

            item {
                SectionHeader(
                    title =
                        "Pantallas",
                    discovering =
                        discovering,
                    onClick =
                        {
                            startDiscovery()
                        }
                )
            }

            item {
                DiagnosticCard(
                    message =
                        serviceDiagnostic.value,
                    isError =
                        discoveryError
                )
            }

            if (
                receiverState.value.isEmpty()
            ) {

                item {
                    EmptyState(
                        discovering =
                            discovering,
                        onRefresh =
                            {
                                startDiscovery()
                            }
                    )
                }

            } else {

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
                                ],
                        onPlay = {
                            sendIndividualCommand(
                                receiver,
                                "PLAY"
                            )
                        },
                        onPause = {
                            sendIndividualCommand(
                                receiver,
                                "PAUSE"
                            )
                        },
                        onStop = {
                            sendIndividualCommand(
                                receiver,
                                "STOP"
                            )
                        },
                        onPrevious = {
                            sendIndividualCommand(
                                receiver,
                                "PREVIOUS"
                            )
                        },
                        onNext = {
                            sendIndividualCommand(
                                receiver,
                                "NEXT"
                            )
                        },
                        onMute = {
                            sendIndividualCommand(
                                receiver,
                                "SET_VOLUME|0.0"
                            )
                        }
                    )
                }

                item {
                    AddScreenButton()
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
                    .size(54.dp)
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
                            RoundedCornerShape(17.dp)
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
                    Modifier.size(29.dp)
            )
        }

        Spacer(
            modifier =
                Modifier.width(13.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text =
                    "YG Sync",
                fontSize =
                    28.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    TextDark
            )

            Text(
                text =
                    "Control Center",
                fontSize =
                    13.sp,
                color =
                    TextSecondary
            )
        }

        Box(
            modifier =
                Modifier
                    .background(
                        SoftGreen,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(
                        horizontal = 10.dp,
                        vertical = 7.dp
                    )
        ) {

            Text(
                text =
                    "MASTER",
                fontSize =
                    10.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    Success
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
                Modifier.padding(18.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier =
                        Modifier
                            .size(38.dp)
                            .background(
                                SoftBlue,
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
                            Modifier.size(20.dp)
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(10.dp)
                )

                Column {

                    Text(
                        text =
                            "Conexión directa",
                        fontSize =
                            16.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            TextDark
                    )

                    Text(
                        text =
                            "Agregar una pantalla por IP",
                        fontSize =
                            12.sp,
                        color =
                            TextSecondary
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                OutlinedTextField(
                    value =
                        ip,
                    onValueChange =
                        onIpChange,
                    modifier =
                        Modifier.weight(1f),
                    singleLine =
                        true,
                    label = {
                        Text("IP")
                    },
                    placeholder = {
                        Text("192.168.1.20")
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Uri
                        )
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                Button(
                    onClick =
                        onConnect,
                    enabled =
                        !connecting &&
                                ip.isNotBlank(),
                    modifier =
                        Modifier.height(56.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                Blue
                        ),
                    shape =
                        RoundedCornerShape(14.dp)
                ) {

                    Text(
                        text =
                            if (connecting) {
                                "..."
                            } else {
                                "Conectar"
                            }
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(7.dp)
            )

            Text(
                text =
                    "Puerto YG Sync · 8765",
                fontSize =
                    11.sp,
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
            RoundedCornerShape(20.dp),
        color =
            CardWhite,
        shadowElevation =
            2.dp
    ) {

        Row(
            modifier =
                Modifier.padding(17.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier =
                    Modifier
                        .size(46.dp)
                        .background(
                            SoftBlue,
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
                        Modifier.size(24.dp)
                )
            }

            Spacer(
                modifier =
                    Modifier.width(12.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text =
                        "Pantallas conectadas",
                    fontSize =
                        15.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        TextDark
                )

                Text(
                    text =
                        if (discovering) {
                            "Buscando en la red local..."
                        } else {
                            "$connectedCount / $receiverCount activas"
                        },
                    fontSize =
                        12.sp,
                    color =
                        TextSecondary
                )
            }

            Box(
                modifier =
                    Modifier
                        .background(
                            if (connectedCount > 0) {
                                SoftGreen
                            } else {
                                SoftRed
                            },
                            RoundedCornerShape(12.dp)
                        )
                        .padding(
                            horizontal = 10.dp,
                            vertical = 7.dp
                        )
            ) {

                Text(
                    text =
                        if (connectedCount > 0) {
                            "ONLINE"
                        } else {
                            "OFFLINE"
                        },
                    fontSize =
                        10.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        if (connectedCount > 0) {
                            Success
                        } else {
                            ErrorRed
                        }
                )
            }
        }
    }
}

@Composable
fun GlobalControlCard(
    commandRunning: Boolean,
    volume: Float,
    muted: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSync: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMute: () -> Unit
) {

    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(24.dp),
        color =
            CardWhite,
        shadowElevation =
            4.dp
    ) {

        Column(
            modifier =
                Modifier.padding(19.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            "CONTROL GLOBAL",
                        fontSize =
                            11.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            Blue
                    )

                    Text(
                        text =
                            "Todas las pantallas",
                        fontSize =
                            18.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            TextDark
                    )
                }

                Icon(
                    imageVector =
                        Icons.Default.Sync,
                    contentDescription =
                        null,
                    tint =
                        Blue,
                    modifier =
                        Modifier.size(24.dp)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(15.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {

                ControlButton(
                    modifier =
                        Modifier.weight(1f),
                    icon =
                        Icons.Default.SkipPrevious,
                    text =
                        "Anterior",
                    enabled =
                        !commandRunning,
                    onClick =
                        onPrevious
                )

                ControlButton(
                    modifier =
                        Modifier.weight(1f),
                    icon =
                        Icons.Default.PlayArrow,
                    text =
                        "Play",
                    enabled =
                        !commandRunning,
                    filled =
                        true,
                    onClick =
                        onPlay
                )

                ControlButton(
                    modifier =
                        Modifier.weight(1f),
                    icon =
                        Icons.Default.Pause,
                    text =
                        "Pausa",
                    enabled =
                        !commandRunning,
                    onClick =
                        onPause
                )

                ControlButton(
                    modifier =
                        Modifier.weight(1f),
                    icon =
                        Icons.Default.Stop,
                    text =
                        "Stop",
                    enabled =
                        !commandRunning,
                    danger =
                        true,
                    onClick =
                        onStop
                )

                ControlButton(
                    modifier =
                        Modifier.weight(1f),
                    icon =
                        Icons.Default.SkipNext,
                    text =
                        "Siguiente",
                    enabled =
                        !commandRunning,
                    onClick =
                        onNext
                )
            }

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                IconButton(
                    onClick =
                        onMute
                ) {

                    Icon(
                        imageVector =
                            if (muted) {
                                Icons.Default.VolumeOff
                            } else {
                                Icons.Default.VolumeUp
                            },
                        contentDescription =
                            "Silenciar",
                        tint =
                            Blue
                    )
                }

                Icon(
                    imageVector =
                        Icons.Default.VolumeDown,
                    contentDescription =
                        null,
                    tint =
                        TextSecondary,
                    modifier =
                        Modifier.size(20.dp)
                )

                Slider(
                    value =
                        if (muted) {
                            0f
                        } else {
                            volume
                        },
                    onValueChange =
                        onVolumeChange,
                    modifier =
                        Modifier.weight(1f),
                    valueRange =
                        0f..1f
                )

                Icon(
                    imageVector =
                        Icons.Default.VolumeUp,
                    contentDescription =
                        null,
                    tint =
                        TextSecondary,
                    modifier =
                        Modifier.size(20.dp)
                )

                Spacer(
                    modifier =
                        Modifier.width(5.dp)
                )

                Text(
                    text =
                        "${(volume * 100).toInt()}%",
                    fontSize =
                        12.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        TextDark
                )
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            OutlinedButton(
                onClick =
                    onSync,
                modifier =
                    Modifier.fillMaxWidth(),
                enabled =
                    !commandRunning,
                shape =
                    RoundedCornerShape(14.dp)
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Sync,
                    contentDescription =
                        null
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                Text(
                    text =
                        "Sincronizar todas las pantallas"
                )
            }
        }
    }
}

@Composable
fun ControlButton(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    enabled: Boolean,
    filled: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {

    if (filled) {

        Button(
            onClick =
                onClick,
            modifier =
                modifier,
            enabled =
                enabled,
            contentPadding =
                PaddingValues(
                    horizontal = 2.dp,
                    vertical = 8.dp
                ),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        Blue
                ),
            shape =
                RoundedCornerShape(13.dp)
        ) {

            Icon(
                imageVector =
                    icon,
                contentDescription =
                    null,
                modifier =
                    Modifier.size(18.dp)
            )
        }

    } else {

        OutlinedButton(
            onClick =
                onClick,
            modifier =
                modifier,
            enabled =
                enabled,
            contentPadding =
                PaddingValues(
                    horizontal = 2.dp,
                    vertical = 8.dp
                ),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor =
                        if (danger) {
                            ErrorRed
                        } else {
                            TextDark
                        }
                ),
            shape =
                RoundedCornerShape(13.dp)
        ) {

            Icon(
                imageVector =
                    icon,
                contentDescription =
                    null,
                modifier =
                    Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun NowPlayingCard(
    videoId: String,
    currentVideo: String,
    onVideoIdChange: (String) -> Unit,
    onLoad: () -> Unit,
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
            4.dp
    ) {

        Column(
            modifier =
                Modifier.padding(19.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
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
                                    RoundedCornerShape(14.dp)
                            ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.VideoLibrary,
                        contentDescription =
                            null,
                        tint =
                            Color.White,
                        modifier =
                            Modifier.size(23.dp)
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(11.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            "REPRODUCTOR",
                        fontSize =
                            11.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            Blue
                    )

                    Text(
                        text =
                            "Enviar contenido",
                        fontSize =
                            18.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            TextDark
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            if (currentVideo.isNotBlank()) {

                Surface(
                    modifier =
                        Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(14.dp),
                    color =
                        SoftGreen
                ) {

                    Row(
                        modifier =
                            Modifier.padding(12.dp),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .background(
                                        Success,
                                        CircleShape
                                    )
                        )

                        Spacer(
                            modifier =
                                Modifier.width(9.dp)
                        )

                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                text =
                                    "REPRODUCIENDO / PREPARADO",
                                fontSize =
                                    9.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                color =
                                    Success
                            )

                            Text(
                                text =
                                    currentVideo,
                                fontSize =
                                    13.sp,
                                fontWeight =
                                    FontWeight.SemiBold,
                                color =
                                    TextDark
                            )
                        }
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(11.dp)
                )
            }

            Text(
                text =
                    "URL o ID del video",
                fontSize =
                    12.sp,
                fontWeight =
                    FontWeight.SemiBold,
                color =
                    TextDark
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            OutlinedTextField(
                value =
                    videoId,
                onValueChange =
                    onVideoIdChange,
                modifier =
                    Modifier.fillMaxWidth(),
                enabled =
                    !commandRunning,
                singleLine =
                    true,
                label = {
                    Text("YouTube")
                },
                placeholder = {
                    Text(
                        "https://youtube.com/watch?v=..."
                    )
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Uri
                    ),
                shape =
                    RoundedCornerShape(15.dp)
            )

            Spacer(
                modifier =
                    Modifier.height(11.dp)
            )

            /*
             * ESTE BOTÓN SIEMPRE PERMANECE EN LA INTERFAZ.
             * Solo cambia su texto/estado mientras espera READY.
             */
            Button(
                onClick =
                    onLoad,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                enabled =
                    !commandRunning &&
                            videoId.isNotBlank(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            Blue,
                        disabledContainerColor =
                            Color(0xFFB8C7E6)
                    ),
                shape =
                    RoundedCornerShape(15.dp)
            ) {

                Icon(
                    imageVector =
                        Icons.Default.VideoLibrary,
                    contentDescription =
                        null,
                    modifier =
                        Modifier.size(21.dp)
                )

                Spacer(
                    modifier =
                        Modifier.width(9.dp)
                )

                Text(
                    text =
                        if (commandRunning) {
                            "PREPARANDO TODAS LAS PANTALLAS..."
                        } else {
                            "ENVIAR A TODAS LAS PANTALLAS"
                        },
                    fontSize =
                        13.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text =
                    if (commandRunning) {
                        "Esperando confirmación READY..."
                    } else {
                        "Puedes pegar una URL completa o solamente el ID de YouTube."
                    },
                fontSize =
                    11.sp,
                color =
                    TextSecondary
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    discovering: Boolean,
    onClick: () -> Unit
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            modifier =
                Modifier.weight(1f)
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

            Text(
                text =
                    "Receptores disponibles en tu red",
                fontSize =
                    11.sp,
                color =
                    TextSecondary
            )
        }

        Surface(
            modifier =
                Modifier.clickable {
                    onClick()
                },
            shape =
                RoundedCornerShape(13.dp),
            color =
                if (discovering) {
                    Color(0xFFE5EAF3)
                } else {
                    SoftBlue
                }
        ) {

            Row(
                modifier =
                    Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 9.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

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

                Spacer(
                    modifier =
                        Modifier.width(6.dp)
                )

                Text(
                    text =
                        if (discovering) {
                            "Buscando"
                        } else {
                            "Actualizar"
                        },
                    fontSize =
                        12.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        Blue
                )
            }
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
            RoundedCornerShape(15.dp),
        color =
            if (isError) {
                SoftRed
            } else {
                Color(0xFFEFF6FF)
            }
    ) {

        Row(
            modifier =
                Modifier.padding(13.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            if (isError) {
                                ErrorRed
                            } else {
                                Blue
                            },
                            CircleShape
                        )
            )

            Spacer(
                modifier =
                    Modifier.width(9.dp)
            )

            Text(
                text =
                    message,
                fontSize =
                    12.sp,
                color =
                    if (isError) {
                        ErrorRed
                    } else {
                        TextDark
                    }
            )
        }
    }
}

@Composable
fun EmptyState(
    discovering: Boolean,
    onRefresh: () -> Unit
) {

    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(22.dp),
        color =
            CardWhite,
        shadowElevation =
            2.dp
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(25.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Box(
                modifier =
                    Modifier
                        .size(70.dp)
                        .background(
                            SoftBlue,
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
                        Modifier.size(36.dp)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(13.dp)
            )

            Text(
                text =
                    if (discovering) {
                        "Buscando pantallas..."
                    } else {
                        "No hay pantallas conectadas"
                    },
                fontSize =
                    17.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    TextDark
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Text(
                text =
                    "Activa SmartTube Sync en tus receptores.",
                fontSize =
                    12.sp,
                color =
                    TextSecondary
            )

            Spacer(
                modifier =
                    Modifier.height(13.dp)
            )

            Button(
                onClick =
                    onRefresh,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            Blue
                    ),
                shape =
                    RoundedCornerShape(13.dp)
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Refresh,
                    contentDescription =
                        null
                )

                Spacer(
                    modifier =
                        Modifier.width(7.dp)
                )

                Text(
                    text =
                        "Actualizar pantallas"
                )
            }
        }
    }
}

@Composable
fun ScreenCard(
    receiver: Receiver,
    connected: Boolean,
    latency: Long?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit
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
                Modifier.padding(17.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier =
                        Modifier
                            .size(46.dp)
                            .background(
                                if (connected) {
                                    SoftGreen
                                } else {
                                    SoftBlue
                                },
                                RoundedCornerShape(14.dp)
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
                            if (connected) {
                                Success
                            } else {
                                Blue
                            },
                        modifier =
                            Modifier.size(24.dp)
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(11.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

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
                            11.sp,
                        color =
                            TextSecondary
                    )

                    Spacer(
                        modifier =
                            Modifier.height(3.dp)
                    )

                    Text(
                        text =
                            when {
                                connected &&
                                        latency != null ->
                                    "● Conectada · ${latency} ms"

                                connected ->
                                    "● Conectada"

                                else ->
                                    "○ Desconectada"
                            },
                        fontSize =
                            11.sp,
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

                Box(
                    modifier =
                        Modifier
                            .background(
                                if (connected) {
                                    SoftGreen
                                } else {
                                    SoftRed
                                },
                                RoundedCornerShape(10.dp)
                            )
                            .padding(
                                horizontal = 8.dp,
                                vertical = 6.dp
                            )
                ) {

                    Text(
                        text =
                            if (connected) {
                                "ONLINE"
                            } else {
                                "OFFLINE"
                            },
                        fontSize =
                            9.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            if (connected) {
                                Success
                            } else {
                                ErrorRed
                            }
                    )
                }
            }

            if (connected) {

                Spacer(
                    modifier =
                        Modifier.height(13.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(7.dp)
                ) {

                    MiniControl(
                        modifier =
                            Modifier.weight(1f),
                        icon =
                            Icons.Default.SkipPrevious,
                        onClick =
                            onPrevious
                    )

                    MiniControl(
                        modifier =
                            Modifier.weight(1f),
                        icon =
                            Icons.Default.PlayArrow,
                        onClick =
                            onPlay,
                        active =
                            true
                    )

                    MiniControl(
                        modifier =
                            Modifier.weight(1f),
                        icon =
                            Icons.Default.Pause,
                        onClick =
                            onPause
                    )

                    MiniControl(
                        modifier =
                            Modifier.weight(1f),
                        icon =
                            Icons.Default.Stop,
                        onClick =
                            onStop,
                        danger =
                            true
                    )

                    MiniControl(
                        modifier =
                            Modifier.weight(1f),
                        icon =
                            Icons.Default.SkipNext,
                        onClick =
                            onNext
                    )

                    MiniControl(
                        modifier =
                            Modifier.weight(1f),
                        icon =
                            Icons.Default.VolumeOff,
                        onClick =
                            onMute
                    )
                }
            }
        }
    }
}

@Composable
fun MiniControl(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    active: Boolean = false,
    danger: Boolean = false
) {

    Surface(
        modifier =
            modifier.clickable {
                onClick()
            },
        shape =
            RoundedCornerShape(12.dp),
        color =
            when {
                active ->
                    Blue

                danger ->
                    SoftRed

                else ->
                    SoftBlue
            }
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 9.dp
                    ),
            contentAlignment =
                Alignment.Center
        ) {

            Icon(
                imageVector =
                    icon,
                contentDescription =
                    null,
                tint =
                    when {
                        active ->
                            Color.White

                        danger ->
                            ErrorRed

                        else ->
                            Blue
                    },
                modifier =
                    Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun AddScreenButton() {

    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(18.dp),
        color =
            SoftBlue
    ) {

        Row(
            modifier =
                Modifier.padding(17.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .background(
                            Blue,
                            CircleShape
                        ),
                contentAlignment =
                    Alignment.Center
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Add,
                    contentDescription =
                        null,
                    tint =
                        Color.White,
                    modifier =
                        Modifier.size(20.dp)
                )
            }

            Spacer(
                modifier =
                    Modifier.width(10.dp)
            )

            Column {

                Text(
                    text =
                        "Agregar otra pantalla",
                    fontSize =
                        14.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        TextDark
                )

                Text(
                    text =
                        "Puedes controlar hasta 10 receptores",
                    fontSize =
                        11.sp,
                    color =
                        TextSecondary
                )
            }
        }
    }
}
