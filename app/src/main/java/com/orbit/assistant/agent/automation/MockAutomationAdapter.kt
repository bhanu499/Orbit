package com.orbit.assistant.agent.automation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simulated automation adapter for Demo Mode.
 * Clearly marks all observations and actions as simulated; does not control third-party apps.
 */
@Singleton
class MockAutomationAdapter @Inject constructor() : AgentAutomationService {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    override val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    override suspend fun connect(): Boolean {
        delay(120)
        _isConnected.value = true
        _isPaused.value = false
        return true
    }

    override suspend fun disconnect() {
        _isConnected.value = false
        _isPaused.value = false
    }

    override suspend fun observeScreen(packageName: String, contextHint: String): ScreenObservation {
        delay(160)
        return ScreenObservation(
            packageName = packageName,
            screenName = contextHint,
            summary = "Simulated screen observation for $contextHint ($packageName)",
            isSimulated = true,
            detectedElements = listOf("Search bar", "Product list", "Cart summary")
        )
    }

    override suspend fun executeAction(action: AutomationAction): ActionExecutionResult {
        delay(140)
        val desc = when (action) {
            is AutomationAction.Tap -> "Simulated tap on ${action.targetLabel ?: "(${action.x}, ${action.y})"}"
            is AutomationAction.LongPress -> "Simulated long press at (${action.x}, ${action.y})"
            is AutomationAction.TypeText -> "Simulated input '${action.text}'"
            is AutomationAction.Scroll -> "Simulated scroll ${action.direction.name.lowercase()}"
            AutomationAction.Back -> "Simulated back navigation"
            is AutomationAction.LaunchApp -> "Simulated launch flow for ${action.appName}"
            is AutomationAction.Wait -> "Waited ${action.durationMs}ms"
            is AutomationAction.Screenshot -> "Simulated frame capture (${action.tag})"
        }
        return ActionExecutionResult(success = true, message = desc, isSimulated = true)
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
