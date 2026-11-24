package com.orbit.assistant.data.repository

import com.orbit.assistant.data.preferences.AutonomyLevel
import com.orbit.assistant.data.preferences.ExecutionBehavior
import com.orbit.assistant.data.preferences.ExecutionMode
import com.orbit.assistant.data.preferences.OrbitPreferencesDataStore
import com.orbit.assistant.data.preferences.OrbitSettings
import com.orbit.assistant.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataStore: OrbitPreferencesDataStore
) : PreferencesRepository {

    override val settingsFlow: Flow<OrbitSettings> = dataStore.settingsFlow

    override suspend fun updateDarkMode(enabled: Boolean) = dataStore.setDarkMode(enabled)
    override suspend fun updateAutonomyLevel(level: AutonomyLevel) = dataStore.setAutonomyLevel(level)
    override suspend fun updateConfirmSensitive(enabled: Boolean) = dataStore.setConfirmSensitive(enabled)
    override suspend fun updateConfirmPurchases(enabled: Boolean) = dataStore.setConfirmPurchases(enabled)
    override suspend fun updateConfirmMessages(enabled: Boolean) = dataStore.setConfirmMessages(enabled)
    override suspend fun updateConfirmDestructive(enabled: Boolean) = dataStore.setConfirmDestructive(enabled)
    override suspend fun updatePreferredShoppingApps(apps: Set<String>) = dataStore.setPreferredShoppingApps(apps)
    override suspend fun updatePreferredBrands(brands: String) = dataStore.setPreferredBrands(brands)
    override suspend fun updatePreferredCategories(categories: String) = dataStore.setPreferredCategories(categories)
    override suspend fun updatePreferredLanguage(language: String) = dataStore.setPreferredLanguage(language)
    override suspend fun updateDefaultExecutionBehavior(behavior: ExecutionBehavior) =
        dataStore.setExecutionBehavior(behavior)
    override suspend fun updateScreenshotRetention(enabled: Boolean) = dataStore.setScreenshotRetention(enabled)
    override suspend fun updateExecutionMode(mode: ExecutionMode) = dataStore.setExecutionMode(mode)
    override suspend fun updateProxyBaseUrl(url: String) = dataStore.setProxyBaseUrl(url)
    override suspend fun updateGeminiDevKey(key: String) = dataStore.setGeminiDevKey(key)
    override suspend fun updateOpenAiDevKey(key: String) = dataStore.setOpenAiDevKey(key)
    override suspend fun updateGroqDevKey(key: String) = dataStore.setGroqDevKey(key)
    override suspend fun updatePrimaryModelOverride(model: String) = dataStore.setPrimaryModelOverride(model)
    override suspend fun updateRecoveryModelOverride(model: String) = dataStore.setRecoveryModelOverride(model)
    override suspend fun updateClassifierModelOverride(model: String) = dataStore.setClassifierModelOverride(model)
    override suspend fun updateMaxObservationTokens(tokens: Int) = dataStore.setMaxObservationTokens(tokens)
    override suspend fun clearAgentMemory() = dataStore.clearPersonalizationMemory()
}
