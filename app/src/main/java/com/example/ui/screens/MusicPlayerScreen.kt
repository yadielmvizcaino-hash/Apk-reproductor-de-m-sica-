package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.SongEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MusicPlayerScreen(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    
    // Player hook-ups
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val currentSecond by viewModel.currentSecond.collectAsState()
    val activeNoteIndex by viewModel.activeNoteIndex.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanMessage by viewModel.scanMessage.collectAsState()

    // Permission launcher for scanning
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.scanDeviceAudioFiles()
            } else {
                Toast.makeText(context, "Permiso denegado. No se pudieron escanear archivos locales.", Toast.LENGTH_SHORT).show()
                // Scan trigger fallback (it will scan empty which triggers elegant fallback message)
                viewModel.scanDeviceAudioFiles()
            }
        }
    )

    // Trigger toast alerts upon db success savers
    LaunchedEffect(scanMessage) {
        scanMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearScanMessage()
        }
    }

    Scaffold(
        containerColor = BlackAmoled,
        bottomBar = {
            MusicBottomBar(
                currentTab = currentTab,
                onSelected = { viewModel.navigateToTab(it) },
                hasActivePlayer = currentSong != null
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BlackAmoled)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) with fadeOut(animationSpec = tween(220))
                },
                label = "MainTabs"
            ) { tab ->
                when (tab) {
                    "explore" -> ExploreSongsView(
                        songs = allSongs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        isScanning = isScanning,
                        onScanTrigger = {
                            val hasPermission = ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                viewModel.scanDeviceAudioFiles()
                            } else {
                                launcher.launch(permissionToRequest)
                            }
                        },
                        onPlaySong = { viewModel.playSong(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onDeleteSong = { viewModel.deleteSong(it) }
                    )
                    "favorites" -> FavoritesView(
                        songs = favoriteSongs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onPlaySong = { viewModel.playSong(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) }
                    )
                    "synth_creator" -> SynthComposerView(
                        onSave = { title, artist, type, tempo, pattern, duration ->
                            viewModel.saveCustomSynthSong(title, artist, type, tempo, pattern, duration)
                        }
                    )
                    "now_playing" -> PlayerMainDeck(
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        progress = progress,
                        currentSecond = currentSecond,
                        activeNoteIndex = activeNoteIndex,
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onNext = { viewModel.nextSong() },
                        onPrev = { viewModel.previousSong() },
                        onSeek = { viewModel.seekTo(it) },
                        onFavoriteToggle = { currentSong?.let { viewModel.toggleFavorite(it) } }
                    )
                }
            }

            // Quick floating mini-player controller at the bottom if we're on other tabs
            if (currentSong != null && currentTab != "now_playing") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                ) {
                    MiniPlayerBar(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        progress = progress,
                        onBarClick = { viewModel.navigateToTab("now_playing") },
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onNext = { viewModel.nextSong() }
                    )
                }
            }
        }
    }
}

@Composable
fun ExploreSongsView(
    songs: List<SongEntity>,
    currentSong: SongEntity?,
    isPlaying: Boolean,
    isScanning: Boolean,
    onScanTrigger: () -> Unit,
    onPlaySong: (SongEntity) -> Unit,
    onToggleFavorite: (SongEntity) -> Unit,
    onDeleteSong: (SongEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        // Elegant Title Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Biblioteca",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        color = Color.White
                    )
                )
                Text(
                    text = "Offline & AMOLED",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AccentGold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                )
            }

            IconButton(
                onClick = onScanTrigger,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCardBg)
                    .border(1.dp, BorderGlow, RoundedCornerShape(12.dp))
                    .size(48.dp)
                    .testTag("scan_button")
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AccentGold,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Escanear dispositivo",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, BorderGlow, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Empty",
                        tint = BorderGlow,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "La lista está vacía",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Haz clic en el botón de arriba para escanear música del teléfono o dirígete a la pestaña de 'Sintetizador' para crear tus propias pistas analógicas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Text(
                text = "${songs.size} pistas offline cargadas",
                style = MaterialTheme.typography.labelSmall,
                color = AccentSilver,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(songs) { song ->
                    val isCurrent = song.id == currentSong?.id
                    SongRowItem(
                        song = song,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onRowClick = { onPlaySong(song) },
                        onFavClick = { onToggleFavorite(song) },
                        onDeleteClick = { onDeleteSong(song) }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoritesView(
    songs: List<SongEntity>,
    currentSong: SongEntity?,
    isPlaying: Boolean,
    onPlaySong: (SongEntity) -> Unit,
    onToggleFavorite: (SongEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text(
            text = "Favoritos",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
        )
        Text(
            text = "Tus canciones preferidas",
            style = MaterialTheme.typography.bodyMedium,
            color = RedFavorite,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, BorderGlow, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "Sin favoritos",
                        tint = BorderGlow,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nada por aquí",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Marca el corazón rojo en la lista de canciones para que aparezcan en esta biblioteca rápida.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(songs) { song ->
                    val isCurrent = song.id == currentSong?.id
                    SongRowItem(
                        song = song,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onRowClick = { onPlaySong(song) },
                        onFavClick = { onToggleFavorite(song) },
                        onDeleteClick = null // No direct remove option from favorites catalog
                    )
                }
            }
        }
    }
}

@Composable
fun SongRowItem(
    song: SongEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onRowClick: () -> Unit,
    onFavClick: () -> Unit,
    onDeleteClick: (() -> Unit)?
) {
    val borderColor = if (isCurrent) AccentGold.copy(alpha = 0.5f) else BorderGlow
    val bgSelection = if (isCurrent) DarkCardBg else BlackAmoled

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgSelection)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onRowClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon / Wave Representation
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkCardSurface),
            contentAlignment = Alignment.Center
        ) {
            if (isPlaying) {
                LiveWaveformBars(modifier = Modifier.size(24.dp), barsCount = 3, barColor = AccentGold)
            } else {
                Icon(
                    imageVector = if (song.isCustomSynth) Icons.Default.Build else Icons.Default.PlayArrow,
                    contentDescription = "Pista",
                    tint = if (isCurrent) AccentGold else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) AccentGold else Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.isCustomSynth) {
                    Box(
                        modifier = Modifier
                            .background(AccentCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "SYNTH ${song.synthType}",
                            style = TextStyle(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Time length helper
        Text(
            text = formatDuration(song.durationSec),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color.Gray
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Favorite Heart click
        IconButton(
            onClick = onFavClick,
            modifier = Modifier.testTag("row_fav_${song.id}")
        ) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (song.isFavorite) RedFavorite else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }

        // Optional Delete Action
        if (onDeleteClick != null) {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.testTag("row_del_${song.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Borrar",
                    tint = Color.DarkGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SynthComposerView(
    onSave: (String, String, String, Int, String, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("CHIP") }
    var tempo by remember { mutableStateOf(120f) }
    
    // Grid notes sequencer representation
    val notesAvailable = listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5", "D5", "E5")
    val selectedNotesSequence = remember { mutableStateListOf("C4", "E4", "G4", "B4") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text(
            text = "Sintetizador",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )
        )
        Text(
            text = "Diseña tus propias canciones",
            style = MaterialTheme.typography.bodyMedium,
            color = AccentCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Detalles de la pista",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Título de la melodía", color = Color.Gray) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkCardBg,
                        unfocusedContainerColor = DarkCardBg,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = AccentGold,
                        unfocusedIndicatorColor = BorderGlow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGlow, RoundedCornerShape(8.dp))
                        .testTag("input_title"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextField(
                    value = artist,
                    onValueChange = { artist = it },
                    placeholder = { Text("Tu nombre de compositor", color = Color.Gray) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkCardBg,
                        unfocusedContainerColor = DarkCardBg,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = AccentGold,
                        unfocusedIndicatorColor = BorderGlow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGlow, RoundedCornerShape(8.dp))
                        .testTag("input_composer"),
                    singleLine = true
                )
            }

            item {
                Text(
                    text = "Forma de la onda",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))

                val shapes = listOf("SINE" to "Sine", "SQUARE" to "Squr", "TRIANGLE" to "Tri", "CHIP" to "Chip")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shapes.forEach { (rawType, label) ->
                        val selected = selectedType == rawType
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AccentCyan else DarkCardBg)
                                .border(1.dp, if (selected) AccentCyan else BorderGlow, RoundedCornerShape(8.dp))
                                .clickable { selectedType = rawType }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (selected) Color.Black else Color.White
                                )
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tempo (BPM)",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "${tempo.toInt()} BPM",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = tempo,
                    onValueChange = { tempo = it },
                    valueRange = 80f..180f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = BorderGlow,
                        thumbColor = AccentCyan
                    )
                )
            }

            item {
                Text(
                    text = "Patrón de Notas",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Toca para agregar notas. Mantén presionado para borrar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable note pads selection
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(notesAvailable) { noteName ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(DarkCardSurface)
                                .border(1.dp, BorderGlow, CircleShape)
                                .clickable {
                                    if (selectedNotesSequence.size < 16) {
                                        selectedNotesSequence.add(noteName)
                                    }
                                }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = noteName,
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Display selected patterns sequences
                Text(
                    text = "Melodia actual (${selectedNotesSequence.size} notas):",
                    style = MaterialTheme.typography.bodyMedium.copy(color = AccentGold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRowLayout(
                    spacing = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGlow, RoundedCornerShape(12.dp))
                        .background(DarkCardBg)
                        .padding(12.dp)
                ) {
                    if (selectedNotesSequence.isEmpty()) {
                        Text(
                            text = "Sin notas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray
                        )
                    } else {
                        selectedNotesSequence.forEachIndexed { idx, melodyNote ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkCardSurface)
                                    .clickable {
                                        selectedNotesSequence.removeAt(idx)
                                    }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${idx + 1}:$melodyNote",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Eliminar",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val seqString = selectedNotesSequence.joinToString(",")
                        val calculatedDurationSec = (selectedNotesSequence.size * (60.0 / tempo.toInt())).toInt().coerceAtLeast(1)
                        
                        onSave(
                            title.ifBlank { "Sinfonia Espacial" },
                            artist.ifBlank { "Compositor DIY" },
                            selectedType,
                            tempo.toInt(),
                            seqString,
                            calculatedDurationSec
                        )

                        // Clear pattern form for another nice creation
                        title = ""
                        artist = ""
                        selectedNotesSequence.clear()
                        selectedNotesSequence.addAll(listOf("C4", "E4", "G4", "B4"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_synth_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = selectedNotesSequence.isNotEmpty()
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Guardar")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Guardar en biblioteca offline",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerMainDeck(
    currentSong: SongEntity?,
    isPlaying: Boolean,
    progress: Float,
    currentSecond: Int,
    activeNoteIndex: Int,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Float) -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App title/status
        Text(
            text = "REPRODUCIENDO",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 3.sp,
                color = AccentGold,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        )

        if (currentSong == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Selecciona una canción de la lista para empezar a reproducir.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Elegant Album Art/Visualiser Card in the center
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Spinning vinyl visual or Pulsing ring
                val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isPlaying) 1.08f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "PulseScale"
                )

                // Elegant dynamic Canvas: drawing custom waveforms depending on frequencies
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(DarkCardBg)
                        .border(1.dp, BorderGlow, CircleShape)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        val strokeWidthValue = 2.dp.toPx()
                        
                        // Circle pulsing line
                        drawCircle(
                            color = AccentGold.copy(alpha = 0.15f),
                            radius = (size.minDimension / 2) * pulseScale,
                            style = Stroke(width = strokeWidthValue)
                        )

                        // Static aesthetic record grooves
                        drawCircle(
                            color = BorderGlow.copy(alpha = 0.4f),
                            radius = size.minDimension / 3,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Dynamic wave bars synced to active music note
                    if (isPlaying) {
                        LiveWaveformBars(
                            modifier = Modifier.size(100.dp),
                            barsCount = 8,
                            barColor = if (currentSong.isCustomSynth) AccentCyan else AccentGold
                        )
                    } else {
                        if (currentSong.isCustomSynth) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Logotipo de reproducción",
                                tint = BorderGlow,
                                modifier = Modifier.size(64.dp)
                            )
                        } else {
                            CustomMusicNoteIcon(
                                tint = BorderGlow,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
            }

            // Track details with a heart favoriting button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.testTag("deck_fav")
                    ) {
                        Icon(
                            imageVector = if (currentSong.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (currentSong.isFavorite) RedFavorite else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentSong.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentSong.artist,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = AccentGold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Just to balance the layout asymmetry nicely
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // If playing synthesizer, display notes sequence track visualizer
                if (currentSong.isCustomSynth) {
                    val notes = currentSong.melodyPattern.split(",").map { it.trim() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .border(1.dp, BorderGlow, RoundedCornerShape(10.dp))
                            .background(DarkCardBg)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        notes.forEachIndexed { index, synthNote ->
                            val isActive = activeNoteIndex == index
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isActive) AccentCyan else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = synthNote,
                                    style = TextStyle(
                                        color = if (isActive) Color.Black else Color.Gray,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }
                }

                // Wave progress slider seeker
                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = { onSeek(it) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = BorderGlow
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentSecond),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    )
                    Text(
                        text = formatDuration(currentSong.durationSec),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Classic Media Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrev,
                    modifier = Modifier
                        .size(64.dp)
                        .testTag("prev_btn")
                ) {
                    CustomSkipPreviousIcon(
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Play / Pause Circle Target
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .testTag("play_pause_btn")
                ) {
                    if (isPlaying) {
                        CustomPauseIcon(
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Reproducir / Pausa",
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(64.dp)
                        .testTag("next_btn")
                ) {
                    CustomSkipNextIcon(
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    song: SongEntity,
    isPlaying: Boolean,
    progress: Float,
    onBarClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCardBg)
            .border(1.dp, BorderGlow, RoundedCornerShape(14.dp))
            .clickable(onClick = onBarClick)
    ) {
        // Linear progress tracker at the bottom of the minibox container
        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            color = AccentGold.copy(alpha = 0.6f),
            trackColor = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (song.isCustomSynth) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Audio",
                    tint = AccentGold,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                CustomMusicNoteIcon(
                    tint = AccentGold,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = TextStyle(fontSize = 11.sp, color = Color.Gray),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.testTag("mini_play_pause")
            ) {
                if (isPlaying) {
                    CustomPauseIcon(
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Reproducir / Pausa",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.testTag("mini_next")
            ) {
                CustomSkipNextIcon(
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun MusicBottomBar(
    currentTab: String,
    onSelected: (String) -> Unit,
    hasActivePlayer: Boolean
) {
    NavigationBar(
        containerColor = BlackAmoled,
        modifier = Modifier
            .border(1.dp, BorderGlow.copy(alpha = 0.5f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        NavigationBarItem(
            selected = currentTab == "explore",
            onClick = { onSelected("explore") },
            icon = {
                Icon(
                    imageVector = if (currentTab == "explore") Icons.Filled.List else Icons.Outlined.List,
                    contentDescription = "Pistas"
                )
            },
            label = { Text("Pistas", style = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentGold,
                selectedTextColor = AccentGold,
                indicatorColor = AccentGold.copy(alpha = 0.12f),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            ),
            modifier = Modifier.testTag("tab_explore")
        )

        NavigationBarItem(
            selected = currentTab == "favorites",
            onClick = { onSelected("favorites") },
            icon = {
                Icon(
                    imageVector = if (currentTab == "favorites") Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favoritos"
                )
            },
            label = { Text("Favoritos", style = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RedFavorite,
                selectedTextColor = RedFavorite,
                indicatorColor = RedFavorite.copy(alpha = 0.12f),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            ),
            modifier = Modifier.testTag("tab_favorites")
        )

        NavigationBarItem(
            selected = currentTab == "synth_creator",
            onClick = { onSelected("synth_creator") },
            icon = {
                Icon(
                    imageVector = if (currentTab == "synth_creator") Icons.Filled.Add else Icons.Outlined.Add,
                    contentDescription = "Sintetizador"
                )
            },
            label = { Text("Sinte", style = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace)) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentCyan,
                selectedTextColor = AccentCyan,
                indicatorColor = AccentCyan.copy(alpha = 0.12f),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            ),
            modifier = Modifier.testTag("tab_synth")
        )

        if (hasActivePlayer) {
            NavigationBarItem(
                selected = currentTab == "now_playing",
                onClick = { onSelected("now_playing") },
                icon = {
                    Icon(
                        imageVector = if (currentTab == "now_playing") Icons.Filled.PlayArrow else Icons.Outlined.PlayArrow,
                        contentDescription = "Reproductor"
                    )
                },
                label = { Text("Sonar", style = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color.White.copy(alpha = 0.12f),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                ),
                modifier = Modifier.testTag("tab_playing")
            )
        }
    }
}

@Composable
fun LiveWaveformBars(
    modifier: Modifier = Modifier,
    barsCount: Int = 4,
    barColor: Color = AccentGold
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barsCount) {
            val infiniteTransition = rememberInfiniteTransition(label = "Bar_$i")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.15f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 350 + (i * 120),
                        delayMillis = i * 40,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "BarScale_$i"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(scale)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun FlowRowLayout(
    spacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val layoutWidth = constraints.maxWidth
        
        var rowX = 0
        var rowY = 0
        var maxRowHeight = 0
        val positions = mutableListOf<Offset>()
        
        placeables.forEach { placeable ->
            val spacingPx = spacing.roundToPx()
            if (rowX + placeable.width > layoutWidth && rowX > 0) {
                rowX = 0
                rowY += maxRowHeight + spacingPx
                maxRowHeight = 0
            }
            positions.add(Offset(rowX.toFloat(), rowY.toFloat()))
            rowX += placeable.width + spacingPx
            maxRowHeight = maxRowHeight.coerceAtLeast(placeable.height)
        }
        
        val finalHeight = if (placeables.isEmpty()) 0 else rowY + maxRowHeight
        
        layout(layoutWidth, finalHeight) {
            placeables.forEachIndexed { idx, placeable ->
                val pos = positions[idx]
                placeable.placeRelative(pos.x.toInt(), pos.y.toInt())
            }
        }
    }
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

@Composable
fun CustomPauseIcon(tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxHeight(0.55f)
        ) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(tint, RoundedCornerShape(1.dp)))
            Box(Modifier.width(5.dp).fillMaxHeight().background(tint, RoundedCornerShape(1.dp)))
        }
    }
}

@Composable
fun CustomSkipPreviousIcon(tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.size(24.dp)
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight(0.6f).background(tint, RoundedCornerShape(1.dp)))
            Canvas(modifier = Modifier.size(16.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(0f, size.height / 2)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path, color = tint)
            }
        }
    }
}

@Composable
fun CustomSkipNextIcon(tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.size(24.dp)
        ) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height / 2)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, color = tint)
            }
            Box(Modifier.width(4.dp).fillMaxHeight(0.6f).background(tint, RoundedCornerShape(1.dp)))
        }
    }
}

@Composable
fun CustomMusicNoteIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        drawCircle(color = tint, radius = w * 0.14f, center = Offset(w * 0.32f, h * 0.72f))
        drawCircle(color = tint, radius = w * 0.14f, center = Offset(w * 0.76f, h * 0.62f))
        
        drawLine(color = tint, start = Offset(w * 0.44f, h * 0.72f), end = Offset(w * 0.44f, h * 0.22f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.88f, h * 0.62f), end = Offset(w * 0.88f, h * 0.12f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
        
        drawLine(color = tint, start = Offset(w * 0.44f, h * 0.22f), end = Offset(w * 0.88f, h * 0.12f), strokeWidth = w * 0.12f, cap = StrokeCap.Round)
    }
}

