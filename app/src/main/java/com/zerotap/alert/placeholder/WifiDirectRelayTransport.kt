package com.zerotap.alert.placeholder

import com.zerotap.alert.AlertTransport
import com.zerotap.domain.model.AlertPayload
import com.zerotap.domain.model.DeliveryResult

class WifiDirectRelayTransport : AlertTransport {
    override val transportName: String = "Wi-Fi Direct Relay"

    override suspend fun send(alert: AlertPayload): DeliveryResult {
        return DeliveryResult(success = false, transportName = transportName, timestamp = System.currentTimeMillis(), errorMessage = "Wi-Fi Direct relay not yet implemented")
    }

    override fun isAvailable(): Boolean = false
}
