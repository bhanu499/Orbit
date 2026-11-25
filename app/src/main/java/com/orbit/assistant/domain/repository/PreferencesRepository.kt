package com.orbit.assistant.domain.repository

import com.orbit.assistant.data.preferences.AutonomyLevel
import com.orbit.assistant.data.preferences.ExecutionBehavior
import com.orbit.assistant.data.preferences.ExecutionMode
import com.orbit.assistant.data.preferences.OrbitSettings
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val settingsFlow: Flow<OrbitSettings>
    suspend fun updateDarkMode(enabled: Boolean)
    suspend fun updateAutonomyLevel(level: AutonomyLevel)
    suspend fun updateConfirmSensitive(enabled: Boolean)
    suspend fun updateConfirmPurchases(enabled: Boolean)
    suspend fun updateConfirmMessages(enabled: Boolean)
    suspend fun updateConfirmDestructive(enabled: Boolean)
    suspend fun updatePreferredShoppingApps(apps: Set<String>)
    suspend fun updatePreferredBrands(brands: String)
    suspend fun updatePreferredCategories(categories: String)
    suspend fun updatePreferredLanguage(language: String)
    suspend fun updateDefaultExecutionBehavior(behavior: ExecutionBehavior)
    suspend fun updateScreenshotRetention(enabled: Boolean)
    suspend fun updateExecutionMode(mode: ExecutionMode)
    suspend fun updateProxyBaseUrl(url: String)
    suspend fun updateGeminiDevKey(key: String)
    suspend fun updateOpenAiDevKey(key: String)
    suspend fun updateGroqDevKey(key: String)
    suspend fun updatePrimaryModelOverride(model: String)
    suspend fun updateRecoveryModelOverride(model: String)
    suspend fun updateClassifierModelOverride(model: String)
    suspend fun updateMaxObservationTokens(tokens: Int)
    suspend fun updateUseLocalLlm(enabled: Boolean)
    suspend fun updateLocalLlmBaseUrl(url: String)
    suspend fun updateLocalLlmModel(model: String)
    suspend fun clearAgentMemory()
}
