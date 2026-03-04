package com.pocketpass.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Encounter::class], version = 3, exportSchema = false)
abstract class PocketPassDatabase : RoomDatabase() {

    abstract fun encounterDao(): EncounterDao

    companion object {
        @Volatile
        private var INSTANCE: PocketPassDatabase? = null

        fun getDatabase(context: Context): PocketPassDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PocketPassDatabase::class.java,
                    "pocketpass_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}