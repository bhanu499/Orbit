package com.orbit.assistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.orbit.assistant.domain.models.AgentEvent
import com.orbit.assistant.domain.models.ApprovalHistoryEntry
import com.orbit.assistant.domain.models.ApprovalRequest
import com.orbit.assistant.domain.models.Task
import com.orbit.assistant.domain.models.TaskResult
import com.orbit.assistant.domain.state.AgentState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val taskId: String,
    val userPrompt: String,
    val createdAt: Long,
    val updatedAt: Long,
    val statusJson: String,
    val selectedAppsJson: String,
    val eventsJson: String,
    val resultJson: String?,
    val error: String?,
    val approvalRequiredJson: String?,
    val approvalHistoryJson: String,
    val durationMs: Long
) {
    fun toDomain(json: Json): Task = Task(
        taskId = taskId,
        userPrompt = userPrompt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = json.decodeFromString<AgentState>(statusJson),
        selectedApps = json.decodeFromString<List<String>>(selectedAppsJson),
        events = json.decodeFromString<List<AgentEvent>>(eventsJson),
        result = resultJson?.let { json.decodeFromString<TaskResult>(it) },
        error = error,
        approvalRequired = approvalRequiredJson?.let { json.decodeFromString<ApprovalRequest>(it) },
        approvalHistory = json.decodeFromString<List<ApprovalHistoryEntry>>(approvalHistoryJson),
        durationMs = durationMs
    )

    companion object {
        fun fromDomain(task: Task, json: Json): TaskEntity = TaskEntity(
            taskId = task.taskId,
            userPrompt = task.userPrompt,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
            statusJson = json.encodeToString(task.status),
            selectedAppsJson = json.encodeToString(task.selectedApps),
            eventsJson = json.encodeToString(task.events),
            resultJson = task.result?.let { json.encodeToString(it) },
            error = task.error,
            approvalRequiredJson = task.approvalRequired?.let { json.encodeToString(it) },
            approvalHistoryJson = json.encodeToString(task.approvalHistory),
            durationMs = task.durationMs
        )
    }
}
