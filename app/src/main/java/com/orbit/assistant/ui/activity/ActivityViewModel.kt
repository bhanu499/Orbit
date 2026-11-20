package com.orbit.assistant.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.assistant.domain.models.Task
import com.orbit.assistant.domain.repository.AgentRepository
import com.orbit.assistant.domain.state.AgentState
import com.orbit.assistant.domain.usecases.ObserveTaskHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ActivityFilter(val label: String) {
    ALL("All"),
    RUNNING("Running"),
    COMPLETED("Completed"),
    FAILED("Failed")
}

data class ActivityUiState(
    val selectedFilter: ActivityFilter = ActivityFilter.ALL,
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val observeTaskHistoryUseCase: ObserveTaskHistoryUseCase,
    private val agentRepository: AgentRepository
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(ActivityFilter.ALL)

    val uiState: StateFlow<ActivityUiState> = combine(
        observeTaskHistoryUseCase(),
        selectedFilter
    ) { allTasks, filter ->
        val filtered = when (filter) {
            ActivityFilter.ALL -> allTasks
            ActivityFilter.RUNNING -> allTasks.filter { it.status.isActive }
            ActivityFilter.COMPLETED -> allTasks.filter { it.status is AgentState.Completed }
            ActivityFilter.FAILED -> allTasks.filter {
                it.status is AgentState.Failed || it.status is AgentState.Cancelled
            }
        }
        ActivityUiState(
            selectedFilter = filter,
            tasks = filtered,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ActivityUiState()
    )

    init {
        viewModelScope.launch {
            agentRepository.seedSampleHistoryIfEmpty()
        }
    }

    fun selectFilter(filter: ActivityFilter) {
        selectedFilter.value = filter
    }

    fun observeTaskDetail(taskId: String): StateFlow<Task?> {
        return observeTaskHistoryUseCase.observeTask(taskId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }
}
