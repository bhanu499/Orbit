package com.orbit.assistant.di

import android.content.Context
import androidx.room.Room
import com.orbit.assistant.data.local.OrbitDatabase
import com.orbit.assistant.data.local.TaskDao
import com.orbit.assistant.data.local.TokenUsageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    @Provides
    @Singleton
    fun provideOrbitDatabase(@ApplicationContext context: Context): OrbitDatabase {
        return Room.databaseBuilder(
            context,
            OrbitDatabase::class.java,
            "orbit_database.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideTaskDao(database: OrbitDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideTokenUsageDao(database: OrbitDatabase): TokenUsageDao = database.tokenUsageDao()
}
