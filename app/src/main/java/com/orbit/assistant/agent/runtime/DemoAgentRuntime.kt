package com.orbit.assistant.agent.runtime

import com.orbit.assistant.agent.events.DemoScenario
import com.orbit.assistant.agent.events.DemoScenarios

import com.orbit.assistant.agent.automation.AgentAutomationService
import com.orbit.assistant.agent.automation.AutomationAction
import com.orbit.assistant.domain.models.AgentEvent
import com.orbit.assistant.domain.models.ApprovalDecision
import com.orbit.assistant.domain.models.ApprovalDecisionType
import com.orbit.assistant.domain.models.ApprovalHistoryEntry
import com.orbit.assistant.domain.models.EventStatus
import com.orbit.assistant.domain.models.EventType
import com.orbit.assistant.domain.models.Task
import com.orbit.assistant.domain.state.AgentState
import com.orbit.assistant.domain.state.AgentStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoAgentRuntime @Inject constructor(
    private val automationService: AgentAutomationService
) {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = mutableMapOf<String, Job>()
    private val activeScenarios = mutableMapOf<String, DemoScenario>()
    private val taskStates = mutableMapOf<String, MutableStateFlow<Task>>()

    private val pauseFlags = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val interventionFlags = mutableMapOf<String, MutableStateFlow<Boolean>>()

    fun startTask(
        initialTask: Task,
        onPersist: suspend (Task) -> Unit
    ): StateFlow<Task> {
        val scenario = DemoScenarios.matchScenario(initialTask.userPrompt, initialTask.taskId)
        activeScenarios[initialTask.taskId] = scenario

        val flow = MutableStateFlow(
            initialTask.copy(
                status = AgentState.Planning,
                selectedApps = scenario.selectedApps
            )
        )
        taskStates[initialTask.taskId] = flow
        pauseFlags[initialTask.taskId] = MutableStateFlow(false)
        interventionFlags[initialTask.taskId] = MutableStateFlow(false)

        val job = runtimeScope.launch {
            automationService.connect()
            val startTime = initialTask.createdAt

            updateTask(initialTask.taskId, onPersist) { current ->
                current.copy(
                    status = AgentStateMachine.transition(current.status, AgentState.Running),
                    updatedAt = System.currentTimeMillis()
                )
            }

            val events = mutableListOf<AgentEvent>()

            for ((index, step) in scenario.steps.withIndex()) {
                waitIfPausedOrIntervening(initialTask.taskId)

                val eventId = "evt_${initialTask.taskId}_$index"
                val inProgressEvent = AgentEvent(
                    eventId = eventId,
                    taskId = initialTask.taskId,
                    timestamp = System.currentTimeMillis(),
                    eventType = step.eventType,
                    title = step.title,
                    description = step.description,
                    status = if (step.pausesForIntervention) EventStatus.NEEDS_ATTENTION else EventStatus.IN_PROGRESS,
                    metadata = step.metadata
                )
                events.add(inProgressEvent)

                updateTask(initialTask.taskId, onPersist) { current ->
                    current.copy(
                        events = events.toList(),
                        updatedAt = System.currentTimeMillis(),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }

                // Exercise automation abstraction
                when (val type = step.eventType) {
                    is EventType.AppLaunched -> automationService.executeAction(
                        AutomationAction.LaunchApp(type.packageName, type.appName)
                    )
                    is EventType.SearchPerformed -> automationService.executeAction(
                        AutomationAction.TypeText(type.query, "Search bar")
                    )
                    is EventType.ScreenObserved -> automationService.observeScreen(
                        packageName = scenario.selectedApps.firstOrNull() ?: "orbit.demo",
                        contextHint = type.screenSummary
                    )
                    else -> automationService.executeAction(AutomationAction.Wait(step.delayMs))
                }

                delay(step.delayMs)
                waitIfPausedOrIntervening(initialTask.taskId)

                if (step.pausesForIntervention) {
                    interventionFlags[initialTask.taskId]?.value = true
                    updateTask(initialTask.taskId, onPersist) { current ->
                        current.copy(
                            status = AgentStateMachine.transition(current.status, AgentState.NeedsUserIntervention),
                            updatedAt = System.currentTimeMillis(),
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    }
                    waitIfPausedOrIntervening(initialTask.taskId)
                    updateTask(initialTask.taskId, onPersist) { current ->
                        current.copy(
                            status = AgentStateMachine.transition(current.status, AgentState.Running),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }

                val completedEvent = inProgressEvent.copy(
                    status = EventStatus.COMPLETED,
                    timestamp = System.currentTimeMillis()
                )
                events[events.lastIndex] = completedEvent

                updateTask(initialTask.taskId, onPersist) { current ->
                    current.copy(
                        events = events.toList(),
                        updatedAt = System.currentTimeMillis(),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            if (scenario.approvalRequest != null) {
                updateTask(initialTask.taskId, onPersist) { current ->
                    current.copy(
                        status = AgentStateMachine.transition(current.status, AgentState.WaitingForApproval),
                        approvalRequired = scenario.approvalRequest,
                        updatedAt = System.currentTimeMillis(),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            } else {
                updateTask(initialTask.taskId, onPersist) { current ->
                    current.copy(
                        status = AgentStateMachine.transition(current.status, AgentState.Completed),
                        result = scenario.finalResult,
                        updatedAt = System.currentTimeMillis(),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
                automationService.disconnect()
            }
        }

        activeJobs[initialTask.taskId] = job
        return flow.asStateFlow()
    }

    suspend fun pause(taskId: String, onPersist: suspend (Task) -> Unit) {
        pauseFlags[taskId]?.value = true
        automationService.pause()
        updateTask(taskId, onPersist) { current ->
            current.copy(
                status = AgentStateMachine.transition(current.status, AgentState.Paused),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun resume(taskId: String, onPersist: suspend (Task) -> Unit) {
        pauseFlags[taskId]?.value = false
        automationService.resume()
        updateTask(taskId, onPersist) { current ->
            current.copy(
                status = AgentStateMachine.transition(current.status, AgentState.Running),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun continueAfterIntervention(taskId: String, onPersist: suspend (Task) -> Unit) {
        interventionFlags[taskId]?.value = false
        pauseFlags[taskId]?.value = false
        updateTask(taskId, onPersist) { current ->
            current.copy(
                status = AgentStateMachine.transition(current.status, AgentState.Running),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun cancel(taskId: String, onPersist: suspend (Task) -> Unit) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        automationService.stop()
        updateTask(taskId, onPersist) { current ->
            val updatedEvents = current.events.map {
                if (it.status == EventStatus.IN_PROGRESS || it.status == EventStatus.NEEDS_ATTENTION) {
                    it.copy(status = EventStatus.FAILED)
                } else it
            }
            current.copy(
                status = AgentStateMachine.transition(current.status, AgentState.Cancelled),
                events = updatedEvents,
                updatedAt = System.currentTimeMillis(),
                durationMs = System.currentTimeMillis() - current.createdAt
            )
        }
    }

    suspend fun updateBasketItemQuantity(
        taskId: String,
        itemId: String,
        delta: Int,
        onPersist: suspend (Task) -> Unit
    ) {
        updateTask(taskId, onPersist) { current ->
            val approval = current.approvalRequired ?: return@updateTask current
            val shopping = approval.shoppingData ?: return@updateTask current
            val updatedItems = shopping.items.mapNotNull { item ->
                if (item.itemId == itemId) {
                    val newQty = (item.quantity + delta).coerceAtLeast(0)
                    if (newQty == 0) null else item.copy(quantity = newQty)
                } else item
            }
            val newSummary = DemoScenarios.recalculateSummary(
                items = updatedItems,
                deliveryFee = shopping.summary.deliveryFee,
                handlingFee = shopping.summary.handlingFee,
                couponSavings = shopping.summary.couponSavings
            )
            current.copy(
                approvalRequired = approval.copy(
                    shoppingData = shopping.copy(
                        items = updatedItems,
                        summary = newSummary
                    )
                ),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun submitApproval(
        taskId: String,
        decision: ApprovalDecision,
        onPersist: suspend (Task) -> Unit
    ) {
        val scenario = activeScenarios[taskId]
        val now = System.currentTimeMillis()
        updateTask(taskId, onPersist) { current ->
            val approvalTitle = current.approvalRequired?.title ?: "Action Approval"
            val entry = ApprovalHistoryEntry(
                approvalId = decision.approvalId,
                title = approvalTitle,
                decision = decision.decision,
                timestamp = now,
                summary = decision.note ?: when (decision.decision) {
                    ApprovalDecisionType.APPROVED -> "User authorized next step (checkout preparation only)."
                    ApprovalDecisionType.EDITED_AND_APPROVED -> "User edited basket and authorized next step."
                    ApprovalDecisionType.REJECTED -> "User rejected approval request."
                }
            )
            if (decision.decision == ApprovalDecisionType.REJECTED) {
                current.copy(
                    status = AgentStateMachine.transition(current.status, AgentState.Cancelled),
                    approvalRequired = null,
                    approvalHistory = current.approvalHistory + entry,
                    updatedAt = now,
                    durationMs = now - current.createdAt
                )
            } else {
                val finalEvent = AgentEvent(
                    eventId = "evt_${UUID.randomUUID().toString().take(8)}",
                    taskId = taskId,
                    timestamp = now,
                    eventType = EventType.Verification("User Authorization"),
                    title = "Approval confirmed",
                    description = "Authorized checkout preparation in ${current.approvalRequired?.shoppingData?.storeName ?: "app"}. Payment is never automated.",
                    status = EventStatus.COMPLETED
                )
                current.copy(
                    status = AgentStateMachine.transition(current.status, AgentState.Completed),
                    events = current.events + finalEvent,
                    approvalRequired = null,
                    approvalHistory = current.approvalHistory + entry,
                    result = scenario?.finalResult,
                    updatedAt = now,
                    durationMs = now - current.createdAt
                )
            }
        }
        automationService.disconnect()
    }

    private suspend fun waitIfPausedOrIntervening(taskId: String) {
        val pause = pauseFlags[taskId]
        val intervention = interventionFlags[taskId]
        while (pause?.value == true || intervention?.value == true) {
            delay(150L)
        }
    }

    private suspend fun updateTask(
        taskId: String,
        onPersist: suspend (Task) -> Unit,
        transform: (Task) -> Task
    ) {
        val flow = taskStates[taskId] ?: return
        val updated = transform(flow.value)
        flow.value = updated
        onPersist(updated)
    }
}
