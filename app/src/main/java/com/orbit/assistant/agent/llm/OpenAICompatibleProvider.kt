package com.orbit.assistant.agent.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

open class OpenAICompatibleProvider(
    override val id: ProviderId,
    private val defaultBaseUrl: String,
    private val defaultModel: String,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : LLMProvider {

    @Volatile
    var config: LLMProviderConfig = LLMProviderConfig()

    private val requestCount = AtomicInteger(0)
    private val inputTokensSum = AtomicLong(0)
    private val outputTokensSum = AtomicLong(0)
    private val cachedTokensSum = AtomicLong(0)
    private val latencySumMs = AtomicLong(0)
    @Volatile private var costSumUsd = 0.0

    override suspend fun generate(request: LLMRequest): Result<LLMTextResponse> = withContext(Dispatchers.IO) {
        runCatching {
            if (!config.isConfigured) {
                error("${id.displayName} is not configured. Set a backend proxy URL or development API key.")
            }

            val modelId = request.model.ifBlank { defaultModel }
            val endpoint = if (config.usesProxy) {
                "${config.proxyBaseUrl.trimEnd('/')}/v1/llm/${id.name.lowercase()}/generate"
            } else {
                val base = config.baseUrlOverride.ifBlank { defaultBaseUrl }.trimEnd('/')
                "$base/chat/completions"
            }

            val payload = buildJsonObject {
                put("model", JsonPrimitive(modelId))
                put("temperature", JsonPrimitive(request.temperature))
                put("max_tokens", JsonPrimitive(request.maxOutputTokens))
                if (request.expectJson) {
                    put("response_format", buildJsonObject {
                        put("type", JsonPrimitive("json_object"))
                    })
                }
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", JsonPrimitive("system"))
                        put("content", JsonPrimitive(request.systemPrompt))
                    })
                    add(buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(request.userPrompt))
                    })
                })
            }

            val startNs = System.nanoTime()
            val rawResponse = executeWithTransientRetry(endpoint, payload.toString())
            val latencyMs = (System.nanoTime() - startNs) / 1_000_000L

            val root = json.parseToJsonElement(rawResponse).jsonObject
            val text = root["choices"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?: error("${id.displayName} returned an empty response")

            val usageObj = root["usage"]?.jsonObject
            val inTokens = usageObj?.get("prompt_tokens")?.jsonPrimitive?.intOrNull
                ?: estimateTokens(request.systemPrompt + request.userPrompt)
            val outTokens = usageObj?.get("completion_tokens")?.jsonPrimitive?.intOrNull
                ?: estimateTokens(text)
            val cachedTokens = usageObj?.get("prompt_tokens_details")
                ?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.intOrNull ?: 0
            val cost = ModelCostCatalog.estimateCostUsd(modelId, inTokens, outTokens, cachedTokens)

            recordUsage(inTokens, outTokens, cachedTokens, latencyMs, cost)

            LLMTextResponse(
                provider = id,
                model = modelId,
                text = text,
                usage = LLMUsage(inTokens, outTokens, cachedTokens, latencyMs, cost)
            )
        }
    }

    override suspend fun <T> generateStructured(
        request: LLMRequest,
        serializer: KSerializer<T>
    ): Result<LLMStructuredResponse<T>> {
        val textResult = generate(request.copy(expectJson = true))
        return textResult.mapCatching { resp ->
            val cleaned = stripCodeFences(resp.text)
            val parsed = json.decodeFromString(serializer, cleaned)
            LLMStructuredResponse(
                provider = resp.provider,
                model = resp.model,
                rawText = resp.text,
                data = parsed,
                usage = resp.usage
            )
        }
    }

    override fun stream(request: LLMRequest): Flow<LLMStreamChunk> = flow {
        val result = generate(request)
        result.onSuccess { resp ->
            emit(LLMStreamChunk.Delta(resp.text))
            emit(LLMStreamChunk.Completed(resp.text, resp.usage))
        }.onFailure { err ->
            emit(LLMStreamChunk.Error(err.localizedMessage ?: "${id.displayName} stream failed"))
        }
    }.flowOn(Dispatchers.IO)

    override fun getUsage(): ProviderUsageSnapshot {
        val count = requestCount.get()
        return ProviderUsageSnapshot(
            provider = id,
            requestCount = count,
            totalInputTokens = inputTokensSum.get(),
            totalOutputTokens = outputTokensSum.get(),
            totalCachedTokens = cachedTokensSum.get(),
            totalEstimatedCostUsd = costSumUsd,
            averageLatencyMs = if (count > 0) latencySumMs.get() / count else 0L
        )
    }

    override suspend fun healthCheck(): ProviderHealthStatus = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext ProviderHealthStatus(
                provider = id,
                isHealthy = false,
                latencyMs = 0L,
                message = "No API key or backend proxy configured"
            )
        }
        val startNs = System.nanoTime()
        val res = generate(
            LLMRequest(
                model = defaultModel,
                systemPrompt = "Reply with OK.",
                userPrompt = "ping",
                maxOutputTokens = 8,
                reason = "health_check"
            )
        )
        val latency = (System.nanoTime() - startNs) / 1_000_000L
        res.fold(
            onSuccess = { ProviderHealthStatus(id, true, latency, "Healthy (${it.model})") },
            onFailure = { ProviderHealthStatus(id, false, latency, it.localizedMessage ?: "Unreachable") }
        )
    }

    private suspend fun executeWithTransientRetry(url: String, bodyJson: String): String {
        val delays = listOf(800L, 1600L)
        var lastError: Exception? = null
        for (attempt in 0..delays.size) {
            val builder = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
            if (!config.usesProxy && config.apiKey.isNotBlank()) {
                builder.addHeader("Authorization", "Bearer ${config.apiKey}")
            }
            try {
                okHttpClient.newCall(builder.build()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) return raw
                    if (resp.code == 429 || resp.code == 503) {
                        throw TransientProviderException(resp.code, "${id.displayName} HTTP ${resp.code}: ${raw.take(240)}")
                    }
                    error("${id.displayName} API error ${resp.code}: ${raw.take(240)}")
                }
            } catch (e: TransientProviderException) {
                lastError = e
                if (attempt < delays.size) delay(delays[attempt])
            }
        }
        throw lastError ?: IllegalStateException("${id.displayName} request failed")
    }

    private fun recordUsage(inTok: Int, outTok: Int, cachedTok: Int, latencyMs: Long, cost: Double) {
        requestCount.incrementAndGet()
        inputTokensSum.addAndGet(inTok.toLong())
        outputTokensSum.addAndGet(outTok.toLong())
        cachedTokensSum.addAndGet(cachedTok.toLong())
        latencySumMs.addAndGet(latencyMs)
        synchronized(this) { costSumUsd += cost }
    }
}

@Singleton
class OpenAIProvider @Inject constructor(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAICompatibleProvider(
    id = ProviderId.OPENAI,
    defaultBaseUrl = "https://api.openai.com/v1",
    defaultModel = "gpt-6.1-sol",
    okHttpClient = okHttpClient,
    json = json
)

@Singleton
class GroqProvider @Inject constructor(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAICompatibleProvider(
    id = ProviderId.GROQ,
    defaultBaseUrl = "https://api.groq.com/openai/v1",
    defaultModel = "openai/gpt-oss-20b",
    okHttpClient = okHttpClient,
    json = json
)
