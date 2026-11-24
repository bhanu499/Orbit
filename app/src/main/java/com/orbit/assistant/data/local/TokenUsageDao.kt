package com.orbit.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TokenUsageEntity)

    @Query("SELECT * FROM token_usage ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TokenUsageEntity>>

    @Query("SELECT * FROM token_usage WHERE taskId = :taskId ORDER BY timestamp ASC")
    fun observeForTask(taskId: String): Flow<List<TokenUsageEntity>>

    @Query("DELETE FROM token_usage")
    suspend fun clearAll()
}
