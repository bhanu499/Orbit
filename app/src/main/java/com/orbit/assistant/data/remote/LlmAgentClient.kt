package com.orbit.assistant.data.remote

import com.orbit.assistant.data.preferences.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LlmNextStep(
    val thought: String = "",
    val action: String = "done",
    val nodeIndex: Int? = null,
    val text: String? = null,
    val packageName: String? = null,
    val direction: String? = null,
    val summary: String? = null
)

@Singleton
class LlmAgentClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun planNextStep(
        provider: LlmProvider,
        apiKey: String,
        model: String,
        baseUrl: String,
        userGoal: String,
        currentPackage: String,
        uiNodesText: String,
        installedAppsText: String,
        historyText: String
    ): Result<LlmNextStep> = withContext(Dispatchers.IO) {
        runCatching {
            val systemPrompt = """
                You are Orbit, an Android AI agent controlling the user's phone via the Android Accessibility tree.
                Respond ONLY with a single valid JSON object (no markdown fences) with these keys:
                - "thought": short reasoning
                - "action": one of ["launch_app", "tap", "type", "scroll", "back", "wait", "request_approval", "needs_user_intervention", "done"]
                - "nodeIndex": integer index from UI tree (for tap or type) or null
                - "text": text to type (for type) or null
                - "packageName": Android package name (for launch_app) or null
                - "direction": "down" | "up" | "left" | "right" (for scroll) or null
                - "summary": short user-facing message (required for request_approval, needs_user_intervention, or done)
                Safety rule: NEVER complete payment or final checkout. Always use "request_approval" before any purchase/checkout/send action, and "needs_user_intervention" for login/OTP/CAPTCHA.
            """.trimIndent()

            val userContent = """
                Goal: $userGoal
                Current foreground package: $currentPackage
                Available launchable apps:
                $installedAppsText

                Recent steps:
                $historyText

                Current Accessibility UI tree:
                $uiNodesText
            """.trimIndent()

            val rawText = when (provider) {
                LlmProvider.GEMINI -> callGemini(apiKey, model, systemPrompt, userContent)
                LlmProvider.OPENAI_COMPATIBLE -> callOpenAiCompatible(baseUrl, apiKey, model, systemPrompt, userContent)
                LlmProvider.DEMO -> error("Demo provider should not call LlmAgentClient")
            }

            val cleaned = rawText
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            json.decodeFromString<LlmNextStep>(cleaned)
        }
    }

    private fun callGemini(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userContent: String
    ): String {
        val modelId = model.ifBlank { "gemini-2.5-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
        val bodyObj = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", JsonPrimitive(systemPrompt)) })
                })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", JsonPrimitive(userContent)) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseMimeType", JsonPrimitive("application/json"))
                put("temperature", JsonPrimitive(0.2))
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(bodyObj.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Gemini API error ${response.code}: ${raw.take(300)}")
            }
            val root = json.parseToJsonElement(raw).jsonObject
            return root["candidates"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject["parts"]!!
                .jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        }
    }

    private fun callOpenAiCompatible(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userContent: String
    ): String {
        val trimmedBase = baseUrl.trim().trimEnd('/').ifBlank { "https://api.openai.com/v1" }
        val url = "$trimmedBase/chat/completions"
        val bodyObj = buildJsonObject {
            put("model", JsonPrimitive(model.ifBlank { "gpt-4o-mini" }))
            put("temperature", JsonPrimitive(0.2))
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("system"))
                    put("content", JsonPrimitive(systemPrompt))
                })
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive(userContent))
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(bodyObj.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("LLM API error ${response.code}: ${raw.take(300)}")
            }
            val root = json.parseToJsonElement(raw).jsonObject
            return root["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!
                .jsonPrimitive.content
        }
    }
}
