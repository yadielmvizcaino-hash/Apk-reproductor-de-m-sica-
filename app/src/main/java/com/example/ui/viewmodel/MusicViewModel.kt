package com.example.ui.viewmodel

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SongEntity
import com.example.data.SongRepository
import com.example.player.OfflinePlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = SongRepository(database.songDao())
    private val playerManager = OfflinePlayerManager(application)

    // Reactive database queries
    val allSongs: StateFlow<List<SongEntity>> = repository.allSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<SongEntity>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Player state hooks
    val currentSong = playerManager.currentSong
    val isPlaying = playerManager.isPlaying
    val progress = playerManager.currentProgress
    val currentSecond = playerManager.currentSecond
    val activeNoteIndex = playerManager.activeNoteIndex

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage = _scanMessage.asStateFlow()

    // Screen navigation
    private val _currentTab = MutableStateFlow("explore") // explore, favorites, synth_creator, now_playing
    val currentTab = _currentTab.asStateFlow()

    fun navigateToTab(tabName: String) {
        _currentTab.value = tabName
    }

    fun playSong(song: SongEntity) {
        playerManager.playSong(song)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun stopSong() {
        playerManager.stopPlaying()
    }

    fun seekTo(fraction: Float) {
        playerManager.seekToFraction(fraction)
    }

    fun nextSong() {
        val songsList = allSongs.value
        val curSong = currentSong.value
        if (songsList.isNotEmpty()) {
            val index = if (curSong == null) 0 else songsList.indexOfFirst { it.id == curSong.id }
            val nextIndex = (index + 1) % songsList.size
            playSong(songsList[nextIndex])
        }
    }

    fun previousSong() {
        val songsList = allSongs.value
        val curSong = currentSong.value
        if (songsList.isNotEmpty()) {
            val index = if (curSong == null) 0 else songsList.indexOfFirst { it.id == curSong.id }
            val prevIndex = if (index - 1 < 0) songsList.size - 1 else index - 1
            playSong(songsList[prevIndex])
        }
    }

    fun toggleFavorite(song: SongEntity) {
        viewModelScope.launch {
            val updated = song.copy(isFavorite = !song.isFavorite)
            repository.updateSong(updated)
            // also update current song state if it is currently playing
            if (currentSong.value?.id == song.id) {
                // simple state reference refresh or player updates
            }
        }
    }

    fun deleteSong(song: SongEntity) {
        viewModelScope.launch {
            if (currentSong.value?.id == song.id) {
                playerManager.stopPlaying()
            }
            repository.deleteSong(song)
        }
    }

    fun saveCustomSynthSong(title: String, artist: String, synthType: String, tempo: Int, pattern: String, durationSec: Int) {
        viewModelScope.launch {
            val song = SongEntity(
                title = title.ifBlank { "Unfinished Symphony" },
                artist = artist.ifBlank { "Personal Synth Rec" },
                durationSec = durationSec,
                isFavorite = false,
                isCustomSynth = true,
                synthType = synthType,
                tempo = tempo,
                melodyPattern = pattern
            )
            repository.insertSong(song)
            _scanMessage.value = "Canción guardada correctamente!"
        }
    }

    // High fidelity MediaStore scanner for native offline MP3/Audio files
    fun scanDeviceAudioFiles() {
        viewModelScope.launch {
            _isScanning.value = true
            _scanMessage.value = "Escaneando archivos..."
            var count = 0
            
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val resolver = context.contentResolver
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION
                )
                // Select only actual audio content (no calls, voice notes, etc.)
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                
                try {
                    val cursor = resolver.query(uri, projection, selection, null, null)
                    cursor?.use { c ->
                        val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                        val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                        
                        while (c.moveToNext()) {
                            val id = c.getLong(idColumn)
                            val title = c.getString(titleColumn) ?: "Canción de dispositivo"
                            val artist = c.getString(artistColumn) ?: "Artista desconocido"
                            val durationMs = c.getInt(durationColumn)
                            val durationSec = if (durationMs > 0) durationMs / 1000 else 180
                            val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                            
                            val song = SongEntity(
                                title = title,
                                artist = artist,
                                durationSec = durationSec,
                                isFavorite = false,
                                uriString = contentUri.toString(),
                                isCustomSynth = false
                            )
                            repository.insertSong(song)
                            count++
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Failed to query MediaStore: ${e.message}")
                }
            }
            
            _isScanning.value = false
            _scanMessage.value = if (count > 0) "¡Se encontraron $count canciones locales!" else "No se encontraron canciones locales en almacenamiento. ¡Disfruta de nuestros sintetizadores pre-cargados!"
        }
    }

    fun clearScanMessage() {
        _scanMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.stopPlaying()
    }
}
