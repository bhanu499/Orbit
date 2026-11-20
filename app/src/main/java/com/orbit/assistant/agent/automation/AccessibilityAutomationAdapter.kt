package com.orbit.assistant.agent.automation

import com.orbit.assistant.agent.appdiscovery.AppLaunchResult
import com.orbit.assistant.agent.appdiscovery.AppLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityAutomationAdapter @Inject constructor(
    private val appLauncher: AppLauncher
) : AgentAutomationService {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    override val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    fun isAccessibilityReady(): Boolean = OrbitAccessibilityService.instance.value != null

    override suspend fun connect(): Boolean {
        val ready = isAccessibilityReady()
        _isConnected.value = ready
        _isPaused.value = false
        return ready
    }

    override suspend fun disconnect() {
        _isConnected.value = false
        _isPaused.value = false
    }

    override suspend fun observeScreen(packageName: String, contextHint: String): ScreenObservation {
        val service = OrbitAccessibilityService.instance.value
            ?: return ScreenObservation(
                packageName = packageName,
                screenName = "Accessibility disconnected",
                summary = "Orbit Accessibility Service is not enabled.",
                isSimulated = false
            )
        val (activePkg, nodes) = service.captureUiTree()
        return ScreenObservation(
            packageName = activePkg.ifBlank { packageName },
            screenName = activePkg.ifBlank { contextHint },
            summary = "Captured ${nodes.size} UI nodes from $activePkg",
            isSimulated = false,
            detectedElements = nodes.map { it.toCompactLine() }
        )
    }

    override suspend fun executeAction(action: AutomationAction): ActionExecutionResult {
        val service = OrbitAccessibilityService.instance.value
        if (service == null && action !is AutomationAction.LaunchApp && action !is AutomationAction.Wait) {
            return ActionExecutionResult(
                success = false,
                message = "Orbit Accessibility Service is not active.",
                isSimulated = false
            )
        }

        return when (action) {
            is AutomationAction.LaunchApp -> {
                when (val res = appLauncher.launchApp(action.packageName)) {
                    is AppLaunchResult.Launched ->
                        ActionExecutionResult(true, "Launched ${res.appName}", isSimulated = false)
                    is AppLaunchResult.AppNotInstalled ->
                        ActionExecutionResult(false, "${action.appName} (${action.packageName}) is not installed.", isSimulated = false)
                    is AppLaunchResult.LaunchIntentUnavailable ->
                        ActionExecutionResult(false, "No launch intent for ${action.packageName}.", isSimulated = false)
                    is AppLaunchResult.CannotLaunch ->
                        ActionExecutionResult(false, "Cannot launch ${action.appName}: ${res.reason}", isSimulated = false)
                }
            }
            is AutomationAction.Tap -> {
                val nodeIdx = action.targetLabel?.toIntOrNull()
                val ok = if (nodeIdx != null) {
                    service!!.clickNode(nodeIdx)
                } else {
                    service!!.tapAt(action.x.toFloat(), action.y.toFloat())
                }
                ActionExecutionResult(ok, if (ok) "Tapped node ${action.targetLabel ?: "(${action.x},${action.y})"}" else "Tap failed", isSimulated = false)
            }
            is AutomationAction.TypeText -> {
                val nodeIdx = action.fieldHint?.toIntOrNull()
                val ok = service!!.typeIntoNode(nodeIdx, action.text)
                ActionExecutionResult(ok, if (ok) "Entered text '${action.text}'" else "Could not find editable field", isSimulated = false)
            }
            is AutomationAction.Scroll -> {
                val ok = service!!.scroll(action.direction)
                ActionExecutionResult(ok, if (ok) "Scrolled ${action.direction.name.lowercase()}" else "Scroll failed", isSimulated = false)
            }
            AutomationAction.Back -> {
                val ok = service!!.pressBack()
                ActionExecutionResult(ok, if (ok) "Pressed Back" else "Back failed", isSimulated = false)
            }
            is AutomationAction.Wait -> {
                delay(action.durationMs.coerceIn(200L, 5000L))
                ActionExecutionResult(true, "Waited ${action.durationMs}ms", isSimulated = false)
            }
            is AutomationAction.LongPress -> {
                val ok = service!!.tapAt(action.x.toFloat(), action.y.toFloat())
                ActionExecutionResult(ok, "Tap fallback at (${action.x},${action.y})", isSimulated = false)
            }
            is AutomationAction.Screenshot -> {
                ActionExecutionResult(false, "Screenshot capture is not enabled; using Accessibility tree.", isSimulated = false)
            }
        }
    }

    override suspend fun pause() {
        _isPaused.value = true
    }

    override suspend fun resume() {
        _isPaused.value = false
    }

    override suspend fun stop() {
        _isPaused.value = false
        _isConnected.value = false
    }
}
