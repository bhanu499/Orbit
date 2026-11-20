package com.orbit.assistant.domain.usecases

import com.orbit.assistant.domain.models.ApprovalDecision
import com.orbit.assistant.domain.models.Task
import com.orbit.assistant.domain.repository.AgentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CreateTaskUseCase @Inject constructor(
    private val agentRepository: AgentRepository
) {
    suspend operator fun invoke(prompt: String): Task = agentRepository.createTask(prompt)
}

class ControlTaskUseCase @Inject constructor(
    private val agentRepository: AgentRepository
) {
    suspend fun pause(taskId: String) = agentRepository.pauseTask(taskId)
    suspend fun resume(taskId: String) = agentRepository.resumeTask(taskId)
    suspend fun cancel(taskId: String) = agentRepository.cancelTask(taskId)
    suspend fun continueAfterIntervention(taskId: String) = agentRepository.continueAfterIntervention(taskId)
    suspend fun submitApproval(taskId: String, decision: ApprovalDecision) =
        agentRepository.approveTask(taskId, decision)
    suspend fun updateBasketQuantity(taskId: String, itemId: String, delta: Int) =
        agentRepository.updateBasketItemQuantity(taskId, itemId, delta)
}

class ObserveTaskHistoryUseCase @Inject constructor(
    private val agentRepository: AgentRepository
) {
    operator fun invoke(): Flow<List<Task>> = agentRepository.getTaskHistory()
    fun observeTask(taskId: String): Flow<Task?> = agentRepository.observeTask(taskId)
}
