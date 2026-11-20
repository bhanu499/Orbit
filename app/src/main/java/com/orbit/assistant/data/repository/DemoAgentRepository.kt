package com.orbit.assistant.data.repository

import com.orbit.assistant.agent.events.DemoScenarios
import com.orbit.assistant.agent.runtime.DemoAgentRuntime
import com.orbit.assistant.agent.runtime.LiveAgentRuntime
import com.orbit.assistant.data.local.TaskDao
import com.orbit.assistant.data.local.TaskEntity
import com.orbit.assistant.data.preferences.LlmProvider
import com.orbit.assistant.domain.models.AgentEvent
import com.orbit.assistant.domain.models.ApprovalDecision
import com.orbit.assistant.domain.models.Task
import com.orbit.assistant.domain.repository.AgentRepository
import com.orbit.assistant.domain.repository.PreferencesRepository
import com.orbit.assistant.domain.state.AgentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoAgentRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val demoRuntime: DemoAgentRuntime,
    private val liveRuntime: LiveAgentRuntime,
    private val preferencesRepository: PreferencesRepository,
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

        val settings = preferencesRepository.settingsFlow.first()
        val persist: suspend (Task) -> Unit = { updated ->
            taskDao.upsertTask(TaskEntity.fromDomain(updated, json))
        }

        if (settings.llmProvider != LlmProvider.DEMO && settings.llmApiKey.isNotBlank()) {
            liveRuntime.startTask(initialTask, settings, persist)
        } else {
            demoRuntime.startTask(initialTask, persist)
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
        val persist: suspend (Task) -> Unit = { updated ->
            taskDao.upsertTask(TaskEntity.fromDomain(updated, json))
        }
        if (liveRuntime.isManaging(taskId)) liveRuntime.cancel(taskId, persist)
        else demoRuntime.cancel(taskId, persist)
    }

    override suspend fun pauseTask(taskId: String) {
        val persist: suspend (Task) -> Unit = { updated ->
            taskDao.upsertTask(TaskEntity.fromDomain(updated, json))
        }
        if (liveRuntime.isManaging(taskId)) liveRuntime.pause(taskId, persist)
        else demoRuntime.pause(taskId, persist)
    }

    override suspend fun resumeTask(taskId: String) {
        val persist: suspend (Task) -> Unit = { updated ->
            taskDao.upsertTask(TaskEntity.fromDomain(updated, json))
        }
        if (liveRuntime.isManaging(taskId)) liveRuntime.resume(taskId, persist)
        else demoRuntime.resume(taskId, persist)
    }

    override suspend fun continueAfterIntervention(taskId: String) {
        val persist: suspend (Task) -> Unit = { updated ->
            taskDao.upsertTask(TaskEntity.fromDomain(updated, json))
        }
        if (liveRuntime.isManaging(taskId)) liveRuntime.continueAfterIntervention(taskId, persist)
        else demoRuntime.continueAfterIntervention(taskId, persist)
    }

    override suspend fun approveTask(taskId: String, approval: ApprovalDecision) {
        val persist: suspend (Task) -> Unit = { updated ->
            taskDao.upsertTask(TaskEntity.fromDomain(updated, json))
        }
        if (liveRuntime.isManaging(taskId)) liveRuntime.submitApproval(taskId, approval, persist)
        else demoRuntime.submitApproval(taskId, approval, persist)
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
