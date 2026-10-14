package com.example.trackly.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.trackly.data.local.dao.CompletionDao
import com.example.trackly.data.local.dao.HabitDao
import com.example.trackly.data.local.entity.HabitCompletionEntity
import com.example.trackly.data.local.entity.HabitEntity
import com.example.trackly.data.local.entity.HabitPauseEntity
import com.example.trackly.data.local.entity.HabitReminderEntity
import com.example.trackly.data.local.entity.HabitScheduleEntity

@Database(
    entities = [
        HabitEntity::class,
        HabitScheduleEntity::class,
        HabitReminderEntity::class,
        HabitPauseEntity::class,
        HabitCompletionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TracklyDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun completionDao(): CompletionDao

    companion object {
        const val NAME = "trackly.db"

        // Never use fallbackToDestructiveMigration(): users' habit history must survive upgrades.
        // When the schema changes, bump `version` and add a Migration here (schemas/ holds the JSON).
        fun create(context: Context): TracklyDatabase =
            Room.databaseBuilder(context.applicationContext, TracklyDatabase::class.java, NAME).build()

        fun inMemory(context: Context): TracklyDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, TracklyDatabase::class.java).build()
    }
}
