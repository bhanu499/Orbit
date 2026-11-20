package com.orbit.assistant.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.assistant.agent.appdiscovery.AppDescriptor
import com.orbit.assistant.agent.appdiscovery.AppDiscoveryManager
import com.orbit.assistant.agent.appdiscovery.AppLaunchResult
import com.orbit.assistant.agent.appdiscovery.AppLauncher
import com.orbit.assistant.domain.models.ApprovalDecision
import com.orbit.assistant.domain.models.ApprovalDecisionType
import com.orbit.assistant.domain.models.ChatMessage
import com.orbit.assistant.domain.models.MessageRole
import com.orbit.assistant.domain.models.Task
import com.orbit.assistant.domain.repository.AgentRepository
import com.orbit.assistant.domain.state.AgentState
import com.orbit.assistant.domain.usecases.ControlTaskUseCase
import com.orbit.assistant.domain.usecases.CreateTaskUseCase
import com.orbit.assistant.domain.usecases.ObserveTaskHistoryUseCase
import com.orbit.assistant.ui.components.AgentHeaderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AgentUiState(
    val messages: List<ChatMessage> = emptyList(),
    val activeTasksById: Map<String, Task> = emptyMap(),
    val activeTaskId: String? = null,
    val headerStatus: AgentHeaderStatus = AgentHeaderStatus.READY,
    val inputText: String = "",
    val isTyping: Boolean = false,
    val discoveredApps: List<AppDescriptor> = emptyList(),
    val isLoadingApps: Boolean = false
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val createTaskUseCase: CreateTaskUseCase,
    private val controlTaskUseCase: ControlTaskUseCase,
    private val observeTaskHistoryUseCase: ObserveTaskHistoryUseCase,
    private val agentRepository: AgentRepository,
    private val appDiscoveryManager: AppDiscoveryManager,
    private val appLauncher: AppLauncher
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val observedTaskJobs = mutableMapOf<String, Job>()
    private val streamedResultTasks = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            agentRepository.seedSampleHistoryIfEmpty()
        }
        loadDiscoveredApps()
    }

    private fun loadDiscoveredApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = appDiscoveryManager.discoverLaunchableApps()
            _uiState.update { it.copy(discoveredApps = apps, isLoadingApps = false) }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun submitPrompt(promptOverride: String? = null) {
        val raw = (promptOverride ?: _uiState.value.inputText).trim()
        if (raw.isEmpty()) return

        val effectivePrompt = when (raw) {
            "Find the cheapest groceries for my list" ->
                "Buy milk, eggs, bread and coffee. Check Blinkit and Zepto and find the cheapest combination."
            "Compare prices across shopping apps" ->
                "Compare milk prices on Blinkit and Zepto."
            else -> raw
        }

        val userMessage = ChatMessage(
            messageId = "msg_${UUID.randomUUID().toString().take(8)}",
            role = MessageRole.USER,
            text = effectivePrompt,
            timestamp = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = if (promptOverride == null) "" else it.inputText,
                isTyping = true,
                headerStatus = AgentHeaderStatus.WORKING
            )
        }

        viewModelScope.launch {
            val task = createTaskUseCase(effectivePrompt)
            val agentTaskMessage = ChatMessage(
                messageId = "msg_task_${task.taskId}",
                role = MessageRole.AGENT,
                text = "Starting simulated task execution in Demo Mode.",
                timestamp = System.currentTimeMillis(),
                linkedTaskId = task.taskId
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + agentTaskMessage,
                    activeTaskId = task.taskId,
                    activeTasksById = it.activeTasksById + (task.taskId to task),
                    isTyping = false
                )
            }
            observeTask(task.taskId)
        }
    }

    private fun observeTask(taskId: String) {
        if (observedTaskJobs.containsKey(taskId)) return
        observedTaskJobs[taskId] = viewModelScope.launch {
            observeTaskHistoryUseCase.observeTask(taskId).collect { task ->
                if (task == null) return@collect
                val newHeader = when (task.status) {
                    AgentState.Paused, AgentState.NeedsUserIntervention -> AgentHeaderStatus.PAUSED
                    AgentState.Running, AgentState.Planning, AgentState.WaitingForApproval -> AgentHeaderStatus.WORKING
                    else -> AgentHeaderStatus.READY
                }
                _uiState.update { state ->
                    state.copy(
                        activeTasksById = state.activeTasksById + (taskId to task),
                        headerStatus = if (state.activeTaskId == taskId) newHeader else state.headerStatus
                    )
                }

                val result = task.result
                if (task.status is AgentState.Completed && result != null && streamedResultTasks.add(taskId)) {
                    streamAgentSummary(taskId, result.summaryMarkdown)
                }
            }
        }
    }

    private fun streamAgentSummary(taskId: String, fullText: String) {
        viewModelScope.launch {
            val msgId = "msg_result_$taskId"
            val initialMsg = ChatMessage(
                messageId = msgId,
                role = MessageRole.AGENT,
                text = "",
                timestamp = System.currentTimeMillis(),
                isStreaming = true
            )
            _uiState.update { it.copy(messages = it.messages + initialMsg) }

            val words = fullText.split(" ")
            val builder = StringBuilder()
            for ((idx, word) in words.withIndex()) {
                if (idx > 0) builder.append(" ")
                builder.append(word)
                val snapshot = builder.toString()
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map {
                            if (it.messageId == msgId) it.copy(text = snapshot) else it
                        }
                    )
                }
                delay(28L)
            }
            _uiState.update { state ->
                state.copy(
                    messages = state.messages.map {
                        if (it.messageId == msgId) it.copy(isStreaming = false) else it
                    }
                )
            }
        }
    }

    fun pauseActiveTask(taskId: String) {
        viewModelScope.launch { controlTaskUseCase.pause(taskId) }
    }

    fun resumeActiveTask(taskId: String) {
        viewModelScope.launch { controlTaskUseCase.resume(taskId) }
    }

    fun stopActiveTask(taskId: String) {
        viewModelScope.launch {
            controlTaskUseCase.cancel(taskId)
            _snackbarEvents.tryEmit("Task stopped.")
        }
    }

    fun stopCurrentWorkingTask() {
        val currentId = _uiState.value.activeTaskId ?: return
        stopActiveTask(currentId)
    }

    fun continueAfterIntervention(taskId: String) {
        viewModelScope.launch { controlTaskUseCase.continueAfterIntervention(taskId) }
    }

    fun takeOverTask(taskId: String) {
        viewModelScope.launch {
            controlTaskUseCase.pause(taskId)
            val targetPkg = _uiState.value.activeTasksById[taskId]?.selectedApps?.firstOrNull()?.let { name ->
                _uiState.value.discoveredApps.firstOrNull { it.appName.contains(name, ignoreCase = true) }?.packageName
            }
            if (targetPkg != null) {
                openExternalApp(targetPkg)
            } else {
                _snackbarEvents.tryEmit("Task paused for manual takeover. Tap Resume or Continue when ready.")
            }
        }
    }

    fun approveTask(taskId: String, approvalId: String) {
        viewModelScope.launch {
            controlTaskUseCase.submitApproval(
                taskId,
                ApprovalDecision(
                    approvalId = approvalId,
                    decision = ApprovalDecisionType.APPROVED
                )
            )
            _snackbarEvents.tryEmit("Basket approved. Orbit will not submit payment.")
        }
    }

    fun rejectTask(taskId: String, approvalId: String) {
        viewModelScope.launch {
            controlTaskUseCase.submitApproval(
                taskId,
                ApprovalDecision(
                    approvalId = approvalId,
                    decision = ApprovalDecisionType.REJECTED
                )
            )
            _snackbarEvents.tryEmit("Approval rejected and task stopped.")
        }
    }

    fun adjustBasketQuantity(taskId: String, itemId: String, delta: Int) {
        viewModelScope.launch {
            controlTaskUseCase.updateBasketQuantity(taskId, itemId, delta)
        }
    }

    fun openExternalApp(packageName: String) {
        when (val result = appLauncher.launchApp(packageName)) {
            is AppLaunchResult.Launched ->
                _snackbarEvents.tryEmit("Opening ${result.appName}...")
            is AppLaunchResult.AppNotInstalled ->
                _snackbarEvents.tryEmit("App ($packageName) is not installed on this device.")
            is AppLaunchResult.LaunchIntentUnavailable ->
                _snackbarEvents.tryEmit("Launch intent unavailable for $packageName.")
            is AppLaunchResult.CannotLaunch ->
                _snackbarEvents.tryEmit("Could not launch app: ${result.reason}")
        }
    }

    fun onAttachmentPlaceholderClicked() {
        _snackbarEvents.tryEmit("Attachments will be supported when backend multimodal upload is connected.")
    }

    fun onVoicePlaceholderClicked() {
        _uiState.update {
            it.copy(inputText = "Buy milk, eggs, bread and coffee. Check Blinkit and Zepto and find the cheapest combination.")
        }
        _snackbarEvents.tryEmit("Voice input simulated: populated sample grocery prompt.")
    }

    fun onReturnedToOrbit() {
        appLauncher.onUserReturnedToOrbit()
    }
}
