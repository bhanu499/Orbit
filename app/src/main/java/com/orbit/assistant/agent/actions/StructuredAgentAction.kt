package com.orbit.assistant.agent.actions

import com.orbit.assistant.agent.observation.NormalizedScreenObservation
import kotlinx.serialization.Serializable

@Serializable
enum class AgentActionType {
    TAP,
    TYPE_TEXT,
    SCROLL,
    BACK,
    LAUNCH_APP,
    WAIT,
    ASK_USER,
    COMPLETE
}

@Serializable
data class AgentActionPlan(
    val thought: String,
    val confidence: Float = 0.8f,
    val action: AgentActionType,
    val nodeId: Int? = null,
    val text: String? = null,
    val direction: String? = null,
    val packageName: String? = null,
    val summary: String? = null
)

sealed class ActionValidationResult {
    data class Valid(val plan: AgentActionPlan) : ActionValidationResult()
    data class Invalid(val reason: String) : ActionValidationResult()
}

object AgentActionValidator {

    private val validScrollDirections = setOf("up", "down", "left", "right")

    fun validate(
        plan: AgentActionPlan,
        observation: NormalizedScreenObservation?
    ): ActionValidationResult {
        if (plan.confidence !in 0.0f..1.0f) {
            return ActionValidationResult.Invalid("confidence must be between 0.0 and 1.0")
        }

        return when (plan.action) {
            AgentActionType.TAP -> {
                val id = plan.nodeId ?: return ActionValidationResult.Invalid("TAP requires a non-null nodeId")
                if (observation != null && observation.elements.none { it.id == id }) {
                    return ActionValidationResult.Invalid("TAP nodeId=$id does not exist in current normalized UI elements")
                }
                ActionValidationResult.Valid(plan)
            }
            AgentActionType.TYPE_TEXT -> {
                if (plan.text.isNullOrBlank()) {
                    return ActionValidationResult.Invalid("TYPE_TEXT requires non-blank text")
                }
                val id = plan.nodeId
                if (id != null && observation != null && observation.elements.none { it.id == id }) {
                    return ActionValidationResult.Invalid("TYPE_TEXT nodeId=$id does not exist in current normalized UI elements")
                }
                ActionValidationResult.Valid(plan)
            }
            AgentActionType.SCROLL -> {
                val dir = plan.direction?.lowercase() ?: "down"
                if (dir !in validScrollDirections) {
                    return ActionValidationResult.Invalid("SCROLL direction '$dir' must be one of $validScrollDirections")
                }
                ActionValidationResult.Valid(plan.copy(direction = dir))
            }
            AgentActionType.LAUNCH_APP -> {
                if (plan.packageName.isNullOrBlank()) {
                    return ActionValidationResult.Invalid("LAUNCH_APP requires a non-blank packageName")
                }
                ActionValidationResult.Valid(plan)
            }
            AgentActionType.ASK_USER, AgentActionType.COMPLETE -> {
                if (plan.summary.isNullOrBlank() && plan.thought.isBlank()) {
                    return ActionValidationResult.Invalid("${plan.action} requires a non-blank summary")
                }
                ActionValidationResult.Valid(plan)
            }
            AgentActionType.BACK, AgentActionType.WAIT -> ActionValidationResult.Valid(plan)
        }
    }
}
