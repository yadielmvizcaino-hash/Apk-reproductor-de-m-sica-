package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val isFavorite: Boolean = false,
    val uriString: String = "", // Location URI for local device files
    val isCustomSynth: Boolean = false,
    val synthType: String = "CHIP", // "SINE", "SQUARE", "TRIANGLE", "CHIP"
    val tempo: Int = 120, // Beats per minute
    val melodyPattern: String = "C4,E4,G4,C5", // Comma-separated notes for synthezier
    val playCount: Int = 0,
    val dateAdded: Long = System.currentTimeMillis()
)
