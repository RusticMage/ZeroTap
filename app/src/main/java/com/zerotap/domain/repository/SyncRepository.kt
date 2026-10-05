package com.zerotap.domain.repository

import com.zerotap.domain.model.SyncStatus
import kotlinx.coroutines.flow.StateFlow

interface SyncRepository {

    val syncState: StateFlow<SyncStatus>

    val pendingCount: StateFlow<Int>

    suspend fun syncPendingData(): Result<Int>

    suspend fun syncIncident(incidentId: String): Result<Unit>

    suspend fun syncCurrentLocation(
        latitude: Double,
        longitude: Double,
        speed: Float = 0f,
        bearing: Float = 0f
    ): Result<Unit>
}
