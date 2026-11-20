package com.orbit.assistant.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit contract for future Orbit AI orchestration backend.
 * Currently unused in Demo Mode; RemoteAgentRepository will bind to this interface.
 */
interface OrbitApiService {
    @POST("v1/tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): TaskResponse

    @GET("v1/tasks/{taskId}")
    suspend fun getTask(@Path("taskId") taskId: String): TaskResponse

    @POST("v1/tasks/{taskId}/cancel")
    suspend fun cancelTask(@Path("taskId") taskId: String)

    @POST("v1/tasks/{taskId}/pause")
    suspend fun pauseTask(@Path("taskId") taskId: String)

    @POST("v1/tasks/{taskId}/resume")
    suspend fun resumeTask(@Path("taskId") taskId: String)

    @POST("v1/tasks/{taskId}/approve")
    suspend fun approveTask(
        @Path("taskId") taskId: String,
        @Body approval: ApprovalSubmissionRequest
    )
}
