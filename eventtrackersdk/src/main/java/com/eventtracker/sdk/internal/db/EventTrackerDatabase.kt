package com.eventtracker.sdk.internal.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [EventEntity::class], version = 1, exportSchema = false)
internal abstract class EventTrackerDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {
        private const val DB_NAME = "event_tracker.db"

        @Volatile
        private var instance: EventTrackerDatabase? = null

        fun getInstance(appContext: Context): EventTrackerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(appContext, EventTrackerDatabase::class.java, DB_NAME)
                    .build()
                    .also { instance = it }
            }
    }
}
