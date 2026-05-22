package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [SongEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_player_db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Precompile default awesome space synth soundscapes for the user to enjoy instantly offline
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = database.songDao()
                        dao.insertSong(
                            SongEntity(
                                title = "Nebula Sparkles",
                                artist = "Cosmic Synthesizer",
                                durationSec = 15,
                                isFavorite = true,
                                isCustomSynth = true,
                                synthType = "CHIP",
                                tempo = 140,
                                melodyPattern = "C4,E4,G4,B4,C5,B4,G4,E4"
                            )
                        )
                        dao.insertSong(
                            SongEntity(
                                title = "Deep Aurora",
                                artist = "Deepwave LFO",
                                durationSec = 22,
                                isFavorite = false,
                                isCustomSynth = true,
                                synthType = "SINE",
                                tempo = 90,
                                melodyPattern = "A3,C4,E4,G4,A4,G4,E4,C4"
                            )
                        )
                        dao.insertSong(
                            SongEntity(
                                title = "Chiptune Odyssey",
                                artist = "Pixel Pulse",
                                durationSec = 12,
                                isFavorite = false,
                                isCustomSynth = true,
                                synthType = "SQUARE",
                                tempo = 160,
                                melodyPattern = "E4,G4,E5,D5,C5,D5,E5,B4"
                            )
                        )
                        dao.insertSong(
                            SongEntity(
                                title = "Ethereal Echoes",
                                artist = "Triangle Dreams",
                                durationSec = 20,
                                isFavorite = true,
                                isCustomSynth = true,
                                synthType = "TRIANGLE",
                                tempo = 110,
                                melodyPattern = "F4,A4,C5,E5,F5,E5,C5,A4"
                            )
                        )
                    }
                }
            }
        }
    }
}
