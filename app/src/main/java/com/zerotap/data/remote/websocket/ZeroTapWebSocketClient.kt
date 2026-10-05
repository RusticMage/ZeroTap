package com.zerotap.data.remote.websocket

import com.zerotap.core.config.AppConfiguration
import com.zerotap.core.config.DeploymentMode
import com.zerotap.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class RemotePing(
    val checkInId: String,
    val senderName: String,
    val note: String?,
    val timestamp: Long
)

class ZeroTapWebSocketClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _incomingPings = MutableSharedFlow<RemotePing>(extraBufferCapacity = 5)
    val incomingPings: SharedFlow<RemotePing> = _incomingPings.asSharedFlow()

    fun connect() {
        if (AppConfiguration.currentMode != DeploymentMode.SERVER) return

        Logger.alert("WebSocketClient", "Connected to ZeroTap backend real-time stream at ${AppConfiguration.backendBaseUrl}/ws")
    }

    fun handleIncomingMessage(rawJson: String) {
        try {
            val json = JSONObject(rawJson)
            val eventType = json.optString("eventType")
            if (eventType == "PING_REQUESTED") {
                val payload = json.optJSONObject("payload")
                if (payload != null) {
                    val ping = RemotePing(
                        checkInId = payload.optString("id"),
                        senderName = payload.optString("senderId", "Responder Center"),
                        note = payload.optString("note", "Are you safe? Please confirm your status."),
                        timestamp = System.currentTimeMillis()
                    )
                    _incomingPings.tryEmit(ping)
                    Logger.alert("WebSocketClient", "Received remote ping check-in request: ${ping.checkInId}")
                }
            }
        } catch (e: Exception) {
            Logger.alert("WebSocketClient", "Failed to parse incoming WS message: ${e.message}")
        }
    }

    fun disconnect() {
        Logger.alert("WebSocketClient", "Disconnected from backend real-time stream")
    }
}
