package com.orbit.assistant.agent.automation

sealed class AutomationAction {
    data class Tap(val x: Int, val y: Int, val targetLabel: String? = null) : AutomationAction()
    data class LongPress(val x: Int, val y: Int, val durationMs: Long = 600L) : AutomationAction()
    data class TypeText(val text: String, val fieldHint: String? = null) : AutomationAction()
    data class Scroll(val direction: ScrollDirection, val distancePx: Int = 500) : AutomationAction()
    data object Back : AutomationAction()
    data class LaunchApp(val packageName: String, val appName: String) : AutomationAction()
    data class Wait(val durationMs: Long) : AutomationAction()
    data class Screenshot(val tag: String) : AutomationAction()
}

enum class ScrollDirection {
    UP, DOWN, LEFT, RIGHT
}

data class ScreenObservation(
    val packageName: String,
    val screenName: String,
    val summary: String,
    val isSimulated: Boolean = true,
    val detectedElements: List<String> = emptyList()
)

data class ActionExecutionResult(
    val success: Boolean,
    val message: String,
    val isSimulated: Boolean = true
)
