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

class TransientProviderException(val code: Int, override val message: String) : Exception(message)

@Singleton
class GeminiProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : LLMProvider {

    override val id: ProviderId = ProviderId.GEMINI

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
                error("Gemini provider is not configured. Set a backend proxy URL or development API key.")
            }

            val modelId = request.model.ifBlank { "gemini-3.8-flash" }
            val endpoint = if (config.usesProxy) {
                "${config.proxyBaseUrl.trimEnd('/')}/v1/llm/gemini/generate"
            } else {
                "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=${config.apiKey}"
            }

            val payload = buildJsonObject {
                if (config.usesProxy) {
                    put("model", JsonPrimitive(modelId))
                }
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", JsonPrimitive(request.systemPrompt)) })
                    })
                })
                put("contents", buildJsonArray {
                    add(buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", JsonPrimitive(request.userPrompt)) })
                        })
                    })
                })
                put("generationConfig", buildJsonObject {
                    put("temperature", JsonPrimitive(request.temperature))
                    put("maxOutputTokens", JsonPrimitive(request.maxOutputTokens))
                    if (request.expectJson) {
                        put("responseMimeType", JsonPrimitive("application/json"))
                    }
                })
            }

            val startNs = System.nanoTime()
            val rawResponse = executeWithTransientRetry(endpoint, payload.toString())
            val latencyMs = (System.nanoTime() - startNs) / 1_000_000L

            val root = json.parseToJsonElement(rawResponse).jsonObject
            val text = root["candidates"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("content")
                ?.jsonObject?.get("parts")
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.content
                ?: error("Gemini returned an empty candidate response")

            val usageMeta = root["usageMetadata"]?.jsonObject
            val inTokens = usageMeta?.get("promptTokenCount")?.jsonPrimitive?.intOrNull
                ?: estimateTokens(request.systemPrompt + request.userPrompt)
            val outTokens = usageMeta?.get("candidatesTokenCount")?.jsonPrimitive?.intOrNull
                ?: estimateTokens(text)
            val cachedTokens = usageMeta?.get("cachedContentTokenCount")?.jsonPrimitive?.intOrNull ?: 0
            val cost = ModelCostCatalog.estimateCostUsd(modelId, inTokens, outTokens, cachedTokens)

            recordUsage(inTokens, outTokens, cachedTokens, latencyMs, cost)

            LLMTextResponse(
                provider = ProviderId.GEMINI,
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
            emit(LLMStreamChunk.Error(err.localizedMessage ?: "Gemini stream failed"))
        }
    }.flowOn(Dispatchers.IO)

    override fun getUsage(): ProviderUsageSnapshot {
        val count = requestCount.get()
        return ProviderUsageSnapshot(
            provider = ProviderId.GEMINI,
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
                provider = ProviderId.GEMINI,
                isHealthy = false,
                latencyMs = 0L,
                message = "No API key or backend proxy configured"
            )
        }
        val startNs = System.nanoTime()
        val result = generate(
            LLMRequest(
                model = "gemini-3.8-flash",
                systemPrompt = "Reply with OK.",
                userPrompt = "ping",
                maxOutputTokens = 8,
                reason = "health_check"
            )
        )
        val latency = (System.nanoTime() - startNs) / 1_000_000L
        result.fold(
            onSuccess = {
                ProviderHealthStatus(ProviderId.GEMINI, true, latency, "Healthy (${it.model})")
            },
            onFailure = {
                ProviderHealthStatus(ProviderId.GEMINI, false, latency, it.localizedMessage ?: "Unreachable")
            }
        )
    }

    private suspend fun executeWithTransientRetry(url: String, bodyJson: String): String {
        val delays = listOf(800L, 1600L)
        var lastError: Exception? = null
        for (attempt in 0..delays.size) {
            val req = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
            try {
                okHttpClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) return raw
                    if (resp.code == 429 || resp.code == 503) {
                        throw TransientProviderException(resp.code, "Gemini HTTP ${resp.code}: ${raw.take(240)}")
                    }
                    error("Gemini API error ${resp.code}: ${raw.take(240)}")
                }
            } catch (e: TransientProviderException) {
                lastError = e
                if (attempt < delays.size) delay(delays[attempt])
            }
        }
        throw lastError ?: IllegalStateException("Gemini request failed")
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

internal fun stripCodeFences(raw: String): String = raw
    .trim()
    .removePrefix("```json")
    .removePrefix("```")
    .removeSuffix("```")
    .trim()

internal fun estimateTokens(text: String): Int = ((text.length + 3) / 4).coerceAtLeast(1)
