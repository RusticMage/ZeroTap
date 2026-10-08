package com.zerotap.service

import android.content.Context
import com.zerotap.ServiceLocator
import com.zerotap.core.config.AppConfiguration
import com.zerotap.util.Logger
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Global background manager that ensures continuous GPS location syncing and
 * emergency safety ping polling between phone user and web command center.
 * Operates independently of whether full physical sensor protection is toggled on.
 */
object EmergencyContactSyncManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isStarted = AtomicBoolean(false)
    private var lastHandledPingId: String? = null
    private var lastSyncedLocationTime = 0L

    fun start(context: Context) {
        if (isStarted.getAndSet(true)) return

        val appContext = context.applicationContext
        Logger.alert("ContactSyncManager", "Starting global emergency contact sync & safety ping poller")

        // 1. Immediately request location tracking on LocationRepository
        scope.launch {
            try {
                ServiceLocator.locationRepository.startTracking()
            } catch (e: Exception) {
                Logger.alert("ContactSyncManager", "Could not start tracking: ${e.message}")
            }
        }

        // 2. Stream GPS location whenever samples arrive (throttled to every 2500ms)
        scope.launch {
            ServiceLocator.locationRepository.observeLocationSample().collect { sample ->
                val now = System.currentTimeMillis()
                if (now - lastSyncedLocationTime >= 2500L) {
                    lastSyncedLocationTime = now
                    try {
                        ServiceLocator.apiClient.syncCurrentLocation(
                            latitude = sample.latitude,
                            longitude = sample.longitude,
                            speed = sample.speed,
                            bearing = sample.bearing
                        )
                    } catch (_: Exception) {}
                }
            }
        }

        // 3. Continuous Emergency Safety Ping Poller (Runs every 2000ms)
        scope.launch {
            while (isActive) {
                delay(2000)
                try {
                    val userId = AppConfiguration.deviceId
                    val res = ServiceLocator.apiClient.checkPendingPing(userId)
                    res.onSuccess { pending ->
                        if (pending != null && pending.responseStatus == "PENDING" && pending.pingId != lastHandledPingId) {
                            lastHandledPingId = pending.pingId
                            Logger.alert("ContactSyncManager", "Received emergency safety ping from: ${pending.contactName} (${pending.pingId})")
                            ProtectionForegroundService.postSafetyPing(pending.pingId, pending.contactName)
                            ProtectionForegroundService.triggerPingVibration(appContext)
                            ProtectionForegroundService.notifySafetyCheckPing(appContext, pending.pingId, pending.contactName)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
