package com.example.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.data.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OfflinePlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val synthPlayer = AudioSynthPlayer()

    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentSecond = MutableStateFlow(0)
    val currentSecond = _currentSecond.asStateFlow()

    private val _currentProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val currentProgress = _currentProgress.asStateFlow()

    private val _activeNoteIndex = MutableStateFlow(-1) // active index for synth notes visual highlights
    val activeNoteIndex = _activeNoteIndex.asStateFlow()

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun playSong(song: SongEntity) {
        stopPlaying()
        _currentSong.value = song
        _isPlaying.value = true
        _currentSecond.value = 0
        _currentProgress.value = 0f
        _activeNoteIndex.value = -1

        if (song.isCustomSynth) {
            // Synthesiser Playback
            val notes = song.melodyPattern.split(",").filter { it.isNotBlank() }
            val beatDurationMs = 60000 / song.tempo
            
            synthPlayer.startPlaying(
                melodyPattern = song.melodyPattern,
                synthType = song.synthType,
                tempoBpm = song.tempo,
                onProgress = { index ->
                    _activeNoteIndex.value = index
                    _currentSecond.value = ((index * beatDurationMs) / 1000).toInt()
                    _currentProgress.value = if (notes.isNotEmpty()) index.toFloat() / notes.size else 0f
                },
                onFinished = {
                    scope.launch {
                        _isPlaying.value = false
                        _activeNoteIndex.value = -1
                        _currentProgress.value = 1f
                        _currentSecond.value = song.durationSec
                    }
                }
            )
        } else {
            // Real physical Media Player Playback
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, Uri.parse(song.uriString))
                    prepare()
                    start()
                    setOnCompletionListener {
                        stopPlaying()
                    }
                }
                startProgressTracker()
            } catch (e: Exception) {
                Log.e("OfflinePlayerManager", "Failed to play standard audio file: ${e.message}")
                _isPlaying.value = false
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Default) {
            while (true) {
                val player = mediaPlayer
                val song = _currentSong.value
                if (player != null && player.isPlaying && song != null) {
                    val currentPos = player.currentPosition
                    val duration = player.duration
                    
                    launch(Dispatchers.Main) {
                        _currentSecond.value = currentPos / 1000
                        if (duration > 0) {
                            _currentProgress.value = currentPos.toFloat() / duration
                        }
                    }
                }
                delay(250)
            }
        }
    }

    fun pauseSong() {
        val song = _currentSong.value ?: return
        if (song.isCustomSynth) {
            // Synths cannot be paused because they are direct PCM streams, we just stop them
            synthPlayer.stopPlaying()
            _isPlaying.value = false
        } else {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    _isPlaying.value = false
                }
            }
        }
    }

    fun resumeSong() {
        val song = _currentSong.value ?: return
        _isPlaying.value = true
        if (song.isCustomSynth) {
            playSong(song) // Synthesizer players restart on resume
        } else {
            mediaPlayer?.let { player ->
                player.start()
                startProgressTracker()
            }
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pauseSong()
        } else {
            val song = _currentSong.value
            if (song != null) {
                resumeSong()
            }
        }
    }

    fun stopPlaying() {
        progressJob?.cancel()
        progressJob = null
        
        synthPlayer.stopPlaying()
        
        mediaPlayer?.apply {
            try {
                if (isPlaying) {
                    stop()
                }
                release()
            } catch (e: Exception) {
                // ignore
            }
        }
        mediaPlayer = null

        _isPlaying.value = false
        _activeNoteIndex.value = -1
    }

    fun seekToFraction(fraction: Float) {
        val song = _currentSong.value ?: return
        if (song.isCustomSynth) {
            // Linear seek does not make sense for simple sequencers, but we can do it roughly
            val notes = song.melodyPattern.split(",").filter { it.isNotBlank() }
            if (notes.isNotEmpty()) {
                val index = (fraction * notes.size).toInt().coerceIn(0, notes.size - 1)
                val remainingNotes = notes.subList(index, notes.size).joinToString(",")
                
                _isPlaying.value = true
                synthPlayer.startPlaying(
                    melodyPattern = remainingNotes,
                    synthType = song.synthType,
                    tempoBpm = song.tempo,
                    onProgress = { offsetIndex ->
                        val realIndex = index + offsetIndex
                        _activeNoteIndex.value = realIndex
                        val beatDurationMs = 60000 / song.tempo
                        _currentSecond.value = ((realIndex * beatDurationMs) / 1000).toInt()
                        _currentProgress.value = realIndex.toFloat() / notes.size
                    },
                    onFinished = {
                        scope.launch {
                            _isPlaying.value = false
                            _activeNoteIndex.value = -1
                            _currentProgress.value = 1f
                            _currentSecond.value = song.durationSec
                        }
                    }
                )
            }
        } else {
            mediaPlayer?.let { player ->
                val duration = player.duration
                if (duration > 0) {
                    val targetMs = (fraction * duration).toInt()
                    player.seekTo(targetMs)
                    _currentSecond.value = targetMs / 1000
                    _currentProgress.value = fraction
                }
            }
        }
    }
}
