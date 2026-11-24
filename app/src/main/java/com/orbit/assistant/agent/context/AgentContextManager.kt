package com.orbit.assistant.agent.context

import com.orbit.assistant.agent.observation.IncrementalScreenDiff
import com.orbit.assistant.agent.observation.NormalizedScreenObservation
import com.orbit.assistant.agent.observation.ScreenChangeState
import com.orbit.assistant.domain.state.AgentState
import javax.inject.Inject

data class AgentContextState(
    val originalGoal: String,
    val taskState: AgentState = AgentState.Planning,
    val currentApp: String = "",
    val currentScreenId: String = "",
    val lastExecutedAction: String? = null,
    val lastObservationSummary: String = "",
    val relevantPreviousActions: List<String> = emptyList(),
    val completedStepSummaries: List<String> = emptyList(),
    val recoveryAttempts: Int = 0,
    val repeatedScreenCount: Int = 0,
    val relevantPreferences: Map<String, String> = emptyMap()
)

class AgentContextManager @Inject constructor() {

    companion object {
        const val MAX_RECENT_ACTIONS = 5
        const val MAX_STEP_SUMMARIES = 6
    }

    private var state: AgentContextState? = null
    private var previousObservation: NormalizedScreenObservation? = null

    fun initialize(
        goal: String,
        preferences: Map<String, String> = emptyMap()
    ): AgentContextState {
        previousObservation = null
        val initial = AgentContextState(
            originalGoal = goal,
            taskState = AgentState.Planning,
            relevantPreferences = preferences
        )
        state = initial
        return initial
    }

    fun currentState(): AgentContextState =
        state ?: error("AgentContextManager must be initialized before reading state")

    fun previousObservation(): NormalizedScreenObservation? = previousObservation

    fun updateObservation(
        observation: NormalizedScreenObservation,
        diff: IncrementalScreenDiff
    ): AgentContextState {
        val curr = currentState()
        val repeated = if (
            diff.screenState == ScreenChangeState.UNCHANGED &&
            curr.currentScreenId == observation.screenId
        ) {
            curr.repeatedScreenCount + 1
        } else {
            0
        }

        val summary = buildString {
            append("${observation.packageName}/${observation.activityName} ")
            append("(${diff.screenState.name}, ${observation.elements.size} nodes, ")
            append("+${diff.addedElements.size}/-${diff.removedElements.size}/~${diff.changedElements.size})")
        }

        previousObservation = observation
        val updated = curr.copy(
            currentApp = observation.packageName,
            currentScreenId = observation.screenId,
            lastObservationSummary = summary,
            repeatedScreenCount = repeated
        )
        state = updated
        return updated
    }

    fun recordActionOutcome(
        actionLabel: String,
        succeeded: Boolean,
        taskState: AgentState = AgentState.Running
    ): AgentContextState {
        val curr = currentState()
        val entry = if (succeeded) "OK: $actionLabel" else "FAIL: $actionLabel"
        val combinedActions = curr.relevantPreviousActions + entry

        val (trimmedRecent, updatedSummaries) = if (combinedActions.size > MAX_RECENT_ACTIONS) {
            val overflow = combinedActions.first()
            val newSummaries = (curr.completedStepSummaries + overflow).takeLast(MAX_STEP_SUMMARIES)
            combinedActions.drop(1) to newSummaries
        } else {
            combinedActions to curr.completedStepSummaries
        }

        val updated = curr.copy(
            taskState = taskState,
            lastExecutedAction = actionLabel,
            relevantPreviousActions = trimmedRecent,
            completedStepSummaries = updatedSummaries,
            recoveryAttempts = if (succeeded) 0 else curr.recoveryAttempts + 1
        )
        state = updated
        return updated
    }

    fun toCompactContextPrompt(installedAppsCompact: String): String {
        val curr = currentState()
        return buildString {
            appendLine("GOAL: ${curr.originalGoal}")
            appendLine("STATE: ${curr.taskState::class.simpleName} | APP: ${curr.currentApp.ifBlank { "none" }} | SCREEN: ${curr.currentScreenId.ifBlank { "none" }}")
            if (curr.relevantPreferences.isNotEmpty()) {
                val prefs = curr.relevantPreferences.entries.joinToString("; ") { "${it.key}=${it.value}" }
                appendLine("PREFS: $prefs")
            }
            if (curr.completedStepSummaries.isNotEmpty()) {
                appendLine("COMPLETED_SUMMARY: ${curr.completedStepSummaries.joinToString(" -> ")}")
            }
            if (curr.relevantPreviousActions.isNotEmpty()) {
                appendLine("RECENT_ACTIONS:")
                curr.relevantPreviousActions.forEachIndexed { idx, act ->
                    appendLine("${idx + 1}. $act")
                }
            }
            curr.lastExecutedAction?.let { appendLine("LAST_ACTION: $it") }
            if (curr.recoveryAttempts > 0 || curr.repeatedScreenCount > 0) {
                appendLine("RECOVERY_ATTEMPTS=${curr.recoveryAttempts} REPEATED_SCREEN=${curr.repeatedScreenCount}")
            }
            if (installedAppsCompact.isNotBlank()) {
                appendLine("APPS:")
                appendLine(installedAppsCompact)
            }
        }.trim()
    }
}
