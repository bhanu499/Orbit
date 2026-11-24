package com.orbit.assistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, TokenUsageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun tokenUsageDao(): TokenUsageDao
}
