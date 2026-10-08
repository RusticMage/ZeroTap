package com.zerotap.data.repository

import com.zerotap.core.config.AppConfiguration
import com.zerotap.core.config.DeploymentMode
import com.zerotap.domain.model.SyncStatus
import com.zerotap.domain.repository.EvidenceRepository
import com.zerotap.domain.repository.SyncRepository
import com.zerotap.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SyncRepositoryImpl(
    private val evidenceRepository: EvidenceRepository
) : SyncRepository {

    private val _syncState = MutableStateFlow(SyncStatus.LOCAL_ONLY)
    override val syncState: StateFlow<SyncStatus> = _syncState.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    override val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    override suspend fun syncPendingData(): Result<Int> = withContext(Dispatchers.IO) {
        if (AppConfiguration.currentMode != DeploymentMode.SERVER) {
            _syncState.value = SyncStatus.LOCAL_ONLY
            return@withContext Result.success(0)
        }

        _syncState.value = SyncStatus.SYNCING
        var syncedCount = 0

        try {
            val pendingItems = evidenceRepository.getPendingSyncItems()
            _pendingCount.value = pendingItems.size

            for (item in pendingItems) {
                val success = uploadEvidenceItem(item)
                if (success) {
                    evidenceRepository.updateSyncStatus(item.id, SyncStatus.SYNCED)
                    syncedCount++
                } else {
                    evidenceRepository.updateSyncStatus(item.id, SyncStatus.FAILED)
                }
            }

            val remaining = evidenceRepository.getPendingSyncItems().size
            _pendingCount.value = remaining
            _syncState.value = if (remaining == 0) SyncStatus.SYNCED else SyncStatus.FAILED

            Result.success(syncedCount)
        } catch (e: Exception) {
            _syncState.value = SyncStatus.FAILED
            Logger.alert("SyncRepo", "Sync failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun uploadEvidenceItem(item: com.zerotap.domain.model.EvidenceItem): Boolean {
        return try {
            val file = if (item.uri.isNotBlank()) {
                val f = java.io.File(item.uri)
                if (f.exists()) f else null
            } else null

            if (file != null && item.type == com.zerotap.domain.model.EvidenceType.VEHICLE_PLATE) {
                uploadMultipartEvidence(item, file)
            } else {
                uploadJsonEvidence(item)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun uploadJsonEvidence(item: com.zerotap.domain.model.EvidenceItem): Boolean {
        val url = URL("${AppConfiguration.backendBaseUrl}/api/evidence")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 4000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val json = JSONObject().apply {
            put("id", item.id)
            put("type", item.type.name)
            put("uri", item.uri)
            put("timestamp", item.timestamp)
            put("latitude", item.latitude ?: 0.0)
            put("longitude", item.longitude ?: 0.0)
            put("relevanceScore", item.relevanceScore)
            put("incidentId", item.incidentId ?: "")
            if (item.metadata.containsKey("registrationNumber")) {
                put("registrationNumber", item.metadata["registrationNumber"])
            }
            if (item.metadata.containsKey("confidence")) {
                put("confidence", item.metadata["confidence"]?.toFloatOrNull() ?: 1.0f)
            }
        }

        conn.outputStream.use { os ->
            os.write(json.toString().toByteArray(Charsets.UTF_8))
        }

        return conn.responseCode in 200..299
    }

    private fun uploadMultipartEvidence(item: com.zerotap.domain.model.EvidenceItem, file: java.io.File): Boolean {
        val boundary = "==ZeroTapBoundary${System.currentTimeMillis()}=="
        val url = URL("${AppConfiguration.backendBaseUrl}/api/evidence/upload")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        val regNo = item.metadata["registrationNumber"] ?: ""
        val conf = item.metadata["confidence"]?.toFloatOrNull() ?: item.relevanceScore

        val metadataJson = JSONObject().apply {
            put("id", item.id)
            put("type", item.type.name)
            put("uri", item.uri)
            put("timestamp", item.timestamp)
            put("latitude", item.latitude ?: 0.0)
            put("longitude", item.longitude ?: 0.0)
            put("relevanceScore", item.relevanceScore)
            put("incidentId", item.incidentId ?: "")
            put("registrationNumber", regNo)
            put("confidence", conf)
        }.toString()

        val lineEnd = "\r\n"
        val twoHyphens = "--"

        conn.outputStream.use { os ->
            val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(os, Charsets.UTF_8))

            // Part 1: metadata JSON ("data")
            writer.write(twoHyphens + boundary + lineEnd)
            writer.write("Content-Disposition: form-data; name=\"data\"" + lineEnd)
            writer.write("Content-Type: application/json; charset=UTF-8" + lineEnd)
            writer.write(lineEnd)
            writer.write(metadataJson)
            writer.write(lineEnd)
            writer.flush()

            // Part 2: file
            writer.write(twoHyphens + boundary + lineEnd)
            writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"" + lineEnd)
            writer.write("Content-Type: image/jpeg" + lineEnd)
            writer.write(lineEnd)
            writer.flush()

            file.inputStream().use { fis ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    os.write(buffer, 0, bytesRead)
                }
            }
            os.flush()

            writer.write(lineEnd)
            writer.write(twoHyphens + boundary + twoHyphens + lineEnd)
            writer.flush()
        }

        return conn.responseCode in 200..299
    }

    override suspend fun syncIncident(incidentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (AppConfiguration.currentMode != DeploymentMode.SERVER) {
            return@withContext Result.success(Unit)
        }

        try {
            val url = URL("${AppConfiguration.backendBaseUrl}/api/incidents")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val json = JSONObject().apply {
                put("id", incidentId)
                put("timestamp", System.currentTimeMillis())
            }

            conn.outputStream.use { os ->
                os.write(json.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncCurrentLocation(
        latitude: Double,
        longitude: Double,
        speed: Float,
        bearing: Float
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val res = com.zerotap.ServiceLocator.apiClient.syncCurrentLocation(
                latitude, longitude, speed, bearing
            )
            if (res.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(res.exceptionOrNull() ?: Exception("Failed to sync location"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
