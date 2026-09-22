package com.zerotap.alert

import com.zerotap.domain.model.AlertPayload
import com.zerotap.domain.model.DeliveryResult

interface AlertTransport {
    val transportName: String
    suspend fun send(alert: AlertPayload): DeliveryResult
    fun isAvailable(): Boolean
}
