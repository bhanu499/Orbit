package com.orbit.assistant.data.repository

import com.orbit.assistant.agent.llm.LLMUsage
import com.orbit.assistant.agent.llm.ProviderId
import com.orbit.assistant.data.local.TokenUsageDao
import com.orbit.assistant.data.local.TokenUsageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ProviderBreakdown(
    val provider: String,
    val requestCount: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val cachedTokens: Long,
    val estimatedCostUsd: Double
)

data class TaskCostSummary(
    val taskId: String,
    val requestCount: Int,
    val totalTokens: Long,
    val estimatedCostUsd: Double
)

data class TokenUsageSummary(
    val totalInputTokens: Long = 0L,
    val totalOutputTokens: Long = 0L,
    val totalCachedTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val totalEstimatedCostUsd: Double = 0.0,
    val totalRequests: Int = 0,
    val distinctTasks: Int = 0,
    val averageCostPerTaskUsd: Double = 0.0,
    val averageTokensPerAction: Long = 0L,
    val providerBreakdown: List<ProviderBreakdown> = emptyList(),
    val taskBreakdown: List<TaskCostSummary> = emptyList(),
    val recentEntries: List<TokenUsageEntity> = emptyList()
)

@Singleton
class TokenUsageRepository @Inject constructor(
    private val tokenUsageDao: TokenUsageDao
) {
    suspend fun record(
        taskId: String,
        provider: ProviderId,
        model: String,
        usage: LLMUsage,
        reason: String
    ) {
        tokenUsageDao.insert(
            TokenUsageEntity(
                id = "tok_${UUID.randomUUID().toString().take(12)}",
                taskId = taskId,
                provider = provider.name,
                model = model,
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                cachedTokens = usage.cachedTokens,
                latencyMs = usage.latencyMs,
                estimatedCostUsd = usage.estimatedCostUsd,
                reason = reason,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun observeSummary(): Flow<TokenUsageSummary> = tokenUsageDao.observeAll().map { entries ->
        if (entries.isEmpty()) return@map TokenUsageSummary()

        val totalIn = entries.sumOf { it.inputTokens.toLong() }
        val totalOut = entries.sumOf { it.outputTokens.toLong() }
        val totalCached = entries.sumOf { it.cachedTokens.toLong() }
        val totalTok = totalIn + totalOut
        val totalCost = entries.sumOf { it.estimatedCostUsd }

        val taskGroups = entries.filter { it.taskId.isNotBlank() }.groupBy { it.taskId }
        val taskCount = taskGroups.size

        val providerBreakdown = entries.groupBy { it.provider }.map { (prov, list) ->
            ProviderBreakdown(
                provider = prov,
                requestCount = list.size,
                inputTokens = list.sumOf { it.inputTokens.toLong() },
                outputTokens = list.sumOf { it.outputTokens.toLong() },
                cachedTokens = list.sumOf { it.cachedTokens.toLong() },
                estimatedCostUsd = list.sumOf { it.estimatedCostUsd }
            )
        }.sortedByDescending { it.estimatedCostUsd }

        val taskBreakdown = taskGroups.map { (tid, list) ->
            TaskCostSummary(
                taskId = tid,
                requestCount = list.size,
                totalTokens = list.sumOf { (it.inputTokens + it.outputTokens).toLong() },
                estimatedCostUsd = list.sumOf { it.estimatedCostUsd }
            )
        }.take(15)

        TokenUsageSummary(
            totalInputTokens = totalIn,
            totalOutputTokens = totalOut,
            totalCachedTokens = totalCached,
            totalTokens = totalTok,
            totalEstimatedCostUsd = totalCost,
            totalRequests = entries.size,
            distinctTasks = taskCount,
            averageCostPerTaskUsd = if (taskCount > 0) totalCost / taskCount else 0.0,
            averageTokensPerAction = totalTok / entries.size,
            providerBreakdown = providerBreakdown,
            taskBreakdown = taskBreakdown,
            recentEntries = entries.take(25)
        )
    }

    suspend fun clearAll() = tokenUsageDao.clearAll()
}
