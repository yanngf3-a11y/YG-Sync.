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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val YgBlue = Color(0xFF1976D2)
private val YgLightBlue = Color(0xFF42A5F5)
private val YgBackground = Color(0xFFF6F8FC)
private val YgText = Color(0xFF172033)
private val YgMuted = Color(0xFF718096)
private val YgSaveRed = Color(0xFFE53935)
private val YgGradientStart = Color(0xFF1565D8)
private val YgGradientEnd = Color(0xFF22D3EE)

private fun formatSeconds(totalSeconds: Int): String {

    val safeSeconds = totalSeconds.coerceAtLeast(0)

    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val seconds = safeSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun parseDurationToSeconds(duration: String): Int {

    val parts =
        duration.trim().split(":").mapNotNull {
            it.toIntOrNull()
        }

    return when (parts.size) {
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        2 -> parts[0] * 60 + parts[1]
        1 -> parts[0]
        else -> 0
    }
}

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
private fun YgSyncApp(context: Context) {

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

    var serviceDiagnosticLog by remember {
        mutableStateOf<List<String>>(emptyList())
    }

    var isPlaying by remember {
        mutableStateOf(false)
    }

    var livePositionSec by remember {
        mutableFloatStateOf(0f)
    }

    var lastKnownPositionSec by remember {
        mutableFloatStateOf(-1f)
    }

    var lastSyncedVideoId by remember {
        mutableStateOf<String?>(null)
    }

    val savedVideos = remember {
        mutableStateListOf<YgYouTubeResult>()
    }

    LaunchedEffect(Unit) {
        while (true) {
            val service =
                ControllerSyncService.getInstance()

            if (service != null) {
                receivers =
                    service.receiverList.value

                serviceDiagnosticLog =
                    service.diagnosticLog.value

                /*
                 * Tomamos la primera pantalla conectada como
                 * referencia para la barra de avance (misma
                 * que usa el mirror de autoplay del servicio).
                 */
                val reference =
                    receivers.firstOrNull { it.connected }

                if (reference != null) {

                    /*
                     * Antes "isPlaying" era una bandera local
                     * que solo cambiaba al tocar el botón de
                     * play/pausa, y quedaba pegada en "sonando"
                     * aunque el video ya hubiese terminado en
                     * la pantalla real. La sincronizamos acá
                     * con el estado real que reporta la
                     * pantalla, así el ícono y el avance de la
                     * barra siempre reflejan lo que pasa de
                     * verdad.
                     */
                    isPlaying = reference.isPlaying

                    val realPositionSec =
                        reference.playbackPosition / 1000f

                    if (realPositionSec != lastKnownPositionSec) {
                        lastKnownPositionSec = realPositionSec
                        livePositionSec = realPositionSec
                    } else if (reference.isPlaying) {
                        livePositionSec += 1f
                    }
                }

                /*
                 * Si la pantalla cambió de video sola (autoplay
                 * o "siguiente" desde el propio SmartTube), el
                 * servicio lo detecta y expone el nuevo videoId
                 * acá. Traemos su título/miniatura reales para
                 * que el reproductor deje de mostrar el video
                 * que buscaste originalmente.
                 */
                val liveVideoId =
                    service.currentVideoId.value

                if (
                    !liveVideoId.isNullOrEmpty() &&
                    liveVideoId != lastSyncedVideoId
                ) {
                    lastSyncedVideoId = liveVideoId
                    lastKnownPositionSec = -1f

                    val info =
                        viewModel.getVideoInfo(liveVideoId)

                    if (info != null) {
                        selectedVideo = info
                    }
                }
            }

            delay(1000)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = YgBackground
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {

                when (selectedTab) {

                    0 -> HomeScreen(
                        uiState = uiState,
                        selectedVideo = selectedVideo,
                        isPlaying = isPlaying,
                        positionSec = livePositionSec,
                        savedVideos = savedVideos,
                        onPlayPause = {
                            val service =
                                ControllerSyncService.getInstance()

                            if (service != null) {
                                if (isPlaying) {
                                    service.pauseAll()
                                } else {
                                    service.playAll()
                                }

                                isPlaying = !isPlaying
                            }
                        },
                        onPrevious = {
                            ControllerSyncService
                                .getInstance()
                                ?.previousAll()
                        },
                        onNext = {
                            ControllerSyncService
                                .getInstance()
                                ?.nextAll()
                        },
                        onSave = { video ->
                            if (
                                savedVideos.none {
                                    it.videoId == video.videoId
                                }
                            ) {
                                savedVideos.add(video)

                                Toast.makeText(
                                    context,
                                    "Añadido a Biblioteca",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                savedVideos.removeAll {
                                    it.videoId == video.videoId
                                }

                                Toast.makeText(
                                    context,
                                    "Eliminado de Biblioteca",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onSeek = { newPositionSec ->
                            lastKnownPositionSec = newPositionSec
                            livePositionSec = newPositionSec

                            ControllerSyncService
                                .getInstance()
                                ?.seekAll(
                                    (newPositionSec * 1000).toLong()
                                )
                        },
                        onQueryChange = viewModel::setQuery,
                        onSearch = viewModel::search,
                        onClear = viewModel::clearSearch,
                        onSuggestionClick = viewModel::selectSuggestion,
                        onVideoClick = { video ->
                            selectedVideo = video
                            isPlaying = true
                            livePositionSec = 0f
                            lastKnownPositionSec = -1f

                            sendVideoToReceivers(
                                context,
                                video
                            )
                        }
                    )

                    1 -> SearchScreen(
                        uiState = uiState,
                        onQueryChange = viewModel::setQuery,
                        onSearch = viewModel::search,
                        onClear = viewModel::clearSearch,
                        onSuggestionClick = viewModel::selectSuggestion,
                        onVideoClick = { video ->
                            selectedVideo = video
                            isPlaying = true
                            livePositionSec = 0f
                            lastKnownPositionSec = -1f

                            sendVideoToReceivers(
                                context,
                                video
                            )
                        }
                    )

                    2 -> LibraryScreen(
                        savedVideos = savedVideos,
                        onVideoClick = { video ->
                            selectedVideo = video
                            isPlaying = true
                            livePositionSec = 0f
                            lastKnownPositionSec = -1f

                            sendVideoToReceivers(
                                context,
                                video
                            )
                        }
                    )

                    3 -> DevicesScreen(
                        receivers = receivers,
                        onRefresh = {
                            val service =
                                ControllerSyncService
                                    .getInstance()

                            if (service != null) {
                                service.startDiscovery()
                                receivers =
                                    service.receiverList.value
                            }
                        }
                    )

                    4 -> TechnicalScreen(
                        receivers = receivers,
                        diagnostic = serviceDiagnosticLog,
                        onRefresh = {
                            val service =
                                ControllerSyncService
                                    .getInstance()

                            if (service != null) {
                                service.startDiscovery()
                                receivers =
                                    service.receiverList.value
                            }
                        }
                    )

                    5 -> SettingsScreen()
                }
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
    selectedVideo: YgYouTubeResult?,
    isPlaying: Boolean,
    positionSec: Float,
    savedVideos: List<YgYouTubeResult>,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSave: (YgYouTubeResult) -> Unit,
    onSeek: (Float) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onVideoClick: (YgYouTubeResult) -> Unit
) {
    /*
     * Si no hay una búsqueda activa, usamos las sugerencias
     * por defecto para que siempre haya opciones de canciones
     * distintas para tocar, sin tener que buscar.
     */
    val displayResults =
        uiState.results.ifEmpty {
            uiState.suggestions
        }

    val featuredVideo =
        selectedVideo ?: displayResults.firstOrNull()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Header()

        SearchBar(
            query = uiState.query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            suggestions = uiState.querySuggestions,
            onSuggestionClick = onSuggestionClick
        )

        if (uiState.isLoading) {

            LoadingView()

        } else if (uiState.error != null) {

            ErrorView(uiState.error)

        } else {

            if (featuredVideo != null) {

                FeaturedVideoPlayer(
                    video = featuredVideo,
                    isPlaying = isPlaying,
                    isSaved = savedVideos.any {
                        it.videoId == featuredVideo.videoId
                    },
                    positionSec = positionSec,
                    durationSec =
                        parseDurationToSeconds(
                            featuredVideo.duration
                        ),
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSave = {
                        onSave(featuredVideo)
                    },
                    onSeek = onSeek
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                Text(
                    text = "Más videos",
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 4.dp
                    ),
                    color = YgText,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium
                )

                VideoList(
                    results = displayResults,
                    onVideoClick = onVideoClick,
                    featuredVideoId = featuredVideo.videoId
                )

            } else {

                WelcomeView()
            }
        }
    }
}

@Composable
private fun HorizontalProgressBar(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.3f),
    fillBrush: Brush = Brush.horizontalGradient(
        listOf(YgGradientStart, YgGradientEnd)
    )
) {
    var barWidthPx by remember {
        mutableStateOf(0f)
    }

    val span =
        (valueRange.endInclusive - valueRange.start)
            .coerceAtLeast(0.0001f)

    val fraction =
        ((value - valueRange.start) / span)
            .coerceIn(0f, 1f)

    fun valueFromOffsetX(offsetX: Float): Float {

        if (barWidthPx <= 0f) {
            return value
        }

        val frac =
            (offsetX / barWidthPx).coerceIn(0f, 1f)

        return valueRange.start + frac * span
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .onGloballyPositioned { coordinates ->
                barWidthPx =
                    coordinates.size.width.toFloat()
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onValueChange(
                            valueFromOffsetX(offset.x)
                        )
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onValueChange(
                            valueFromOffsetX(change.position.x)
                        )
                    },
                    onDragEnd = {
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        onValueChangeFinished()
                    }
                )
            }
    ) {

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(fraction = fraction)
                .clip(RoundedCornerShape(50))
                .background(fillBrush)
        )
    }
}

@Composable
private fun FeaturedVideoPlayer(
    video: YgYouTubeResult,
    isPlaying: Boolean,
    isSaved: Boolean,
    positionSec: Float,
    durationSec: Int,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    onSeek: (Float) -> Unit
) {
    var draggingPosition by remember {
        mutableStateOf<Float?>(null)
    }

    val safeDuration =
        durationSec.coerceAtLeast(1)

    val shownPosition =
        (draggingPosition ?: positionSec)
            .coerceIn(0f, safeDuration.toFloat())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(18.dp))
        ) {

            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.78f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.82f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 13.dp
                    )
            ) {

                Text(
                    text = video.title,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(
                    modifier = Modifier.height(2.dp)
                )

                Text(
                    text = video.channelName,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {

                FeaturedControlButton(
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = "Anterior",
                    onClick = onPrevious
                )

                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(
                            Color.White.copy(alpha = 0.96f)
                        )
                        .clickable {
                            onPlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector =
                            if (isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                        contentDescription =
                            if (isPlaying) {
                                "Pausa"
                            } else {
                                "Play"
                            },
                        tint = YgBlue,
                        modifier = Modifier.size(34.dp)
                    )
                }

                FeaturedControlButton(
                    icon = Icons.Default.SkipNext,
                    contentDescription = "Siguiente",
                    onClick = onNext
                )
            }

            /*
             * Abajo: tiempo, barra de avance y guardar,
             * todo en la misma fila.
             */
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 10.dp,
                        bottom = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text =
                        "${formatSeconds(shownPosition.toInt())} / " +
                            formatSeconds(safeDuration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(end = 6.dp)
                )

                HorizontalProgressBar(
                    value = shownPosition,
                    valueRange = 0f..safeDuration.toFloat(),
                    onValueChange = {
                        draggingPosition = it
                    },
                    onValueChangeFinished = {
                        draggingPosition?.let {
                            onSeek(it)
                        }
                        draggingPosition = null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .padding(horizontal = 4.dp)
                )

                IconButton(
                    onClick = onSave,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Color.White.copy(alpha = 0.94f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Guardar",
                        tint =
                            if (isSaved) {
                                YgSaveRed
                            } else {
                                YgText
                            },
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedControlButton(
    icon:
        androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(
                Color.Black.copy(alpha = 0.48f)
            )
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun SearchScreen(
    uiState: YgYouTubeUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onVideoClick: (YgYouTubeResult) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Header(title = "Buscar")

        SearchBar(
            query = uiState.query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            suggestions = uiState.querySuggestions,
            onSuggestionClick = onSuggestionClick
        )

        if (uiState.isLoading) {

            LoadingView()

        } else if (uiState.error != null) {

            ErrorView(uiState.error)

        } else if (uiState.results.isNotEmpty()) {

            VideoList(
                results = uiState.results,
                onVideoClick = onVideoClick
            )

        } else {

            Text(
                text =
                    "Busca canciones, artistas, videos o géneros.",
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
                top = 14.dp,
                bottom = 4.dp
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
                .size(40.dp)
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
    onClear: () -> Unit,
    suggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = 5.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(17.dp),
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
                modifier = Modifier.width(7.dp)
            )

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
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

        if (suggestions.isNotEmpty()) {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 3.dp
                )
            ) {

                Column {

                    suggestions.take(6).forEach { suggestion ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSuggestionClick(suggestion)
                                }
                                .padding(
                                    horizontal = 14.dp,
                                    vertical = 11.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = YgMuted,
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(
                                modifier = Modifier.width(10.dp)
                            )

                            Text(
                                text = suggestion,
                                color = YgText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoList(
    results: List<YgYouTubeResult>,
    onVideoClick: (YgYouTubeResult) -> Unit,
    featuredVideoId: String? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            bottom = 24.dp
        ),
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {

        items(
            results.filter {
                it.videoId != featuredVideoId
            },
            key = { it.videoId }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {

        Row(
            modifier = Modifier.padding(9.dp)
        ) {

            Box(
                modifier = Modifier
                    .width(145.dp)
                    .height(82.dp)
                    .clip(
                        RoundedCornerShape(11.dp)
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
                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,
                            modifier = Modifier.padding(
                                horizontal = 5.dp,
                                vertical = 2.dp
                            )
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.width(11.dp)
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
                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = result.channelName,
                    color = YgMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )

                Spacer(
                    modifier = Modifier.height(5.dp)
                )

                Text(
                    text = "Tocar para reproducir",
                    color = YgBlue,
                    fontWeight = FontWeight.Bold,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall
                )
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
                .size(72.dp)
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
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "¿Qué quieres reproducir?",
            color = YgText,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(
            modifier = Modifier.height(7.dp)
        )

        Text(
            text =
                "Busca una canción, artista, video o género.",
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
    savedVideos: List<YgYouTubeResult>,
    onVideoClick: (YgYouTubeResult) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Header(
            title = "Biblioteca"
        )

        if (savedVideos.isEmpty()) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = YgMuted,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "Tu biblioteca está vacía",
                    color = YgText,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text =
                        "Usa el icono de marcador para guardar canciones.",
                    color = YgMuted
                )
            }

        } else {

            LazyColumn(
                contentPadding =
                    PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 24.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                items(
                    savedVideos,
                    key = {
                        "saved_${it.videoId}"
                    }
                ) { video ->

                    VideoCard(
                        result = video,
                        onClick = {
                            onVideoClick(video)
                        }
                    )
                }
            }
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
                horizontalAlignment =
                    Alignment.CenterHorizontally
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
                    text =
                        "Las pantallas SmartTube aparecerán aquí.",
                    color = YgMuted
                )
            }

        } else {

            LazyColumn(
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 24.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                items(
                    receivers,
                    key = { it.id }
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
        colors =
            CardDefaults.cardColors(
                containerColor = Color.White
            ),
        elevation =
            CardDefaults.cardElevation(
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
                    tint =
                        if (receiver.connected) {
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
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )
            }

            Text(
                text =
                    if (receiver.connected) {
                        "Conectada"
                    } else {
                        "Desconectada"
                    },
                color =
                    if (receiver.connected) {
                        Color(0xFF2E7D32)
                    } else {
                        Color(0xFFEF6C00)
                    },
                fontWeight = FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .labelSmall
            )
        }
    }
}

@Composable
private fun TechnicalScreen(
    receivers: List<Receiver>,
    diagnostic: List<String> = emptyList(),
    onRefresh: () -> Unit
) {
    var lastRefresh by remember {
        mutableStateOf<String?>(null)
    }

    val eventLog = remember {
        mutableStateListOf<String>()
    }

    fun addLog(message: String) {
        val time =
            SimpleDateFormat(
                "HH:mm:ss",
                Locale.getDefault()
            ).format(Date())

        eventLog.add(
            0,
            "$time  •  $message"
        )

        if (eventLog.size > 12) {
            eventLog.removeAt(
                eventLog.lastIndex
            )
        }
    }

    LaunchedEffect(receivers) {
        if (receivers.isNotEmpty()) {
            addLog(
                "Pantallas detectadas: ${receivers.size}"
            )
        }
    }

    /*
     * El historial completo del servicio ya viene con hora
     * incluida y sin perder mensajes intermedios (antes solo
     * se veía el último). Lo mostramos más reciente primero,
     * mezclado con los eventos propios de esta pantalla.
     */
    val combinedLog =
        (diagnostic.asReversed() + eventLog).take(20)

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Header(
            title = "Técnico"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 24.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            item {
                TechnicalStatusCard(
                    receiverCount = receivers.size
                )
            }

            item {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {

                    Button(
                        onClick = {

                            onRefresh()

                            lastRefresh =
                                SimpleDateFormat(
                                    "HH:mm:ss",
                                    Locale.getDefault()
                                ).format(Date())

                            addLog(
                                "Búsqueda de pantallas solicitada"
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape =
                            RoundedCornerShape(13.dp),
                        contentPadding =
                            PaddingValues(
                                horizontal = 12.dp,
                                vertical = 10.dp
                            ),
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor = YgBlue
                                )
                    ) {

                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(
                            modifier = Modifier.width(6.dp)
                        )

                        Text(
                            text = "Buscar"
                        )
                    }

                    OutlinedButton(
                        onClick = {

                            addLog(
                                "Servicio: ${
                                    if (
                                        ControllerSyncService
                                            .getInstance() != null
                                    ) {
                                        "ACTIVO"
                                    } else {
                                        "NO DISPONIBLE"
                                    }
                                }"
                            )
                        },
                        modifier = Modifier.weight(0.72f),
                        shape =
                            RoundedCornerShape(13.dp),
                        contentPadding =
                            PaddingValues(
                                horizontal = 10.dp,
                                vertical = 10.dp
                            )
                    ) {

                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )

                        Spacer(
                            modifier = Modifier.width(5.dp)
                        )

                        Text(
                            text = "Estado"
                        )
                    }
                }
            }

            item {

                Text(
                    text = "Pantallas",
                    color = YgText,
                    fontWeight = FontWeight.ExtraBold,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )
            }

            if (receivers.isEmpty()) {

                item {
                    TechnicalEmptyCard()
                }

            } else {

                items(
                    items = receivers,
                    key = {
                        "technical_${it.id}"
                    }
                ) { receiver ->

                    TechnicalReceiverCard(
                        receiver = receiver,
                        onTest = {

                            addLog(
                                "Prueba seleccionada → ${receiver.name}"
                            )
                        }
                    )
                }
            }

            item {

                TechnicalLogCard(
                    logs = combinedLog,
                    lastRefresh = lastRefresh
                )
            }
        }
    }
}

@Composable
private fun TechnicalStatusCard(
    receiverCount: Int
) {
    val serviceActive =
        ControllerSyncService.getInstance() != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = Color.White
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (serviceActive) {
                                Color(0xFFE8F5E9)
                            } else {
                                Color(0xFFFFEBEE)
                            }
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint =
                            if (serviceActive) {
                                Color(0xFF2E7D32)
                            } else {
                                Color(0xFFC62828)
                            },
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.width(11.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "Estado general",
                        color = YgText,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        text =
                            if (serviceActive) {
                                "Servicio de sincronización activo"
                            } else {
                                "Servicio no disponible"
                            },
                        color = YgMuted,
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }

                Text(
                    text =
                        if (serviceActive) {
                            "ACTIVO"
                        } else {
                            "ERROR"
                        },
                    color =
                        if (serviceActive) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFC62828)
                        },
                    fontWeight = FontWeight.Bold,
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium
                )
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                TechnicalMiniStatus(
                    title = "UDP",
                    value = "8766",
                    modifier = Modifier.weight(1f)
                )

                TechnicalMiniStatus(
                    title = "TCP",
                    value = "8765",
                    modifier = Modifier.weight(1f)
                )

                TechnicalMiniStatus(
                    title = "Pantallas",
                    value = receiverCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TechnicalMiniStatus(
    title: String,
    value: String,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        color = YgBackground
    ) {

        Column(
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 8.dp
            ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text = title,
                color = YgMuted,
                style =
                    MaterialTheme
                        .typography
                        .labelSmall
            )

            Spacer(
                modifier = Modifier.height(2.dp)
            )

            Text(
                text = value,
                color = YgText,
                fontWeight = FontWeight.Bold,
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )
        }
    }
}

@Composable
private fun TechnicalReceiverCard(
    receiver: Receiver,
    onTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = Color.White
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 1.dp
            )
    ) {

        Column(
            modifier = Modifier.padding(14.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (receiver.connected) {
                                Color(0xFFE8F5E9)
                            } else {
                                Color(0xFFFFF3E0)
                            }
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Icon(
                        Icons.Default.Tv,
                        contentDescription = null,
                        tint =
                            if (receiver.connected) {
                                Color(0xFF2E7D32)
                            } else {
                                Color(0xFFEF6C00)
                            },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.width(10.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = receiver.name,
                        color = YgText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Text(
                        text =
                            if (receiver.connected) {
                                "Conexión WebSocket disponible"
                            } else {
                                "Sin conexión activa"
                            },
                        color = YgMuted,
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall
                    )
                }

                Text(
                    text =
                        if (receiver.connected) {
                            "ONLINE"
                        } else {
                            "OFFLINE"
                        },
                    color =
                        if (receiver.connected) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFEF6C00)
                        },
                    fontWeight = FontWeight.Bold,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall
                )
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "Dirección",
                        color = YgMuted,
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall
                    )

                    Text(
                        text = receiver.address,
                        color = YgText,
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }

                OutlinedButton(
                    onClick = onTest,
                    enabled = receiver.connected,
                    shape =
                        RoundedCornerShape(10.dp),
                    contentPadding =
                        PaddingValues(
                            horizontal = 13.dp,
                            vertical = 7.dp
                        )
                ) {
                    Text(
                        text = "Probar"
                    )
                }
            }
        }
    }
}

@Composable
private fun TechnicalEmptyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = Color.White
            )
    ) {

        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.Tv,
                contentDescription = null,
                tint = YgMuted,
                modifier = Modifier.size(30.dp)
            )

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            Column {

                Text(
                    text =
                        "No se detectaron pantallas",
                    color = YgText,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text =
                        "Pulsa «Buscar» para actualizar.",
                    color = YgMuted,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )
            }
        }
    }
}

@Composable
private fun TechnicalLogCard(
    logs: List<String>,
    lastRefresh: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = Color.White
            )
    ) {

        Column(
            modifier = Modifier.padding(14.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    tint = YgBlue,
                    modifier = Modifier.size(19.dp)
                )

                Spacer(
                    modifier = Modifier.width(7.dp)
                )

                Text(
                    text = "Registro",
                    color = YgText,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(
                modifier = Modifier.height(9.dp)
            )

            if (lastRefresh != null) {

                Text(
                    text =
                        "Última actualización: $lastRefresh",
                    color = YgMuted,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall
                )

                Spacer(
                    modifier = Modifier.height(7.dp)
                )
            }

            if (logs.isEmpty()) {

                Text(
                    text = "Esperando eventos...",
                    color = YgMuted,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )

            } else {

                logs.take(15).forEach { log ->

                    Text(
                        text = log,
                        color = YgText,
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,
                        modifier =
                            Modifier.padding(
                                vertical = 2.dp
                            )
                    )
                }
            }
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
            colors =
                CardDefaults.cardColors(
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
                    text =
                        "Control sincronizado de reproducción para SmartTube.",
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
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            BottomItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Home,
                label = "Inicio",
                selected = selectedTab == 0,
                onClick = {
                    onTabSelected(0)
                }
            )

            BottomItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Search,
                label = "Buscar",
                selected = selectedTab == 1,
                onClick = {
                    onTabSelected(1)
                }
            )

            BottomItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LibraryMusic,
                label = "Biblioteca",
                selected = selectedTab == 2,
                onClick = {
                    onTabSelected(2)
                }
            )

            BottomItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Tv,
                label = "Pantallas",
                selected = selectedTab == 3,
                onClick = {
                    onTabSelected(3)
                }
            )

            BottomItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Build,
                label = "Técnico",
                selected = selectedTab == 4,
                onClick = {
                    onTabSelected(4)
                }
            )

            BottomItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Settings,
                label = "Ajustes",
                selected = selectedTab == 5,
                onClick = {
                    onTabSelected(5)
                }
            )
        }
    }
}

@Composable
private fun BottomItem(
    modifier: Modifier,
    icon:
        androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(14.dp)
            )
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 2.dp,
                vertical = 5.dp
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Icon(
            imageVector = icon,
            contentDescription = label,
            tint =
                if (selected) {
                    YgBlue
                } else {
                    YgMuted
                },
            modifier = Modifier.size(22.dp)
        )

        Text(
            text = label,
            color =
                if (selected) {
                    YgBlue
                } else {
                    YgMuted
                },
            fontWeight =
                if (selected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
            style =
                MaterialTheme
                    .typography
                    .labelSmall,
            maxLines = 1,
            overflow =
                TextOverflow.Ellipsis
        )
    }
}

private fun sendVideoToReceivers(
    context: Context,
    video: YgYouTubeResult
) {
    val service =
        ControllerSyncService.getInstance()

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
