package com.example.player

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

class AudioSynthPlayer {
    private var synthJob: Job? = null
    private var currentAudioTrack: AudioTrack? = null
    private var isPlaying = false

    private val noteFrequencies = mapOf(
        "A3" to 220.00, "B3" to 246.94,
        "C4" to 261.63, "D4" to 293.66, "E4" to 329.63, "F4" to 349.23, "G4" to 392.00, "A4" to 440.00, "B4" to 493.88,
        "C5" to 523.25, "D5" to 587.33, "E5" to 659.25, "F5" to 698.46, "G5" to 783.99, "A5" to 880.00, "B5" to 987.77
    )

    fun startPlaying(
        melodyPattern: String,
        synthType: String,
        tempoBpm: Int,
        onProgress: (Int) -> Unit, // reports current playing note index
        onFinished: () -> Unit
    ) {
        stopPlaying()
        isPlaying = true
        val notes = melodyPattern.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (notes.isEmpty()) {
            onFinished()
            return
        }

        val beatDurationMs = (60000 / tempoBpm).toLong()

        synthJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                var currentIndex = 0
                while (isPlaying && currentIndex < notes.size) {
                    val note = notes[currentIndex]
                    val frequency = noteFrequencies[note] ?: 440.0
                    onProgress(currentIndex)

                    playSingleNotePcm(frequency, synthType, beatDurationMs.toInt())

                    currentIndex++
                    // Optional tiny rest between notes
                    delay(15)
                }
            } catch (e: Exception) {
                Log.e("AudioSynthPlayer", "Error synthesising audio: ${e.message}")
            } finally {
                isPlaying = false
                onFinished()
            }
        }
    }

    private fun playSingleNotePcm(frequency: Double, synthType: String, durationMs: Int) {
        val sampleRate = 16000 // 16kHz for balanced quality/perf
        val totalSamples = (durationMs / 1000.0 * sampleRate).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val angle = 2.0 * Math.PI * frequency * t
            
            val amplitude = when (synthType.uppercase()) {
                "SINE" -> sin(angle)
                "SQUARE" -> if (sin(angle) >= 0) 0.6 else -0.6
                "TRIANGLE" -> {
                    val x = (angle / (2.0 * Math.PI)) % 1.0
                    if (x < 0.5) (4.0 * x - 1.0) else (3.0 - 4.0 * x)
                }
                else -> { // "CHIP" / Retro pulse
                    val sine = sin(angle)
                    if (sine > 0.4) 0.5 else if (sine < -0.4) -0.5 else 0.0
                }
            }
            
            // Apply volume fade-out envelope to prevent clicking
            val envelope = if (i > totalSamples - 1600) {
                (totalSamples - i).toDouble() / 1600.0
            } else if (i < 400) {
                i.toDouble() / 400.0
            } else {
                1.0
            }

            buffer[i] = (amplitude * Short.MAX_VALUE * envelope).toInt().toShort()
        }

        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize.coerceAtLeast(totalSamples * 2),
                AudioTrack.MODE_STATIC
            )
            
            synchronized(this) {
                currentAudioTrack = track
            }

            track.write(buffer, 0, totalSamples)
            if (isPlaying) {
                track.play()
                // Sleep main thread of synthesis to let static track play fully
                Thread.sleep(durationMs.toLong())
            }
            track.stop()
            track.release()
        } catch (e: Exception) {
            Log.e("AudioSynthPlayer", "AudioTrack play exception: ${e.message}")
        }
    }

    fun stopPlaying() {
        isPlaying = false
        synthJob?.cancel()
        synthJob = null
        synchronized(this) {
            try {
                currentAudioTrack?.apply {
                    if (state == AudioTrack.STATE_INITIALIZED) {
                        stop()
                        release()
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            currentAudioTrack = null
        }
    }
}
