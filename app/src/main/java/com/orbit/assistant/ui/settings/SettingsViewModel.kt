package com.orbit.assistant.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.assistant.agent.appdiscovery.AppDescriptor
import com.orbit.assistant.agent.appdiscovery.AppDiscoveryManager
import com.orbit.assistant.agent.automation.OrbitAccessibilityService
import com.orbit.assistant.data.preferences.AutonomyLevel
import com.orbit.assistant.data.preferences.ExecutionBehavior
import com.orbit.assistant.data.preferences.LlmProvider
import com.orbit.assistant.data.preferences.OrbitSettings
import com.orbit.assistant.domain.repository.AgentRepository
import com.orbit.assistant.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermissionStatusItem(
    val id: String,
    val title: String,
    val rationale: String,
    val isGranted: Boolean,
    val statusLabel: String
)

data class SettingsUiState(
    val settings: OrbitSettings = OrbitSettings(),
    val permissions: List<PermissionStatusItem> = emptyList(),
    val discoveredApps: List<AppDescriptor> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val agentRepository: AgentRepository,
    private val appDiscoveryManager: AppDiscoveryManager
) : ViewModel() {

    private val _permissions = MutableStateFlow<List<PermissionStatusItem>>(emptyList())
    private val _discoveredApps = MutableStateFlow<List<AppDescriptor>>(emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.settingsFlow,
        _permissions,
        _discoveredApps
    ) { settings, perms, apps ->
        SettingsUiState(
            settings = settings,
            permissions = perms,
            discoveredApps = apps
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    init {
        refreshPermissions()
        viewModelScope.launch {
            _discoveredApps.value = appDiscoveryManager.discoverLaunchableApps()
        }
    }

    fun refreshPermissions() {
        val orbitAccessibilityActive = OrbitAccessibilityService.instance.value != null

        val notificationAccess = NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)

        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        _permissions.value = listOf(
            PermissionStatusItem(
                id = "accessibility",
                title = "Accessibility (Orbit Service)",
                rationale = "Required to read the live UI tree and perform taps, typing, and scrolling in real apps.",
                isGranted = orbitAccessibilityActive,
                statusLabel = if (orbitAccessibilityActive) "Connected" else "Not enabled"
            ),
            PermissionStatusItem(
                id = "notification",
                title = "Notification access",
                rationale = "Allows Orbit to read delivery updates, OTPs, or status alerts when permitted.",
                isGranted = notificationAccess,
                statusLabel = if (notificationAccess) "Granted" else "Not granted"
            ),
            PermissionStatusItem(
                id = "microphone",
                title = "Microphone",
                rationale = "Enables hands-free voice instructions in the Agent composer.",
                isGranted = micGranted,
                statusLabel = if (micGranted) "Granted" else "Not granted"
            ),
            PermissionStatusItem(
                id = "screen_capture",
                title = "Screen capture",
                rationale = "Optional MediaProjection fallback only when Accessibility tree lacks visual elements.",
                isGranted = false,
                statusLabel = "On-demand only"
            )
        )
    }

    fun openPermissionSettings(permissionId: String) {
        val intent = when (permissionId) {
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "notification" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(intent) }
            .onFailure {
                _messages.tryEmit("Unable to open system settings screen on this device.")
            }
    }

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.updateDarkMode(enabled)
    }

    fun setAutonomyLevel(level: AutonomyLevel) = viewModelScope.launch {
        preferencesRepository.updateAutonomyLevel(level)
    }

    fun setConfirmSensitive(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.updateConfirmSensitive(enabled)
    }

    fun setConfirmPurchases(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.updateConfirmPurchases(enabled)
    }

    fun setConfirmMessages(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.updateConfirmMessages(enabled)
    }

    fun setConfirmDestructive(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.updateConfirmDestructive(enabled)
    }

    fun togglePreferredShoppingApp(appName: String) = viewModelScope.launch {
        val current = uiState.value.settings.preferredShoppingApps.toMutableSet()
        if (!current.add(appName)) current.remove(appName)
        preferencesRepository.updatePreferredShoppingApps(current)
    }

    fun setPreferredBrands(brands: String) = viewModelScope.launch {
        preferencesRepository.updatePreferredBrands(brands)
    }

    fun setPreferredCategories(categories: String) = viewModelScope.launch {
        preferencesRepository.updatePreferredCategories(categories)
    }

    fun setPreferredLanguage(language: String) = viewModelScope.launch {
        preferencesRepository.updatePreferredLanguage(language)
    }

    fun setExecutionBehavior(behavior: ExecutionBehavior) = viewModelScope.launch {
        preferencesRepository.updateDefaultExecutionBehavior(behavior)
    }

    fun setScreenshotRetention(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.updateScreenshotRetention(enabled)
    }

    fun setLlmProvider(provider: LlmProvider) = viewModelScope.launch {
        preferencesRepository.updateLlmProvider(provider)
        if (provider == LlmProvider.GEMINI && uiState.value.settings.llmModel.startsWith("gpt-")) {
            preferencesRepository.updateLlmModel("gemini-2.5-flash")
        } else if (provider == LlmProvider.OPENAI_COMPATIBLE && uiState.value.settings.llmModel.startsWith("gemini-")) {
            preferencesRepository.updateLlmModel("gpt-4o-mini")
        }
    }

    fun setLlmApiKey(apiKey: String) = viewModelScope.launch {
        preferencesRepository.updateLlmApiKey(apiKey.trim())
    }

    fun setLlmModel(model: String) = viewModelScope.launch {
        preferencesRepository.updateLlmModel(model.trim())
    }

    fun setLlmBaseUrl(baseUrl: String) = viewModelScope.launch {
        preferencesRepository.updateLlmBaseUrl(baseUrl.trim())
    }

    fun clearTaskHistory() = viewModelScope.launch {
        agentRepository.clearTaskHistory()
        _messages.tryEmit("Task history cleared.")
    }

    fun clearAgentMemory() = viewModelScope.launch {
        preferencesRepository.clearAgentMemory()
        _messages.tryEmit("Agent personalization memory reset.")
    }
}
