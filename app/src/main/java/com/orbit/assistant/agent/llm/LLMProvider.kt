package com.orbit.assistant.agent.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
enum class ProviderId(val displayName: String) {
    GEMINI("Google Gemini"),
    OPENAI("OpenAI"),
    GROQ("Groq")
}

data class LLMProviderConfig(
    val apiKey: String = "",
    val proxyBaseUrl: String = "",
    val baseUrlOverride: String = ""
) {
    val usesProxy: Boolean
        get() = proxyBaseUrl.isNotBlank()

    val isConfigured: Boolean
        get() = usesProxy || apiKey.isNotBlank()
}

data class LLMRequest(
    val model: String,
    val systemPrompt: String,
    val userPrompt: String,
    val temperature: Double = 0.2,
    val maxOutputTokens: Int = 512,
    val taskId: String = "",
    val reason: String = "agent_step",
    val expectJson: Boolean = false
)

data class LLMUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedTokens: Int = 0,
    val latencyMs: Long = 0L,
    val estimatedCostUsd: Double = 0.0
) {
    val totalTokens: Int
        get() = inputTokens + outputTokens
}

data class LLMTextResponse(
    val provider: ProviderId,
    val model: String,
    val text: String,
    val usage: LLMUsage
)

data class LLMStructuredResponse<T>(
    val provider: ProviderId,
    val model: String,
    val rawText: String,
    val data: T,
    val usage: LLMUsage
)

sealed class LLMStreamChunk {
    data class Delta(val text: String) : LLMStreamChunk()
    data class Completed(val fullText: String, val usage: LLMUsage) : LLMStreamChunk()
    data class Error(val message: String) : LLMStreamChunk()
}

data class ProviderUsageSnapshot(
    val provider: ProviderId,
    val requestCount: Int = 0,
    val totalInputTokens: Long = 0L,
    val totalOutputTokens: Long = 0L,
    val totalCachedTokens: Long = 0L,
    val totalEstimatedCostUsd: Double = 0.0,
    val averageLatencyMs: Long = 0L
)

data class ProviderHealthStatus(
    val provider: ProviderId,
    val isHealthy: Boolean,
    val latencyMs: Long,
    val message: String
)

interface LLMProvider {
    val id: ProviderId

    suspend fun generate(request: LLMRequest): Result<LLMTextResponse>

    suspend fun <T> generateStructured(
        request: LLMRequest,
        serializer: KSerializer<T>
    ): Result<LLMStructuredResponse<T>>

    fun stream(request: LLMRequest): Flow<LLMStreamChunk>

    fun getUsage(): ProviderUsageSnapshot

    suspend fun healthCheck(): ProviderHealthStatus
}

object ModelCostCatalog {
    data class ModelRate(
        val inputPerMillionUsd: Double,
        val outputPerMillionUsd: Double,
        val cachedInputPerMillionUsd: Double
    )

    private val rates: Map<String, ModelRate> = mapOf(
        "gemini-3.8-flash" to ModelRate(0.15, 0.60, 0.0375),
        "gemini-3.6-flash" to ModelRate(0.15, 0.60, 0.0375),
        "gemini-3.5-flash-lite" to ModelRate(0.10, 0.40, 0.025),
        "gemini-3.1-flash-lite" to ModelRate(0.125, 0.75, 0.025),
        "gpt-6.1-sol" to ModelRate(2.50, 10.00, 0.625),
        "gpt-5.6-sol" to ModelRate(5.00, 30.00, 1.25),
        "gpt-4o-mini" to ModelRate(0.15, 0.60, 0.075),
        "openai/gpt-oss-20b" to ModelRate(0.075, 0.30, 0.075),
        "llama-3.3-70b-versatile" to ModelRate(0.59, 0.79, 0.59)
    )

    fun estimateCostUsd(
        model: String,
        inputTokens: Int,
        outputTokens: Int,
        cachedTokens: Int = 0
    ): Double {
        val rate = rates[model.lowercase()] ?: ModelRate(0.20, 0.80, 0.05)
        val uncachedInput = (inputTokens - cachedTokens).coerceAtLeast(0)
        val inputCost = (uncachedInput / 1_000_000.0) * rate.inputPerMillionUsd
        val cachedCost = (cachedTokens / 1_000_000.0) * rate.cachedInputPerMillionUsd
        val outputCost = (outputTokens / 1_000_000.0) * rate.outputPerMillionUsd
        return inputCost + cachedCost + outputCost
    }
}
