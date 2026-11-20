package com.orbit.assistant.domain.state

import kotlinx.serialization.Serializable

@Serializable
sealed class AgentState {
    @Serializable data object Idle : AgentState()
    @Serializable data object Planning : AgentState()
    @Serializable data object Running : AgentState()
    @Serializable data object Paused : AgentState()
    @Serializable data object WaitingForApproval : AgentState()
    @Serializable data object NeedsUserIntervention : AgentState()
    @Serializable data object Completed : AgentState()
    @Serializable data object Failed : AgentState()
    @Serializable data object Cancelled : AgentState()

    val isActive: Boolean
        get() = this is Planning || this is Running || this is Paused ||
            this is WaitingForApproval || this is NeedsUserIntervention

    val isTerminal: Boolean
        get() = this is Completed || this is Failed || this is Cancelled
}

object AgentStateMachine {

    private val allowedTransitions: Map<Class<out AgentState>, Set<Class<out AgentState>>> = mapOf(
        AgentState.Idle::class.java to setOf(
            AgentState.Planning::class.java,
            AgentState.Running::class.java
        ),
        AgentState.Planning::class.java to setOf(
            AgentState.Running::class.java,
            AgentState.WaitingForApproval::class.java,
            AgentState.Failed::class.java,
            AgentState.Cancelled::class.java
        ),
        AgentState.Running::class.java to setOf(
            AgentState.Paused::class.java,
            AgentState.WaitingForApproval::class.java,
            AgentState.NeedsUserIntervention::class.java,
            AgentState.Completed::class.java,
            AgentState.Failed::class.java,
            AgentState.Cancelled::class.java
        ),
        AgentState.Paused::class.java to setOf(
            AgentState.Running::class.java,
            AgentState.Cancelled::class.java,
            AgentState.Failed::class.java
        ),
        AgentState.WaitingForApproval::class.java to setOf(
            AgentState.Running::class.java,
            AgentState.Completed::class.java,
            AgentState.Cancelled::class.java,
            AgentState.Failed::class.java
        ),
        AgentState.NeedsUserIntervention::class.java to setOf(
            AgentState.Running::class.java,
            AgentState.Paused::class.java,
            AgentState.Cancelled::class.java,
            AgentState.Failed::class.java
        ),
        AgentState.Completed::class.java to emptySet(),
        AgentState.Failed::class.java to setOf(AgentState.Running::class.java),
        AgentState.Cancelled::class.java to emptySet()
    )

    fun canTransition(from: AgentState, to: AgentState): Boolean {
        if (from::class.java == to::class.java) return true
        return allowedTransitions[from::class.java]?.contains(to::class.java) == true
    }

    fun transition(from: AgentState, to: AgentState): AgentState {
        return if (canTransition(from, to)) to else from
    }
}
