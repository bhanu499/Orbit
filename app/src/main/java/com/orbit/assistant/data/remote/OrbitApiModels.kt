package com.orbit.assistant.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class CreateTaskRequest(
    val prompt: String,
    val installedSupportedApps: List<String> = emptyList(),
    val autonomyLevel: String = "BALANCED"
)

@Serializable
data class TaskResponse(
    val taskId: String,
    val status: String,
    val createdAt: Long,
    val message: String? = null
)

@Serializable
data class AgentEventDto(
    val eventId: String,
    val taskId: String,
    val timestamp: Long,
    val type: String,
    val title: String,
    val description: String,
    val status: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class ApprovalSubmissionRequest(
    val approvalId: String,
    val decision: String,
    val note: String? = null
)

@Serializable
data class WebSocketEventFrame(
    val channel: String,
    val payload: AgentEventDto
)
