package com.orbit.assistant.domain.repository

import com.orbit.assistant.data.preferences.AutonomyLevel
import com.orbit.assistant.data.preferences.ExecutionBehavior
import com.orbit.assistant.data.preferences.LlmProvider
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
    suspend fun updateLlmProvider(provider: LlmProvider)
    suspend fun updateLlmApiKey(apiKey: String)
    suspend fun updateLlmModel(model: String)
    suspend fun updateLlmBaseUrl(baseUrl: String)
    suspend fun clearAgentMemory()
}
