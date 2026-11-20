package com.orbit.assistant.agent.runtime

import com.orbit.assistant.agent.appdiscovery.AppDiscoveryManager
import com.orbit.assistant.agent.automation.AccessibilityAutomationAdapter
import com.orbit.assistant.agent.automation.AutomationAction
import com.orbit.assistant.agent.automation.ScrollDirection
import com.orbit.assistant.data.preferences.OrbitSettings
import com.orbit.assistant.data.remote.LlmAgentClient
import com.orbit.assistant.domain.models.AgentEvent
import com.orbit.assistant.domain.models.ApprovalDecision
import com.orbit.assistant.domain.models.ApprovalDecisionType
import com.orbit.assistant.domain.models.ApprovalHistoryEntry
import com.orbit.assistant.domain.models.ApprovalRequest
import com.orbit.assistant.domain.models.EventStatus
import com.orbit.assistant.domain.models.EventType
import com.orbit.assistant.domain.models.Task
import com.orbit.assistant.domain.models.TaskResult
import com.orbit.assistant.domain.state.AgentState
import com.orbit.assistant.domain.state.AgentStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveAgentRuntime @Inject constructor(
    private val accessibilityAdapter: AccessibilityAutomationAdapter,
    private val llmClient: LlmAgentClient,
    private val appDiscoveryManager: AppDiscoveryManager
) {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = mutableMapOf<String, Job>()
    private val taskStates = mutableMapOf<String, MutableStateFlow<Task>>()
    private val pauseFlags = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val interventionFlags = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val approvalFlags = mutableMapOf<String, MutableStateFlow<Boolean>>()

    fun isManaging(taskId: String): Boolean = taskStates.containsKey(taskId)

    fun startTask(
        initialTask: Task,
        settings: OrbitSettings,
        onPersist: suspend (Task) -> Unit
    ) {
        val flow = MutableStateFlow(initialTask.copy(status = AgentState.Planning))
        taskStates[initialTask.taskId] = flow
        pauseFlags[initialTask.taskId] = MutableStateFlow(false)
        interventionFlags[initialTask.taskId] = MutableStateFlow(false)
        approvalFlags[initialTask.taskId] = MutableStateFlow(false)

        val job = runtimeScope.launch {
            val startTime = initialTask.createdAt
            val events = mutableListOf<AgentEvent>()

            if (!accessibilityAdapter.connect()) {
                val errEvent = AgentEvent(
                    eventId = "evt_${UUID.randomUUID().toString().take(8)}",
                    taskId = initialTask.taskId,
                    timestamp = System.currentTimeMillis(),
                    eventType = EventType.UserInterventionRequired(
                        reason = "Orbit Accessibility Service is not enabled",
                        suggestedAction = "Enable Orbit in Settings → Accessibility → Downloaded apps, then retry."
                    ),
                    title = "Accessibility Service required",
                    description = "Turn on Orbit Accessibility Service on your device so Orbit can read UI trees and tap elements.",
                    status = EventStatus.FAILED
                )
                events.add(errEvent)
                updateTask(initialTask.taskId, onPersist) {
                    it.copy(
                        status = AgentState.Failed,
                        events = events,
                        error = "Orbit Accessibility Service is not enabled.",
                        updatedAt = System.currentTimeMillis(),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
                return@launch
            }

            updateTask(initialTask.taskId, onPersist) {
                it.copy(
                    status = AgentStateMachine.transition(it.status, AgentState.Running),
                    updatedAt = System.currentTimeMillis()
                )
            }

            val installedApps = appDiscoveryManager.discoverLaunchableApps()
            val appsText = installedApps.take(40).joinToString("\n") {
                "- ${it.appName} (${it.packageName})"
            }
            val historyLines = mutableListOf<String>()
            val maxSteps = 18

            for (stepIndex in 1..maxSteps) {
                waitIfPausedOrBlocked(initialTask.taskId)

                val observation = accessibilityAdapter.observeScreen("unknown", "Foreground screen")
                val uiNodesText = if (observation.detectedElements.isEmpty()) {
                    "(no visible UI nodes captured)"
                } else {
                    observation.detectedElements.joinToString("\n")
                }

                val thinkingId = "evt_${initialTask.taskId}_$stepIndex"
                val thinkingEvent = AgentEvent(
                    eventId = thinkingId,
                    taskId = initialTask.taskId,
                    timestamp = System.currentTimeMillis(),
                    eventType = EventType.Thinking,
                    title = "Step $stepIndex: Planning next action",
                    description = "Observing ${observation.packageName} (${observation.detectedElements.size} UI nodes)",
                    status = EventStatus.IN_PROGRESS
                )
                events.add(thinkingEvent)
                updateTask(initialTask.taskId, onPersist) {
                    it.copy(
                        events = events.toList(),
                        updatedAt = System.currentTimeMillis(),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }

                val planResult = llmClient.planNextStep(
                    provider = settings.llmProvider,
                    apiKey = settings.llmApiKey,
                    model = settings.llmModel,
                    baseUrl = settings.llmBaseUrl,
                    userGoal = initialTask.userPrompt,
                    currentPackage = observation.packageName,
                    uiNodesText = uiNodesText,
                    installedAppsText = appsText,
                    historyText = historyLines.takeLast(8).joinToString("\n").ifBlank { "None yet" }
                )

                val step = planResult.getOrElse { err ->
                    events[events.lastIndex] = thinkingEvent.copy(
                        eventType = EventType.Error("LLM_ERROR", true),
                        title = "LLM request failed",
                        description = err.localizedMessage ?: "Unknown error",
                        status = EventStatus.FAILED
                    )
                    updateTask(initialTask.taskId, onPersist) {
                        it.copy(
                            status = AgentState.Failed,
                            events = events.toList(),
                            error = err.localizedMessage,
                            updatedAt = System.currentTimeMillis(),
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    }
                    accessibilityAdapter.disconnect()
                    return@launch
                }

                historyLines.add("$stepIndex. ${step.action}(node=${step.nodeIndex}, text=${step.text}, pkg=${step.packageName}) -> ${step.thought}")

                when (step.action.lowercase()) {
                    "launch_app" -> {
                        val pkg = step.packageName.orEmpty()
                        val appName = installedApps.firstOrNull { it.packageName == pkg }?.appName ?: pkg
                        val res = accessibilityAdapter.executeAction(AutomationAction.LaunchApp(pkg, appName))
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.AppLaunched(pkg, appName),
                            title = "Opening $appName",
                            description = "${step.thought}\n${res.message}",
                            status = if (res.success) EventStatus.COMPLETED else EventStatus.FAILED
                        )
                        delay(1600L)
                    }
                    "tap" -> {
                        val res = accessibilityAdapter.executeAction(
                            AutomationAction.Tap(0, 0, step.nodeIndex?.toString())
                        )
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.ScreenObserved(observation.packageName),
                            title = "Tap node #${step.nodeIndex ?: "?"}",
                            description = "${step.thought}\n${res.message}",
                            status = if (res.success) EventStatus.COMPLETED else EventStatus.FAILED
                        )
                        delay(950L)
                    }
                    "type" -> {
                        val text = step.text.orEmpty()
                        val res = accessibilityAdapter.executeAction(
                            AutomationAction.TypeText(text, step.nodeIndex?.toString())
                        )
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.SearchPerformed(text, observation.packageName),
                            title = "Typed '$text'",
                            description = "${step.thought}\n${res.message}",
                            status = if (res.success) EventStatus.COMPLETED else EventStatus.FAILED
                        )
                        delay(1100L)
                    }
                    "scroll" -> {
                        val dir = when (step.direction?.lowercase()) {
                            "up" -> ScrollDirection.UP
                            "left" -> ScrollDirection.LEFT
                            "right" -> ScrollDirection.RIGHT
                            else -> ScrollDirection.DOWN
                        }
                        val res = accessibilityAdapter.executeAction(AutomationAction.Scroll(dir))
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.ScreenObserved(observation.packageName),
                            title = "Scrolled ${dir.name.lowercase()}",
                            description = "${step.thought}\n${res.message}",
                            status = if (res.success) EventStatus.COMPLETED else EventStatus.FAILED
                        )
                        delay(900L)
                    }
                    "back" -> {
                        accessibilityAdapter.executeAction(AutomationAction.Back)
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.ScreenObserved(observation.packageName),
                            title = "Navigated back",
                            description = step.thought,
                            status = EventStatus.COMPLETED
                        )
                        delay(800L)
                    }
                    "wait" -> {
                        accessibilityAdapter.executeAction(AutomationAction.Wait(1500L))
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.Thinking,
                            title = "Waiting for screen",
                            description = step.thought,
                            status = EventStatus.COMPLETED
                        )
                    }
                    "needs_user_intervention" -> {
                        val reason = step.summary ?: step.thought
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.UserInterventionRequired(reason, "Complete this step on screen, then tap Continue."),
                            title = "Needs your attention",
                            description = reason,
                            status = EventStatus.NEEDS_ATTENTION
                        )
                        interventionFlags[initialTask.taskId]?.value = true
                        updateTask(initialTask.taskId, onPersist) {
                            it.copy(
                                status = AgentState.NeedsUserIntervention,
                                events = events.toList(),
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                        waitIfPausedOrBlocked(initialTask.taskId)
                        events[events.lastIndex] = events.last().copy(status = EventStatus.COMPLETED)
                        updateTask(initialTask.taskId, onPersist) {
                            it.copy(status = AgentState.Running, events = events.toList())
                        }
                    }
                    "request_approval" -> {
                        val summary = step.summary ?: step.thought
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.Verification("Human Approval"),
                            title = "Awaiting your approval",
                            description = summary,
                            status = EventStatus.NEEDS_ATTENTION
                        )
                        val appr = ApprovalRequest(
                            approvalId = "appr_${UUID.randomUUID().toString().take(8)}",
                            title = "Approve next step",
                            subtitle = summary,
                            shoppingData = null
                        )
                        approvalFlags[initialTask.taskId]?.value = true
                        updateTask(initialTask.taskId, onPersist) {
                            it.copy(
                                status = AgentState.WaitingForApproval,
                                approvalRequired = appr,
                                events = events.toList(),
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                        waitIfPausedOrBlocked(initialTask.taskId)
                    }
                    else -> {
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.Verification("Task Complete"),
                            title = "Completed",
                            description = step.summary ?: step.thought,
                            status = EventStatus.COMPLETED
                        )
                        updateTask(initialTask.taskId, onPersist) {
                            it.copy(
                                status = AgentState.Completed,
                                events = events.toList(),
                                result = TaskResult(
                                    summaryMarkdown = step.summary ?: step.thought
                                ),
                                updatedAt = System.currentTimeMillis(),
                                durationMs = System.currentTimeMillis() - startTime
                            )
                        }
                        accessibilityAdapter.disconnect()
                        return@launch
                    }
                }

                updateTask(initialTask.taskId, onPersist) {
                    it.copy(
                        events = events.toList(),
                        updatedAt = System.currentTimeMillis(),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            updateTask(initialTask.taskId, onPersist) {
                it.copy(
                    status = AgentState.Completed,
                    result = TaskResult(summaryMarkdown = "Reached step limit ($maxSteps steps)."),
                    updatedAt = System.currentTimeMillis(),
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
            accessibilityAdapter.disconnect()
        }

        activeJobs[initialTask.taskId] = job
    }

    suspend fun pause(taskId: String, onPersist: suspend (Task) -> Unit) {
        pauseFlags[taskId]?.value = true
        accessibilityAdapter.pause()
        updateTask(taskId, onPersist) {
            it.copy(status = AgentState.Paused, updatedAt = System.currentTimeMillis())
        }
    }

    suspend fun resume(taskId: String, onPersist: suspend (Task) -> Unit) {
        pauseFlags[taskId]?.value = false
        accessibilityAdapter.resume()
        updateTask(taskId, onPersist) {
            it.copy(status = AgentState.Running, updatedAt = System.currentTimeMillis())
        }
    }

    suspend fun continueAfterIntervention(taskId: String, onPersist: suspend (Task) -> Unit) {
        interventionFlags[taskId]?.value = false
        pauseFlags[taskId]?.value = false
        updateTask(taskId, onPersist) {
            it.copy(status = AgentState.Running, updatedAt = System.currentTimeMillis())
        }
    }

    suspend fun cancel(taskId: String, onPersist: suspend (Task) -> Unit) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        accessibilityAdapter.stop()
        updateTask(taskId, onPersist) {
            it.copy(
                status = AgentState.Cancelled,
                updatedAt = System.currentTimeMillis(),
                durationMs = System.currentTimeMillis() - it.createdAt
            )
        }
    }

    suspend fun submitApproval(taskId: String, decision: ApprovalDecision, onPersist: suspend (Task) -> Unit) {
        approvalFlags[taskId]?.value = false
        val now = System.currentTimeMillis()
        updateTask(taskId, onPersist) { current ->
            val entry = ApprovalHistoryEntry(
                approvalId = decision.approvalId,
                title = current.approvalRequired?.title ?: "Action Approval",
                decision = decision.decision,
                timestamp = now,
                summary = decision.note ?: decision.decision.name
            )
            if (decision.decision == ApprovalDecisionType.REJECTED) {
                activeJobs[taskId]?.cancel()
                current.copy(
                    status = AgentState.Cancelled,
                    approvalRequired = null,
                    approvalHistory = current.approvalHistory + entry,
                    updatedAt = now
                )
            } else {
                current.copy(
                    status = AgentState.Running,
                    approvalRequired = null,
                    approvalHistory = current.approvalHistory + entry,
                    updatedAt = now
                )
            }
        }
    }

    private suspend fun waitIfPausedOrBlocked(taskId: String) {
        while (
            pauseFlags[taskId]?.value == true ||
            interventionFlags[taskId]?.value == true ||
            approvalFlags[taskId]?.value == true
        ) {
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
