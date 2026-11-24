package com.orbit.assistant.agent.runtime

import com.orbit.assistant.BuildConfig
import com.orbit.assistant.agent.actions.ActionValidationResult
import com.orbit.assistant.agent.actions.AgentActionPlan
import com.orbit.assistant.agent.actions.AgentActionType
import com.orbit.assistant.agent.actions.AgentActionValidator
import com.orbit.assistant.agent.appdiscovery.AppDiscoveryManager
import com.orbit.assistant.agent.automation.AccessibilityAutomationAdapter
import com.orbit.assistant.agent.automation.AutomationAction
import com.orbit.assistant.agent.automation.ScrollDirection
import com.orbit.assistant.agent.context.AgentContextManager
import com.orbit.assistant.agent.llm.GeminiProvider
import com.orbit.assistant.agent.llm.GroqProvider
import com.orbit.assistant.agent.llm.LLMProviderConfig
import com.orbit.assistant.agent.llm.LLMRequest
import com.orbit.assistant.agent.llm.ModelRouter
import com.orbit.assistant.agent.llm.OpenAIProvider
import com.orbit.assistant.agent.llm.ProviderId
import com.orbit.assistant.agent.llm.RoutingPolicyConfig
import com.orbit.assistant.agent.llm.RoutingSignals
import com.orbit.assistant.agent.llm.TransientProviderException
import com.orbit.assistant.agent.observation.NormalizedScreenObservation
import com.orbit.assistant.agent.observation.ScreenObservationNormalizer
import com.orbit.assistant.data.preferences.OrbitSettings
import com.orbit.assistant.data.repository.TokenUsageRepository
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
    private val modelRouter: ModelRouter,
    private val geminiProvider: GeminiProvider,
    private val openAIProvider: OpenAIProvider,
    private val groqProvider: GroqProvider,
    private val normalizer: ScreenObservationNormalizer,
    private val contextManager: AgentContextManager,
    private val tokenUsageRepository: TokenUsageRepository,
    private val appDiscoveryManager: AppDiscoveryManager
) {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = mutableMapOf<String, Job>()
    private val taskStates = mutableMapOf<String, MutableStateFlow<Task>>()
    private val pauseFlags = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val interventionFlags = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val approvalFlags = mutableMapOf<String, MutableStateFlow<Boolean>>()

    fun isManaging(taskId: String): Boolean = taskStates.containsKey(taskId)

    fun configureProviders(settings: OrbitSettings) {
        val proxy = settings.proxyBaseUrl.ifBlank { BuildConfig.ORBIT_PROXY_BASE_URL }
        geminiProvider.config = LLMProviderConfig(
            apiKey = if (BuildConfig.DEBUG) settings.geminiDevApiKey.ifBlank { BuildConfig.DEV_GEMINI_API_KEY } else "",
            proxyBaseUrl = proxy
        )
        openAIProvider.config = LLMProviderConfig(
            apiKey = if (BuildConfig.DEBUG) settings.openAiDevApiKey.ifBlank { BuildConfig.DEV_OPENAI_API_KEY } else "",
            proxyBaseUrl = proxy
        )
        groqProvider.config = LLMProviderConfig(
            apiKey = if (BuildConfig.DEBUG) settings.groqDevApiKey.ifBlank { BuildConfig.DEV_GROQ_API_KEY } else "",
            proxyBaseUrl = proxy
        )
    }

    fun startTask(
        initialTask: Task,
        settings: OrbitSettings,
        onPersist: suspend (Task) -> Unit
    ) {
        configureProviders(settings)

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

            contextManager.initialize(
                goal = initialTask.userPrompt,
                preferences = mapOf(
                    "apps" to settings.preferredShoppingApps.joinToString(","),
                    "lang" to settings.preferredLanguage
                )
            )

            val installedApps = appDiscoveryManager.discoverLaunchableApps()
            val appsCompact = installedApps.take(25).joinToString(", ") {
                "${it.appName}=${it.packageName}"
            }

            val routingPolicy = RoutingPolicyConfig(
                primaryModel = settings.primaryModelOverride.ifBlank { RoutingPolicyConfig.DEFAULT_PRIMARY_MODEL },
                recoveryModel = settings.recoveryModelOverride.ifBlank { RoutingPolicyConfig.DEFAULT_RECOVERY_MODEL },
                classifierModel = settings.classifierModelOverride.ifBlank { RoutingPolicyConfig.DEFAULT_CLASSIFIER_MODEL }
            )

            var lastConfidence = 1.0f
            var lastErrorTransient = false
            val maxSteps = 18

            for (stepIndex in 1..maxSteps) {
                waitIfPausedOrBlocked(initialTask.taskId)

                val rawTree = accessibilityAdapter.observeRawTree()
                val prevObs = contextManager.previousObservation()
                val normalized = normalizer.normalize(rawTree, settings.maxObservationTokens)
                val diff = normalizer.compare(prevObs, normalized)
                val ctx = contextManager.updateObservation(normalized, diff)
                val screenPrompt = normalizer.toCompactPrompt(normalized, diff)

                val route = modelRouter.selectRoute(
                    signals = RoutingSignals(
                        isLightweightClassification = false,
                        consecutiveFailures = ctx.recoveryAttempts,
                        repeatedScreenCount = ctx.repeatedScreenCount,
                        lastConfidence = lastConfidence,
                        lastErrorWasTransient = lastErrorTransient
                    ),
                    policy = routingPolicy
                )

                val thinkingEvent = AgentEvent(
                    eventId = "evt_${initialTask.taskId}_$stepIndex",
                    taskId = initialTask.taskId,
                    timestamp = System.currentTimeMillis(),
                    eventType = EventType.Thinking,
                    title = "Step $stepIndex (${route.model})",
                    description = "Normalized ${normalized.elements.size}/${normalized.totalRawNodes} nodes (~${normalized.estimatedTokens} tok, ${diff.screenState.name})",
                    status = EventStatus.IN_PROGRESS,
                    metadata = mapOf(
                        "provider" to route.providerId.name,
                        "model" to route.model,
                        "tier" to route.tier.name
                    )
                )
                events.add(thinkingEvent)
                updateTask(initialTask.taskId, onPersist) {
                    it.copy(
                        events = events.toList(),
                        updatedAt = System.currentTimeMillis(),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }

                val planResult = requestValidatedPlan(
                    taskId = initialTask.taskId,
                    providerId = route.providerId,
                    model = route.model,
                    reason = route.reason,
                    contextPrompt = contextManager.toCompactContextPrompt(appsCompact),
                    screenPrompt = screenPrompt,
                    observation = normalized
                )

                val plan = planResult.getOrElse { err ->
                    lastErrorTransient = err is TransientProviderException
                    events[events.lastIndex] = thinkingEvent.copy(
                        eventType = EventType.Error("LLM_ERROR", lastErrorTransient),
                        title = "LLM step failed (${route.model})",
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

                lastConfidence = plan.confidence
                lastErrorTransient = false

                when (plan.action) {
                    AgentActionType.LAUNCH_APP -> {
                        val pkg = plan.packageName.orEmpty()
                        val appName = installedApps.firstOrNull { it.packageName == pkg }?.appName ?: pkg
                        val res = accessibilityAdapter.executeAction(AutomationAction.LaunchApp(pkg, appName))
                        contextManager.recordActionOutcome("LAUNCH_APP($pkg)", res.success)
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.AppLaunched(pkg, appName),
                            title = "Opening $appName",
                            description = "${plan.thought}\n${res.message}",
                            status = if (res.success) EventStatus.COMPLETED else EventStatus.FAILED
                        )
                        delay(1500L)
                    }
                    AgentActionType.TAP -> {
                        val res = accessibilityAdapter.executeAction(
                            AutomationAction.Tap(0, 0, plan.nodeId?.toString())
                        )
                        contextManager.recordActionOutcome("TAP(#${plan.nodeId})", res.success)
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.ScreenObserved(normalized.packageName),
                            title = "Tap node #${plan.nodeId}",
                            description = "${plan.thought}\n${res.message}",
                            status = if (res.success) EventStatus.COMPLETED else EventStatus.FAILED
                        )
                        delay(900L)
                    }
                    AgentActionType.TYPE_TEXT -> {
                        val text = plan.text.orEmpty()
                        val res = accessibilityAdapter.executeAction(
                            AutomationAction.TypeText(text, plan.nodeId?.toString())
                        )
                        contextManager.recordActionOutcome("TYPE_TEXT('$text')", res.success)
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.SearchPerformed(text, normalized.packageName),
                            title = "Typed '$text'",
                            description = "${plan.thought}\n${res.message}",
                            status = if (res.success) EventStatus.COMPLETED else EventStatus.FAILED
                        )
                        delay(1000L)
                    }
                    AgentActionType.SCROLL -> {
                        val dir = when (plan.direction?.lowercase()) {
                            "up" -> ScrollDirection.UP
                            "left" -> ScrollDirection.LEFT
                            "right" -> ScrollDirection.RIGHT
                            else -> ScrollDirection.DOWN
                        }
                        val res = accessibilityAdapter.executeAction(AutomationAction.Scroll(dir))
                        contextManager.recordActionOutcome("SCROLL(${dir.name})", res.success)
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.ScreenObserved(normalized.packageName),
                            title = "Scrolled ${dir.name.lowercase()}",
                            description = "${plan.thought}\n${res.message}",
                            status = if (res.success) EventStatus.COMPLETED else EventStatus.FAILED
                        )
                        delay(850L)
                    }
                    AgentActionType.BACK -> {
                        val res = accessibilityAdapter.executeAction(AutomationAction.Back)
                        contextManager.recordActionOutcome("BACK", res.success)
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.ScreenObserved(normalized.packageName),
                            title = "Navigated back",
                            description = plan.thought,
                            status = EventStatus.COMPLETED
                        )
                        delay(750L)
                    }
                    AgentActionType.WAIT -> {
                        accessibilityAdapter.executeAction(AutomationAction.Wait(1400L))
                        contextManager.recordActionOutcome("WAIT", true)
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.Thinking,
                            title = "Waiting for screen",
                            description = plan.thought,
                            status = EventStatus.COMPLETED
                        )
                    }
                    AgentActionType.ASK_USER -> {
                        val promptText = plan.summary ?: plan.thought
                        val isApproval = promptText.contains("checkout", ignoreCase = true) ||
                            promptText.contains("purchase", ignoreCase = true) ||
                            promptText.contains("approve", ignoreCase = true)

                        if (isApproval) {
                            events[events.lastIndex] = thinkingEvent.copy(
                                eventType = EventType.Verification("Human Approval"),
                                title = "Awaiting your approval",
                                description = promptText,
                                status = EventStatus.NEEDS_ATTENTION
                            )
                            val appr = ApprovalRequest(
                                approvalId = "appr_${UUID.randomUUID().toString().take(8)}",
                                title = "Approve next step",
                                subtitle = promptText,
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
                        } else {
                            events[events.lastIndex] = thinkingEvent.copy(
                                eventType = EventType.UserInterventionRequired(
                                    reason = promptText,
                                    suggestedAction = "Complete this step on screen, then tap Continue."
                                ),
                                title = "Needs your attention",
                                description = promptText,
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
                        }
                        waitIfPausedOrBlocked(initialTask.taskId)
                        contextManager.recordActionOutcome("USER_CONFIRMED", true)
                        events[events.lastIndex] = events.last().copy(status = EventStatus.COMPLETED)
                    }
                    AgentActionType.COMPLETE -> {
                        events[events.lastIndex] = thinkingEvent.copy(
                            eventType = EventType.Verification("Task Complete"),
                            title = "Completed",
                            description = plan.summary ?: plan.thought,
                            status = EventStatus.COMPLETED
                        )
                        updateTask(initialTask.taskId, onPersist) {
                            it.copy(
                                status = AgentState.Completed,
                                events = events.toList(),
                                result = TaskResult(summaryMarkdown = plan.summary ?: plan.thought),
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

    private suspend fun requestValidatedPlan(
        taskId: String,
        providerId: ProviderId,
        model: String,
        reason: String,
        contextPrompt: String,
        screenPrompt: String,
        observation: NormalizedScreenObservation
    ): Result<AgentActionPlan> {
        val provider = modelRouter.providerFor(providerId)
        val systemPrompt = """
            You are Orbit, an Android AI agent. Return ONLY valid JSON matching:
            {"thought":"...","confidence":0.0-1.0,"action":"TAP|TYPE_TEXT|SCROLL|BACK|LAUNCH_APP|WAIT|ASK_USER|COMPLETE","nodeId":int|null,"text":string|null,"direction":"up|down|left|right"|null,"packageName":string|null,"summary":string|null}
            Rules: Never complete payment. Use ASK_USER before checkout/purchase/login/OTP. Use nodeId from UI elements.
        """.trimIndent()

        var userPrompt = "$contextPrompt\n\nSCREEN:\n$screenPrompt"
        val maxValidationAttempts = 2

        for (attempt in 0..maxValidationAttempts) {
            val res = provider.generateStructured(
                request = LLMRequest(
                    model = model,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    taskId = taskId,
                    reason = if (attempt == 0) reason else "validation_repair_$attempt",
                    expectJson = true
                ),
                serializer = AgentActionPlan.serializer()
            )

            val structured = res.getOrElse { err ->
                if (attempt == maxValidationAttempts || err is TransientProviderException) {
                    return Result.failure(err)
                }
                userPrompt = "$contextPrompt\n\nSCREEN:\n$screenPrompt\n\nPREVIOUS_ERROR: JSON parse failed (${err.localizedMessage}). Return valid JSON only."
                continue
            }

            tokenUsageRepository.record(
                taskId = taskId,
                provider = structured.provider,
                model = structured.model,
                usage = structured.usage,
                reason = if (attempt == 0) reason else "validation_repair_$attempt"
            )

            when (val validation = AgentActionValidator.validate(structured.data, observation)) {
                is ActionValidationResult.Valid -> return Result.success(validation.plan)
                is ActionValidationResult.Invalid -> {
                    if (attempt == maxValidationAttempts) {
                        return Result.failure(IllegalArgumentException("Action validation failed: ${validation.reason}"))
                    }
                    userPrompt = "$contextPrompt\n\nSCREEN:\n$screenPrompt\n\nVALIDATION_ERROR: ${validation.reason}. Fix and return valid JSON."
                }
            }
        }
        return Result.failure(IllegalStateException("Exhausted bounded validation retries"))
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
