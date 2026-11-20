package com.orbit.assistant.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
            retainScreenshotsLocally = prefs[Keys.SCREENSHOT_RETENTION] ?: false
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

    suspend fun clearPersonalizationMemory() {
        context.orbitDataStore.edit { prefs ->
            prefs.remove(Keys.PREFERRED_SHOPPING_APPS)
            prefs.remove(Keys.PREFERRED_BRANDS)
            prefs.remove(Keys.PREFERRED_CATEGORIES)
        }
    }
}
