package com.orbit.assistant.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp WebSocket client ready for streaming real-time agent events from a future backend.
 */
@Singleton
class OrbitWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private var webSocket: WebSocket? = null
    private val _frames = MutableSharedFlow<WebSocketEventFrame>(extraBufferCapacity = 64)
    val frames: SharedFlow<WebSocketEventFrame> = _frames.asSharedFlow()

    fun connect(wsUrl: String) {
        disconnect()
        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    json.decodeFromString<WebSocketEventFrame>(text)
                }.onSuccess { frame ->
                    _frames.tryEmit(frame)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Handled by RemoteAgentRepository reconnection strategy when backend is enabled.
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnected")
        webSocket = null
    }
}
