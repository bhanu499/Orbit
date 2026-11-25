package com.orbit.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.orbit.assistant.agent.llm.RoutingPolicyConfig
import com.orbit.assistant.agent.observation.ScreenObservationNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.orbitDataStore: DataStore<Preferences> by preferencesDataStore(name = "orbit_preferences")

@Singleton
class OrbitPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val AUTONOMY_LEVEL = stringPreferencesKey("autonomy_level")
        val CONFIRM_SENSITIVE = booleanPreferencesKey("confirm_sensitive")
        val CONFIRM_PURCHASES = booleanPreferencesKey("confirm_purchases")
        val CONFIRM_MESSAGES = booleanPreferencesKey("confirm_messages")
        val CONFIRM_DESTRUCTIVE = booleanPreferencesKey("confirm_destructive")
        val PREFERRED_SHOPPING_APPS = stringSetPreferencesKey("preferred_shopping_apps")
        val PREFERRED_BRANDS = stringPreferencesKey("preferred_brands")
        val PREFERRED_CATEGORIES = stringPreferencesKey("preferred_categories")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val EXECUTION_BEHAVIOR = stringPreferencesKey("execution_behavior")
        val SCREENSHOT_RETENTION = booleanPreferencesKey("screenshot_retention")
        val EXECUTION_MODE = stringPreferencesKey("execution_mode")
        val PROXY_BASE_URL = stringPreferencesKey("proxy_base_url")
        val GEMINI_DEV_KEY = stringPreferencesKey("gemini_dev_key")
        val OPENAI_DEV_KEY = stringPreferencesKey("openai_dev_key")
        val GROQ_DEV_KEY = stringPreferencesKey("groq_dev_key")
        val PRIMARY_MODEL_OVERRIDE = stringPreferencesKey("primary_model_override")
        val RECOVERY_MODEL_OVERRIDE = stringPreferencesKey("recovery_model_override")
        val CLASSIFIER_MODEL_OVERRIDE = stringPreferencesKey("classifier_model_override")
        val MAX_OBSERVATION_TOKENS = intPreferencesKey("max_observation_tokens")
        val USE_LOCAL_LLM = booleanPreferencesKey("use_local_llm")
        val LOCAL_LLM_BASE_URL = stringPreferencesKey("local_llm_base_url")
        val LOCAL_LLM_MODEL = stringPreferencesKey("local_llm_model")
    }

    val settingsFlow: Flow<OrbitSettings> = context.orbitDataStore.data.map { prefs ->
        OrbitSettings(
            isDarkMode = prefs[Keys.DARK_MODE] ?: true,
            autonomyLevel = prefs[Keys.AUTONOMY_LEVEL]?.let {
                runCatching { AutonomyLevel.valueOf(it) }.getOrNull()
            } ?: AutonomyLevel.BALANCED,
            confirmSensitiveActions = prefs[Keys.CONFIRM_SENSITIVE] ?: true,
            confirmBeforePurchases = prefs[Keys.CONFIRM_PURCHASES] ?: true,
            confirmBeforeSendingMessages = prefs[Keys.CONFIRM_MESSAGES] ?: true,
            confirmBeforeDestructiveActions = prefs[Keys.CONFIRM_DESTRUCTIVE] ?: true,
            preferredShoppingApps = prefs[Keys.PREFERRED_SHOPPING_APPS]
                ?: setOf("Blinkit", "Zepto", "Swiggy Instamart"),
            preferredBrands = prefs[Keys.PREFERRED_BRANDS]
                ?: "Amul, Akshayakalpa, Blue Tokai, Harvest Gold",
            preferredCategories = prefs[Keys.PREFERRED_CATEGORIES]
                ?: "Dairy, Fresh Bakery, Coffee, Pantry",
            preferredLanguage = prefs[Keys.PREFERRED_LANGUAGE] ?: "English",
            defaultExecutionBehavior = prefs[Keys.EXECUTION_BEHAVIOR]?.let {
                runCatching { ExecutionBehavior.valueOf(it) }.getOrNull()
            } ?: ExecutionBehavior.STEP_BY_STEP,
            retainScreenshotsLocally = prefs[Keys.SCREENSHOT_RETENTION] ?: false,
            executionMode = prefs[Keys.EXECUTION_MODE]?.let {
                runCatching { ExecutionMode.valueOf(it) }.getOrNull()
            } ?: ExecutionMode.DEMO,
            proxyBaseUrl = prefs[Keys.PROXY_BASE_URL] ?: "",
            geminiDevApiKey = prefs[Keys.GEMINI_DEV_KEY] ?: "",
            openAiDevApiKey = prefs[Keys.OPENAI_DEV_KEY] ?: "",
            groqDevApiKey = prefs[Keys.GROQ_DEV_KEY] ?: "",
            primaryModelOverride = prefs[Keys.PRIMARY_MODEL_OVERRIDE]
                ?: RoutingPolicyConfig.DEFAULT_PRIMARY_MODEL,
            recoveryModelOverride = prefs[Keys.RECOVERY_MODEL_OVERRIDE]
                ?: RoutingPolicyConfig.DEFAULT_RECOVERY_MODEL,
            classifierModelOverride = prefs[Keys.CLASSIFIER_MODEL_OVERRIDE]
                ?: RoutingPolicyConfig.DEFAULT_CLASSIFIER_MODEL,
            maxObservationTokens = prefs[Keys.MAX_OBSERVATION_TOKENS]
                ?: ScreenObservationNormalizer.DEFAULT_MAX_TOKEN_BUDGET,
            useLocalLlm = prefs[Keys.USE_LOCAL_LLM] ?: false,
            localLlmBaseUrl = prefs[Keys.LOCAL_LLM_BASE_URL] ?: "http://127.0.0.1:11434/v1",
            localLlmModel = prefs[Keys.LOCAL_LLM_MODEL] ?: "qwen2.5:3b"
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.orbitDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setAutonomyLevel(level: AutonomyLevel) {
        context.orbitDataStore.edit { it[Keys.AUTONOMY_LEVEL] = level.name }
    }

    suspend fun setConfirmSensitive(enabled: Boolean) {
        context.orbitDataStore.edit { it[Keys.CONFIRM_SENSITIVE] = enabled }
    }

    suspend fun setConfirmPurchases(enabled: Boolean) {
        context.orbitDataStore.edit { it[Keys.CONFIRM_PURCHASES] = enabled }
    }

    suspend fun setConfirmMessages(enabled: Boolean) {
        context.orbitDataStore.edit { it[Keys.CONFIRM_MESSAGES] = enabled }
    }

    suspend fun setConfirmDestructive(enabled: Boolean) {
        context.orbitDataStore.edit { it[Keys.CONFIRM_DESTRUCTIVE] = enabled }
    }

    suspend fun setPreferredShoppingApps(apps: Set<String>) {
        context.orbitDataStore.edit { it[Keys.PREFERRED_SHOPPING_APPS] = apps }
    }

    suspend fun setPreferredBrands(brands: String) {
        context.orbitDataStore.edit { it[Keys.PREFERRED_BRANDS] = brands }
    }

    suspend fun setPreferredCategories(categories: String) {
        context.orbitDataStore.edit { it[Keys.PREFERRED_CATEGORIES] = categories }
    }

    suspend fun setPreferredLanguage(language: String) {
        context.orbitDataStore.edit { it[Keys.PREFERRED_LANGUAGE] = language }
    }

    suspend fun setExecutionBehavior(behavior: ExecutionBehavior) {
        context.orbitDataStore.edit { it[Keys.EXECUTION_BEHAVIOR] = behavior.name }
    }

    suspend fun setScreenshotRetention(enabled: Boolean) {
        context.orbitDataStore.edit { it[Keys.SCREENSHOT_RETENTION] = enabled }
    }

    suspend fun setExecutionMode(mode: ExecutionMode) {
        context.orbitDataStore.edit { it[Keys.EXECUTION_MODE] = mode.name }
    }

    suspend fun setProxyBaseUrl(url: String) {
        context.orbitDataStore.edit { it[Keys.PROXY_BASE_URL] = url }
    }

    suspend fun setGeminiDevKey(key: String) {
        context.orbitDataStore.edit { it[Keys.GEMINI_DEV_KEY] = key }
    }

    suspend fun setOpenAiDevKey(key: String) {
        context.orbitDataStore.edit { it[Keys.OPENAI_DEV_KEY] = key }
    }

    suspend fun setGroqDevKey(key: String) {
        context.orbitDataStore.edit { it[Keys.GROQ_DEV_KEY] = key }
    }

    suspend fun setPrimaryModelOverride(model: String) {
        context.orbitDataStore.edit { it[Keys.PRIMARY_MODEL_OVERRIDE] = model }
    }

    suspend fun setRecoveryModelOverride(model: String) {
        context.orbitDataStore.edit { it[Keys.RECOVERY_MODEL_OVERRIDE] = model }
    }

    suspend fun setClassifierModelOverride(model: String) {
        context.orbitDataStore.edit { it[Keys.CLASSIFIER_MODEL_OVERRIDE] = model }
    }

    suspend fun setMaxObservationTokens(tokens: Int) {
        context.orbitDataStore.edit { it[Keys.MAX_OBSERVATION_TOKENS] = tokens }
    }

    suspend fun setUseLocalLlm(enabled: Boolean) {
        context.orbitDataStore.edit { it[Keys.USE_LOCAL_LLM] = enabled }
    }

    suspend fun setLocalLlmBaseUrl(url: String) {
        context.orbitDataStore.edit { it[Keys.LOCAL_LLM_BASE_URL] = url }
    }

    suspend fun setLocalLlmModel(model: String) {
        context.orbitDataStore.edit { it[Keys.LOCAL_LLM_MODEL] = model }
    }

    suspend fun clearPersonalizationMemory() {
        context.orbitDataStore.edit { prefs ->
            prefs.remove(Keys.PREFERRED_SHOPPING_APPS)
            prefs.remove(Keys.PREFERRED_BRANDS)
            prefs.remove(Keys.PREFERRED_CATEGORIES)
        }
    }
}
