package com.orbit.assistant.domain.repository

import com.orbit.assistant.domain.models.AgentEvent
import com.orbit.assistant.domain.models.ApprovalDecision
import com.orbit.assistant.domain.models.Task
import kotlinx.coroutines.flow.Flow

interface AgentRepository {
    suspend fun createTask(prompt: String): Task
    suspend fun getTask(taskId: String): Task?
    fun observeTask(taskId: String): Flow<Task?>
    fun getTaskHistory(): Flow<List<Task>>
    suspend fun cancelTask(taskId: String)
    suspend fun pauseTask(taskId: String)
    suspend fun resumeTask(taskId: String)
    suspend fun continueAfterIntervention(taskId: String)
    suspend fun approveTask(taskId: String, approval: ApprovalDecision)
    suspend fun updateBasketItemQuantity(taskId: String, itemId: String, delta: Int)
    fun observeTaskEvents(taskId: String): Flow<List<AgentEvent>>
    suspend fun clearTaskHistory()
    suspend fun seedSampleHistoryIfEmpty()
}
