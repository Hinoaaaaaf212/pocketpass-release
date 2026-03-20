package com.pocketpass.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Encounter::class, CachedMessage::class, SpotPassItemEntity::class], version = 15, exportSchema = false)
abstract class PocketPassDatabase : RoomDatabase() {

    abstract fun encounterDao(): EncounterDao
    abstract fun messageDao(): MessageDao
    abstract fun spotPassDao(): SpotPassDao

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
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}