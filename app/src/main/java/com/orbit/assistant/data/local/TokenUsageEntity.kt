package com.orbit.assistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "token_usage")
data class TokenUsageEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val provider: String,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val cachedTokens: Int,
    val latencyMs: Long,
    val estimatedCostUsd: Double,
    val reason: String,
    val timestamp: Long
)
