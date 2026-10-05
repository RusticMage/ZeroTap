package com.zerotap

import com.zerotap.data.remote.api.PairingCodeResult
import com.zerotap.data.remote.api.PairingStatusResult
import com.zerotap.data.remote.api.PendingContactPingResult
import com.zerotap.service.ProtectionForegroundService
import org.junit.Assert.*
import org.junit.Test

class EmergencyPairingTest {

    @Test
    fun testPairingCodeDataStructure() {
        val result = PairingCodeResult(
            code = "482731",
            expiresAt = "2026-10-05T16:30:00Z",
            expiresInSeconds = 600L
        )

        assertEquals("482731", result.code)
        assertEquals(6, result.code.length)
        assertEquals(600L, result.expiresInSeconds)
    }

    @Test
    fun testPairingStatusFormatting() {
        val connectedStatus = PairingStatusResult(
            status = "CONNECTED",
            contactName = "Sarah (Primary Contact)",
            contactPhone = "+91 98765 43210"
        )
        assertEquals("CONNECTED", connectedStatus.status)
        assertEquals("Sarah (Primary Contact)", connectedStatus.contactName)

        val pendingStatus = PairingStatusResult(
            status = "PENDING",
            code = "123456",
            expiresInSeconds = 540L
        )
        assertEquals("PENDING", pendingStatus.status)
        assertEquals("123456", pendingStatus.code)
    }

    @Test
    fun testPendingContactPingModel() {
        val ping = PendingContactPingResult(
            pingId = "ping-1234",
            contactName = "Mom",
            sentAt = "2026-10-05T16:35:00Z",
            responseStatus = "PENDING"
        )

        assertEquals("ping-1234", ping.pingId)
        assertEquals("Mom", ping.contactName)
        assertEquals("PENDING", ping.responseStatus)

        val activePing = ProtectionForegroundService.ActiveSafetyPing(
            pingId = ping.pingId,
            contactName = ping.contactName,
            timestamp = System.currentTimeMillis()
        )
        assertEquals("ping-1234", activePing.pingId)
        assertEquals("Mom", activePing.contactName)
    }
}
