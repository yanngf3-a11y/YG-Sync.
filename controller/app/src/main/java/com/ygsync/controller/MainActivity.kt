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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
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

    fun startDiscovery() {

        if (discovering) return

        discovering = true
        receivers.clear()

        val discovery = ReceiverDiscovery(context)

        CoroutineScope(Dispatchers.Main).launch {

            try {

                discovery.discoverReceivers().collect { receiver ->

                    if (receivers.none { it.id == receiver.id }) {
                        receivers.add(receiver)
                    }
                }

            } catch (_: Exception) {

            } finally {

                discovering = false
            }
        }
    }

    LaunchedEffect(Unit) {
        startDiscovery()
    }

    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                item {

                    Spacer(
                        modifier = Modifier.height(18.dp)
                    )

                    Header(discovering)
                }

                item {

                    ConnectionSummary(
                        count = receivers.size
                    )
                }

                item {

                    NowPlayingCard()
                }

                item {

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
                }

                if (receivers.isEmpty()) {

                    item {

                        EmptyReceiversCard(
                            discovering = discovering
                        )
                    }

                } else {

                    items(
                        items = receivers,
                        key = { it.id }
                    ) { receiver ->

                        ScreenCard(
                            receiver = receiver
                        )
                    }
                }

                item {

                    AddScreenButton(
                        onClick = {
                            startDiscovery()
                        }
                    )
                }

                item {

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun Header(discovering: Boolean) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = "YG Sync",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (discovering) {
                                SkyBlue
                            } else {
                                Success
                            }
                        )
                )

                Spacer(
                    modifier = Modifier.size(6.dp)
                )

                Text(
                    text = if (discovering) {
                        "Buscando pantallas..."
                    } else {
                        "Master conectado"
                    },
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .size(48.dp)
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
                contentDescription = "Dispositivos",
                tint = Color.White,
                modifier = Modifier.size(25.dp)
            )
        }
    }
}

@Composable
fun ConnectionSummary(count: Int) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardWhite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
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
                    modifier = Modifier.size(27.dp)
                )
            }

            Spacer(
                modifier = Modifier.size(15.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Pantallas detectadas",
                    fontSize = 14.sp,
                    color = TextSecondary
                )

                Spacer(
                    modifier = Modifier.height(2.dp)
                )

                Text(
                    text = "$count / 10",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            Text(
                text = if (count == 0) {
                    "Esperando"
                } else {
                    "Disponibles"
                },
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun NowPlayingCard() {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardWhite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(16.dp))
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
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.size(15.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "REPRODUCCIÓN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Blue
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = "Ningún video seleccionado",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )

                    Text(
                        text = "Selecciona un video para comenzar",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(22.dp)
            )

            LinearProgressIndicator(
                progress = { 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {

                IconButton(
                    onClick = {}
                ) {

                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = TextDark
                    )
                }

                Spacer(
                    modifier = Modifier.size(12.dp)
                )

                Box(
                    modifier = Modifier
                        .size(58.dp)
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
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.size(12.dp)
                )

                IconButton(
                    onClick = {}
                ) {

                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Siguiente",
                        tint = TextDark
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Volumen",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(
                    modifier = Modifier.size(10.dp)
                )

                LinearProgressIndicator(
                    progress = { 0.65f },
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(10.dp))
                )

                Spacer(
                    modifier = Modifier.size(10.dp)
                )

                Text(
                    text = "65%",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )

        Text(
            text = action,
            fontSize = 13.sp,
            color = Blue,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun EmptyReceiversCard(
    discovering: Boolean
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardWhite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (discovering) {

                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    color = Blue,
                    strokeWidth = 3.dp
                )

                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                Text(
                    text = "Buscando receptores",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "Asegúrate de que las pantallas estén en la misma red Wi-Fi.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

            } else {

                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(38.dp)
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "No hay pantallas detectadas",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "Enciende un receptor YG Sync para comenzar.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun ScreenCard(
    receiver: Receiver
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {},
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardWhite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Color(0xFFE9F8EF)
                    ),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(25.dp)
                )
            }

            Spacer(
                modifier = Modifier.size(13.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = receiver.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
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

            Column(
                horizontalAlignment = Alignment.End
            ) {

                Text(
                    text = "Detectada",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Success
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
fun AddScreenButton(
    onClick: () -> Unit
) {

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Blue
        )
    ) {

        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null
        )

        Spacer(
            modifier = Modifier.size(8.dp)
        )

        Text(
            text = "Buscar pantallas",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
