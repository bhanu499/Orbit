package com.orbit.assistant.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.assistant.data.repository.TokenUsageRepository
import com.orbit.assistant.data.repository.TokenUsageSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsageViewModel @Inject constructor(
    private val tokenUsageRepository: TokenUsageRepository
) : ViewModel() {

    val uiState: StateFlow<TokenUsageSummary> = tokenUsageRepository.observeSummary()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TokenUsageSummary()
        )

    fun clearUsage() {
        viewModelScope.launch {
            tokenUsageRepository.clearAll()
        }
    }
}
