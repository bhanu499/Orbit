package com.orbit.assistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
