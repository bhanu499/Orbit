package com.orbit.assistant.data.repository

import com.orbit.assistant.agent.events.DemoScenarios
import com.orbit.assistant.agent.runtime.DemoAgentRuntime
import com.orbit.assistant.data.local.TaskDao
import com.orbit.assistant.data.local.TaskEntity
import com.orbit.assistant.domain.models.AgentEvent
import com.orbit.assistant.domain.models.ApprovalDecision
import com.orbit.assistant.domain.models.Task
import com.orbit.assistant.domain.repository.AgentRepository
import com.orbit.assistant.domain.state.AgentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoAgentRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val demoRuntime: DemoAgentRuntime,
    private val json: Json
) : AgentRepository {

    override suspend fun createTask(prompt: String): Task {
        val now = System.currentTimeMillis()
        val taskId = "task_${UUID.randomUUID().toString().take(10)}"
        val initialTask = Task(
            taskId = taskId,
            userPrompt = prompt.trim(),
            createdAt = now,
            updatedAt = now,
            status = AgentState.Planning
        )
        taskDao.upsertTask(TaskEntity.fromDomain(initialTask, json))
        demoRuntime.startTask(initialTask) { updated ->
            taskDao.upsertTask(TaskEntity.fromDomain(updated, json))
        }
        return initialTask
    }

    override suspend fun getTask(taskId: String): Task? {
        return taskDao.getTaskById(taskId)?.toDomain(json)
    }

    override fun observeTask(taskId: String): Flow<Task?> {
        return taskDao.observeTaskById(taskId).map { it?.toDomain(json) }
    }

    override fun getTaskHistory(): Flow<List<Task>> {
        return taskDao.observeAllTasks().map { list ->
            list.map { it.toDomain(json) }
        }
    }

    override suspend fun cancelTask(taskId: String) {
        demoRuntime.cancel(taskId) { updated ->
            taskDao.upsertTask(TaskEntity.fromDomain(updated, json))
        }
    }

    override suspend fun pauseTask(taskId: String) {
        demoRuntime.pause(taskId) { updated ->
            taskDao.upsertTask(TaskEntity.fromDomain(updated, json))
        }
    }

    override suspend fun resumeTask(taskId: String) {
        demoRuntime.resume(taskId) { updated ->
            taskDao.upsertTask(TaskEntity.fromDomain(updated, json))
        }
    }

    override suspend fun continueAfterIntervention(taskId: String) {
        demoRuntime.continueAfterIntervention(taskId) { updated ->
            taskDao.upsertTask(TaskEntity.fromDomain(updated, json))
        }
    }

    override suspend fun approveTask(taskId: String, approval: ApprovalDecision) {
        demoRuntime.submitApproval(taskId, approval) { updated ->
            taskDao.upsertTask(TaskEntity.fromDomain(updated, json))
        }
    }

    override suspend fun updateBasketItemQuantity(taskId: String, itemId: String, delta: Int) {
        demoRuntime.updateBasketItemQuantity(taskId, itemId, delta) { updated ->
            taskDao.upsertTask(TaskEntity.fromDomain(updated, json))
        }
    }

    override fun observeTaskEvents(taskId: String): Flow<List<AgentEvent>> {
        return observeTask(taskId).map { it?.events ?: emptyList() }
    }

    override suspend fun clearTaskHistory() {
        taskDao.clearAllTasks()
    }

    override suspend fun seedSampleHistoryIfEmpty() {
        if (taskDao.countTasks() == 0) {
            val samples = DemoScenarios.seedSampleTasks().map { TaskEntity.fromDomain(it, json) }
            taskDao.upsertTasks(samples)
        }
    }
}
