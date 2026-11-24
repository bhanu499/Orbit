package com.orbit.assistant.agent.llm

import com.orbit.assistant.agent.actions.ActionValidationResult
import com.orbit.assistant.agent.actions.AgentActionPlan
import com.orbit.assistant.agent.actions.AgentActionType
import com.orbit.assistant.agent.actions.AgentActionValidator
import com.orbit.assistant.agent.observation.RawUiNode
import com.orbit.assistant.agent.observation.RawUiTree
import com.orbit.assistant.agent.observation.ScreenObservationNormalizer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRouterAndValidatorTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val okHttp = OkHttpClient()
    private val router = ModelRouter(
        geminiProvider = GeminiProvider(okHttp, json),
        openAIProvider = OpenAIProvider(okHttp, json),
        groqProvider = GroqProvider(okHttp, json)
    )
    private val allAvailable: (ProviderId) -> Boolean = { true }

    @Test
    fun `default routing selects Gemini 3_8 Flash for primary, GPT-6_1 Sol for recovery, and Groq GPT-OSS 20B for classification`() {
        val primary = router.selectRoute(RoutingSignals(), availabilityCheck = allAvailable)
        assertEquals(ProviderId.GEMINI, primary.providerId)
        assertEquals("gemini-3.8-flash", primary.model)

        val classifier = router.selectRoute(
            RoutingSignals(isLightweightClassification = true),
            availabilityCheck = allAvailable
        )
        assertEquals(ProviderId.GROQ, classifier.providerId)
        assertEquals("openai/gpt-oss-20b", classifier.model)

        val recovery = router.selectRoute(
            RoutingSignals(consecutiveFailures = 2),
            availabilityCheck = allAvailable
        )
        assertEquals(ProviderId.OPENAI, recovery.providerId)
        assertEquals("gpt-6.1-sol", recovery.model)
    }

    @Test
    fun `transient error does NOT escalate to expensive recovery model`() {
        val route = router.selectRoute(
            RoutingSignals(
                consecutiveFailures = 3,
                lastErrorWasTransient = true
            ),
            availabilityCheck = allAvailable
        )
        assertEquals(RoutingTier.PRIMARY, route.tier)
        assertEquals("gemini-3.8-flash", route.model)
    }

    @Test
    fun `model overrides are respected`() {
        val policy = RoutingPolicyConfig(
            primaryModel = "gemini-3.5-flash-lite",
            recoveryModel = "gpt-5.6-sol",
            classifierModel = "llama-3.3-70b-versatile"
        )
        val primary = router.selectRoute(RoutingSignals(), policy, allAvailable)
        assertEquals("gemini-3.5-flash-lite", primary.model)

        val recovery = router.selectRoute(RoutingSignals(lastConfidence = 0.2f), policy, allAvailable)
        assertEquals("gpt-5.6-sol", recovery.model)
    }

    @Test
    fun `validator rejects incorrect element selection and missing required fields`() {
        val normalizer = ScreenObservationNormalizer()
        val obs = normalizer.normalize(
            RawUiTree(
                packageName = "com.zeptoconsumerapp",
                nodes = listOf(RawUiNode(10, text = "Milk", isClickable = true))
            )
        )

        val invalidTap = AgentActionValidator.validate(
            AgentActionPlan(thought = "Tap wrong node", action = AgentActionType.TAP, nodeId = 999),
            obs
        )
        assertTrue(invalidTap is ActionValidationResult.Invalid)

        val validTap = AgentActionValidator.validate(
            AgentActionPlan(thought = "Tap milk", action = AgentActionType.TAP, nodeId = 10),
            obs
        )
        assertTrue(validTap is ActionValidationResult.Valid)

        val invalidType = AgentActionValidator.validate(
            AgentActionPlan(thought = "Type empty", action = AgentActionType.TYPE_TEXT, nodeId = 10, text = "  "),
            obs
        )
        assertTrue(invalidType is ActionValidationResult.Invalid)
    }
}
