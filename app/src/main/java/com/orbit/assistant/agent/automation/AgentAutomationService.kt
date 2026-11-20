package com.orbit.assistant.agent.automation

import kotlinx.coroutines.flow.StateFlow

/**
 * Modular automation interface designed for future AccessibilityService,
 * UIAutomator, or privileged device-automation adapters without coupling to UI.
 */
interface AgentAutomationService {
    val isConnected: StateFlow<Boolean>
    val isPaused: StateFlow<Boolean>

    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun observeScreen(packageName: String, contextHint: String): ScreenObservation
    suspend fun executeAction(action: AutomationAction): ActionExecutionResult
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
}
