package com.orbit.assistant.agent.llm

import javax.inject.Inject
import javax.inject.Singleton

enum class RoutingTier {
    PRIMARY,
    COMPLEX_RECOVERY,
    LIGHTWEIGHT_CLASSIFICATION
}

data class RoutingPolicyConfig(
    val primaryProvider: ProviderId = ProviderId.GEMINI,
    val primaryModel: String = DEFAULT_PRIMARY_MODEL,
    val recoveryProvider: ProviderId = ProviderId.OPENAI,
    val recoveryModel: String = DEFAULT_RECOVERY_MODEL,
    val classifierProvider: ProviderId = ProviderId.GROQ,
    val classifierModel: String = DEFAULT_CLASSIFIER_MODEL,
    val recoveryFailureThreshold: Int = 2,
    val minConfidenceThreshold: Float = 0.45f
) {
    companion object {
        const val DEFAULT_PRIMARY_MODEL = "gemini-3.8-flash"
        const val DEFAULT_RECOVERY_MODEL = "gpt-6.1-sol"
        const val DEFAULT_CLASSIFIER_MODEL = "openai/gpt-oss-20b"
    }
}

data class RoutingSignals(
    val isLightweightClassification: Boolean = false,
    val consecutiveFailures: Int = 0,
    val repeatedScreenCount: Int = 0,
    val lastConfidence: Float = 1.0f,
    val lastErrorWasTransient: Boolean = false
)

data class RouteDecision(
    val tier: RoutingTier,
    val providerId: ProviderId,
    val model: String,
    val reason: String
)

@Singleton
class ModelRouter @Inject constructor(
    private val geminiProvider: GeminiProvider,
    private val openAIProvider: OpenAIProvider,
    private val groqProvider: GroqProvider
) {
    fun providerFor(id: ProviderId): LLMProvider = when (id) {
        ProviderId.GEMINI -> geminiProvider
        ProviderId.OPENAI -> openAIProvider
        ProviderId.GROQ -> groqProvider
    }

    /**
     * Routes to the right model tier based on task complexity and escalation thresholds.
     * Transient HTTP failures (e.g. 503/429) do NOT trigger escalation to expensive models.
     */
    fun selectRoute(
        signals: RoutingSignals,
        policy: RoutingPolicyConfig = RoutingPolicyConfig(),
        availabilityCheck: (ProviderId) -> Boolean = { providerFor(it).isReady() }
    ): RouteDecision {
        if (signals.isLightweightClassification && availabilityCheck(policy.classifierProvider)) {
            return RouteDecision(
                tier = RoutingTier.LIGHTWEIGHT_CLASSIFICATION,
                providerId = policy.classifierProvider,
                model = policy.classifierModel,
                reason = "lightweight_classification"
            )
        }

        val shouldEscalate = !signals.lastErrorWasTransient && (
            signals.consecutiveFailures >= policy.recoveryFailureThreshold ||
                signals.repeatedScreenCount >= 2 ||
                signals.lastConfidence < policy.minConfidenceThreshold
            )

        if (shouldEscalate && availabilityCheck(policy.recoveryProvider)) {
            return RouteDecision(
                tier = RoutingTier.COMPLEX_RECOVERY,
                providerId = policy.recoveryProvider,
                model = policy.recoveryModel,
                reason = "complex_recovery"
            )
        }

        // Default primary route, with safe fallback if primary isn't configured
        if (availabilityCheck(policy.primaryProvider)) {
            return RouteDecision(
                tier = RoutingTier.PRIMARY,
                providerId = policy.primaryProvider,
                model = policy.primaryModel,
                reason = "primary_action"
            )
        }

        val fallback = listOf(policy.classifierProvider, policy.recoveryProvider)
            .firstOrNull { availabilityCheck(it) } ?: policy.primaryProvider

        val fallbackModel = when (fallback) {
            policy.classifierProvider -> policy.classifierModel
            policy.recoveryProvider -> policy.recoveryModel
            else -> policy.primaryModel
        }
        return RouteDecision(
            tier = RoutingTier.PRIMARY,
            providerId = fallback,
            model = fallbackModel,
            reason = "configured_fallback"
        )
    }

    private fun LLMProvider.isReady(): Boolean = when (this) {
        is GeminiProvider -> config.isConfigured
        is OpenAICompatibleProvider -> config.isConfigured
        else -> true
    }
}
