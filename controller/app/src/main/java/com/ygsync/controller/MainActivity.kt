package com.ygsync.controller

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ygsync.controller.data.Receiver
import com.ygsync.controller.network.ControllerSyncService
import com.ygsync.controller.youtube.YgYouTubeResult
import com.ygsync.controller.youtube.YgYouTubeUiState
import com.ygsync.controller.youtube.YgYouTubeViewModel
import com.ygsync.controller.youtube.YgYouTubeViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val YgBlue = Color(0xFF1976D2)
private val YgLightBlue = Color(0xFF42A5F5)
private val YgBackground = Color(0xFFF6F8FC)
private val YgText = Color(0xFF172033)
private val YgMuted = Color(0xFF718096)

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            startSyncService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            startSyncService()
        }

        setContent {
            MaterialTheme {
                YgSyncApp(this)
            }
        }
    }

    private fun startSyncService() {
        val intent = Intent(
            this,
            ControllerSyncService::class.java
        )

        try {
            startForegroundService(intent)
        } catch (_: Exception) {
            startService(intent)
        }
    }
}

@Composable
private fun YgSyncApp(
    context: Context
) {
    val viewModel: YgYouTubeViewModel = viewModel(
        factory = YgYouTubeViewModelFactory(context)
    )

    val uiState by viewModel.uiState.collectAsState()

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    var selectedVideo by remember {
        mutableStateOf<YgYouTubeResult?>(null)
    }

    var receivers by remember {
        mutableStateOf<List<Receiver>>(emptyList())
    }

    LaunchedEffect(Unit) {
        while (true) {
            val service = ControllerSyncService.getInstance()

            if (service != null) {
                receivers = service.receiverList.value
            }

            delay(3000)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = YgBackground
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            when (selectedTab) {

                0 -> HomeScreen(
                    uiState = uiState,
                    onQueryChange = viewModel::setQuery,
                    onSearch = viewModel::search,
                    onClear = viewModel::clearSearch,
                    onVideoClick = { video ->
                        selectedVideo = video

                        sendVideoToReceivers(
                            context = context,
                            video = video
                        )
                    }
                )

                1 -> SearchScreen(
                    uiState = uiState,
                    onQueryChange = viewModel::setQuery,
                    onSearch = viewModel::search,
                    onClear = viewModel::clearSearch,
                    onVideoClick = { video ->
                        selectedVideo = video

                        sendVideoToReceivers(
                            context = context,
                            video = video
                        )
                    }
                )

                2 -> LibraryScreen(
                    selectedVideo = selectedVideo
                )

                3 -> DevicesScreen(
                    receivers = receivers,
                    onRefresh = {
                        val service =
                            ControllerSyncService.getInstance()

                        if (service != null) {
                            receivers =
                                service.receiverList.value
                        }
                    }
                )

                else -> SettingsScreen()
            }

            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = {
                    selectedTab = it
                }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    uiState: YgYouTubeUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onVideoClick: (YgYouTubeResult) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Header()

        SearchBar(
            query = uiState.query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear
        )

        if (uiState.isLoading) {

            LoadingView()

        } else if (uiState.error != null) {

            ErrorView(
                message = uiState.error
            )

        } else if (uiState.results.isNotEmpty()) {

            Text(
                text = "Resultados",
                modifier = Modifier.padding(
                    horizontal = 18.dp,
                    vertical = 10.dp
                ),
                fontWeight = FontWeight.Bold,
                color = YgText
            )

            VideoList(
                results = uiState.results,
                onVideoClick = onVideoClick
            )

        } else {

            WelcomeView()
        }
    }
}

@Composable
private fun SearchScreen(
    uiState: YgYouTubeUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onVideoClick: (YgYouTubeResult) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Header(
            title = "Buscar"
        )

        SearchBar(
            query = uiState.query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear
        )

        if (uiState.isLoading) {

            LoadingView()

        } else if (uiState.error != null) {

            ErrorView(
                message = uiState.error
            )

        } else if (uiState.results.isNotEmpty()) {

            VideoList(
                results = uiState.results,
                onVideoClick = onVideoClick
            )

        } else {

            Text(
                text = "Busca canciones, artistas, videos o géneros.",
                color = YgMuted,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@Composable
private fun Header(
    title: String = "YG Sync"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = 12.dp,
                top = 18.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = title,
                color = YgText,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "YouTube sincronizado",
                color = YgMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            YgBlue,
                            YgLightBlue
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {

            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = "Pantallas",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            placeholder = {
                Text("Buscar en YouTube...")
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Buscar"
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Limpiar"
                        )
                    }
                }
            }
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            YgBlue,
                            YgLightBlue
                        )
                    )
                )
                .clickable {
                    onSearch()
                },
            contentAlignment = Alignment.Center
        ) {

            Icon(
                Icons.Default.Search,
                contentDescription = "Buscar",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun VideoList(
    results: List<YgYouTubeResult>,
    onVideoClick: (YgYouTubeResult) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            bottom = 110.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        items(
            items = results,
            key = {
                it.videoId
            }
        ) { result ->

            VideoCard(
                result = result,
                onClick = {
                    onVideoClick(result)
                }
            )
        }
    }
}

@Composable
private fun VideoCard(
    result: YgYouTubeResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {

        Row(
            modifier = Modifier.padding(10.dp)
        ) {

            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(86.dp)
                    .clip(
                        RoundedCornerShape(12.dp)
                    )
            ) {

                AsyncImage(
                    model = result.thumbnailUrl,
                    contentDescription = result.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (result.duration.isNotEmpty()) {

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp),
                        shape = RoundedCornerShape(5.dp),
                        color = Color.Black.copy(
                            alpha = 0.78f
                        )
                    ) {

                        Text(
                            text = result.duration,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(
                                horizontal = 5.dp,
                                vertical = 2.dp
                            )
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = result.title,
                    color = YgText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Text(
                    text = result.channelName,
                    color = YgMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        YgBlue,
                                        YgLightBlue
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {

                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(
                        modifier = Modifier.width(6.dp)
                    )

                    Text(
                        text = "Sincronizar",
                        color = YgBlue,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            YgBlue,
                            YgLightBlue
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {

            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        Text(
            text = "¿Qué quieres reproducir?",
            color = YgText,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Busca una canción, artista, video o género y sincronízalo en todas tus pantallas.",
            color = YgMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {

        CircularProgressIndicator(
            color = YgBlue
        )
    }
}

@Composable
private fun ErrorView(
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "No se pudo realizar la búsqueda",
            color = YgText,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = message,
            color = YgMuted
        )
    }
}

@Composable
private fun LibraryScreen(
    selectedVideo: YgYouTubeResult?
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Header(
            title = "Biblioteca"
        )

        if (selectedVideo == null) {

            Text(
                text = "Todavía no hay una reproducción seleccionada.",
                color = YgMuted,
                modifier = Modifier.padding(24.dp)
            )

        } else {

            Text(
                text = "Última reproducción",
                color = YgText,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    horizontal = 20.dp,
                    vertical = 12.dp
                )
            )

            VideoCard(
                result = selectedVideo,
                onClick = {}
            )
        }
    }
}

@Composable
private fun DevicesScreen(
    receivers: List<Receiver>,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Header(
            title = "Pantallas"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "${receivers.size} pantallas",
                modifier = Modifier.weight(1f),
                color = YgMuted
            )

            TextButton(
                onClick = onRefresh
            ) {

                Text(
                    text = "Actualizar",
                    color = YgBlue
                )
            }
        }

        if (receivers.isEmpty()) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Icon(
                    Icons.Default.Tv,
                    contentDescription = null,
                    tint = YgMuted,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "No hay pantallas conectadas",
                    color = YgText,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Las pantallas SmartTube aparecerán aquí.",
                    color = YgMuted
                )
            }

        } else {

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 110.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                items(
                    receivers,
                    key = {
                        it.id
                    }
                ) { receiver ->

                    ReceiverCard(
                        receiver = receiver
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiverCard(
    receiver: Receiver
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {

        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (receiver.connected) {
                            Color(0xFFE8F5E9)
                        } else {
                            Color(0xFFFFF3E0)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {

                Icon(
                    Icons.Default.Tv,
                    contentDescription = null,
                    tint = if (receiver.connected) {
                        Color(0xFF2E7D32)
                    } else {
                        Color(0xFFEF6C00)
                    }
                )
            }

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = receiver.name,
                    color = YgText,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = receiver.address,
                    color = YgMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = if (receiver.connected) {
                    "Conectada"
                } else {
                    "Desconectada"
                },
                color = if (receiver.connected) {
                    Color(0xFF2E7D32)
                } else {
                    Color(0xFFEF6C00)
                },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Header(
            title = "Configuración"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {

            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                Text(
                    text = "YG Sync Controller",
                    color = YgText,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Text(
                    text = "Control sincronizado de reproducción para SmartTube.",
                    color = YgMuted
                )
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {

            BottomItem(
                icon = Icons.Default.Home,
                label = "Inicio",
                selected = selectedTab == 0,
                onClick = {
                    onTabSelected(0)
                }
            )

            BottomItem(
                icon = Icons.Default.Search,
                label = "Buscar",
                selected = selectedTab == 1,
                onClick = {
                    onTabSelected(1)
                }
            )

            BottomItem(
                icon = Icons.Default.LibraryMusic,
                label = "Biblioteca",
                selected = selectedTab == 2,
                onClick = {
                    onTabSelected(2)
                }
            )

            BottomItem(
                icon = Icons.Default.Tv,
                label = "Pantallas",
                selected = selectedTab == 3,
                onClick = {
                    onTabSelected(3)
                }
            )

            BottomItem(
                icon = Icons.Default.Settings,
                label = "Ajustes",
                selected = selectedTab == 4,
                onClick = {
                    onTabSelected(4)
                }
            )
        }
    }
}

@Composable
private fun BottomItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 12.dp,
                vertical = 5.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) {
                YgBlue
            } else {
                YgMuted
            },
            modifier = Modifier.size(23.dp)
        )

        Text(
            text = label,
            color = if (selected) {
                YgBlue
            } else {
                YgMuted
            },
            fontWeight = if (selected) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            },
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun sendVideoToReceivers(
    context: Context,
    video: YgYouTubeResult
) {
    val service = ControllerSyncService.getInstance()

    if (service == null) {
        Toast.makeText(
            context,
            "El servicio de sincronización todavía está iniciando.",
            Toast.LENGTH_SHORT
        ).show()
        return
    }

    kotlinx.coroutines.MainScope().launch {
        try {

            val success =
                service.loadVideoAndWaitReady(
                    video.videoId
                )

            Toast.makeText(
                context,
                if (success) {
                    "Video enviado a las pantallas"
                } else {
                    "El video fue enviado, pero alguna pantalla no confirmó la reproducción"
                },
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {

            Toast.makeText(
                context,
                "Error al enviar: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
