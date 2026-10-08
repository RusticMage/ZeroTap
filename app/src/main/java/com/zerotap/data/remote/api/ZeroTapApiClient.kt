package com.zerotap.data.remote.api

import com.zerotap.core.config.AppConfiguration
import com.zerotap.domain.model.EvidenceItem
import com.zerotap.domain.model.Incident
import com.zerotap.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ZeroTapApiClient {

    private fun getBaseUrl(): String = getActiveServerUrl()

    suspend fun registerDevice(deviceId: String, deviceName: String, model: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${getBaseUrl()}/api/devices/register")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val json = JSONObject().apply {
                put("deviceId", deviceId)
                put("deviceName", deviceName)
                put("deviceModel", model)
            }

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val ok = conn.responseCode in 200..299
            Result.success(ok)
        } catch (e: Exception) {
            Logger.alert("ApiClient", "Device registration failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun syncIncident(incident: Incident): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${getBaseUrl()}/api/incidents")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val json = JSONObject().apply {
                put("id", incident.id)
                put("riskScore", incident.riskScore)
                put("riskState", incident.riskState.name)
                put("status", incident.status.name)
                put("startTime", incident.createdAt.toString())
                put("summary", incident.summary ?: "")
            }

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            Result.success(conn.responseCode in 200..299)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateIncidentStatus(incidentId: String, status: String, summary: String?): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${getBaseUrl()}/api/incidents/$incidentId/status")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PATCH"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val json = JSONObject().apply {
                put("status", status)
                if (summary != null) put("summary", summary)
            }

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            Result.success(conn.responseCode in 200..299)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadEvidence(item: EvidenceItem): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${getBaseUrl()}/api/evidence")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val json = JSONObject().apply {
                put("id", item.id)
                put("incidentId", item.incidentId ?: "")
                put("type", item.type.name)
                put("uri", item.uri)
                put("latitude", item.latitude ?: 0.0)
                put("longitude", item.longitude ?: 0.0)
                put("relevanceScore", item.relevanceScore)
            }

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            Result.success(conn.responseCode in 200..299)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun respondToCheckIn(checkInId: String, response: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${getBaseUrl()}/api/incidents/check-in/respond")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val json = JSONObject().apply {
                put("checkInId", checkInId)
                put("response", response)
            }

            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            Result.success(conn.responseCode in 200..299)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var activeBaseUrl: String? = null

        fun getActiveServerUrl(): String = activeBaseUrl ?: AppConfiguration.backendBaseUrl

        fun setCustomServerUrl(url: String) {
            val trimmed = url.trim()
            if (trimmed.isNotBlank()) {
                val formatted = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                    "http://$trimmed"
                } else trimmed
                val clean = formatted.trimEnd('/')
                AppConfiguration.backendBaseUrl = clean
                activeBaseUrl = clean
            }
        }
    }

    private fun getCandidateBaseUrls(): List<String> {
        val configured = AppConfiguration.backendBaseUrl
        val isEmulator = try {
            android.os.Build.FINGERPRINT?.startsWith("generic") == true ||
            android.os.Build.MODEL?.contains("google_sdk") == true ||
            android.os.Build.HARDWARE?.contains("goldfish") == true ||
            android.os.Build.HARDWARE?.contains("ranchu") == true
        } catch (_: Throwable) {
            false
        }

        val list = mutableListOf<String>()
        activeBaseUrl?.let { list.add(it) }
        list.add(configured)
        list.add("http://192.168.1.3:8080")
        if (!isEmulator) {
            list.add("http://127.0.0.1:8080")
            list.add("http://172.16.45.4:8080")
        } else {
            list.add("http://10.0.2.2:8080")
        }
        return list.distinct()
    }

    private suspend fun <T> executeWithCandidates(
        action: (baseUrl: String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        val candidates = getCandidateBaseUrls()
        var lastException: Exception? = null

        for (candidate in candidates) {
            try {
                val res = action(candidate)
                activeBaseUrl = candidate
                AppConfiguration.backendBaseUrl = candidate
                return@withContext Result.success(res)
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(lastException ?: Exception("Cannot connect to server at any candidate: ${candidates.joinToString()}"))
    }

    // --- Emergency Contact Pairing & Telemetry ---

    suspend fun generatePairingCode(userId: String): Result<PairingCodeResult> = executeWithCandidates { baseUrl ->
        val url = URL("$baseUrl/api/pairing/generate?userId=$userId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 2500
            readTimeout = 2500
            setRequestProperty("Accept", "application/json")
        }

        if (conn.responseCode in 200..299) {
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(resp)
            val code = json.getString("code")
            val expiresAt = json.optString("expiresAt", "")
            val expiresInSeconds = json.optLong("expiresInSeconds", 600L)
            PairingCodeResult(code, expiresAt, expiresInSeconds)
        } else {
            throw Exception("HTTP ${conn.responseCode} from $baseUrl")
        }
    }

    suspend fun getPairingStatus(userId: String): Result<PairingStatusResult> = executeWithCandidates { baseUrl ->
        val url = URL("$baseUrl/api/pairing/status?userId=$userId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2500
            readTimeout = 2500
            setRequestProperty("Accept", "application/json")
        }

        if (conn.responseCode in 200..299) {
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(resp)
            PairingStatusResult(
                status = json.optString("status", "NOT_CONNECTED"),
                code = json.optString("code").takeIf { it.isNotBlank() },
                expiresAt = json.optString("expiresAt").takeIf { it.isNotBlank() },
                expiresInSeconds = if (json.has("expiresInSeconds") && !json.isNull("expiresInSeconds")) json.getLong("expiresInSeconds") else null,
                contactName = json.optString("contactName").takeIf { it.isNotBlank() },
                contactPhone = json.optString("contactPhone").takeIf { it.isNotBlank() },
                connectedAt = json.optString("connectedAt").takeIf { it.isNotBlank() }
            )
        } else {
            throw Exception("HTTP ${conn.responseCode} from $baseUrl")
        }
    }

    suspend fun unpairContact(userId: String): Result<Boolean> = executeWithCandidates { baseUrl ->
        val url = URL("$baseUrl/api/pairing/unpair?userId=$userId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 2500
            readTimeout = 2500
        }
        if (conn.responseCode in 200..299) {
            true
        } else {
            throw Exception("HTTP ${conn.responseCode} from $baseUrl")
        }
    }

    suspend fun checkPendingPing(userId: String): Result<PendingContactPingResult?> = executeWithCandidates { baseUrl ->
        val url = URL("$baseUrl/api/contacts/ping-status?userId=$userId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2000
            readTimeout = 2000
        }

        if (conn.responseCode == 200) {
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(resp)
            val pingId = json.optString("pingId")
            val contactName = json.optString("contactName", "Emergency Contact")
            val sentAt = json.optString("sentAt")
            val responseStatus = json.optString("responseStatus", "PENDING")
            PendingContactPingResult(pingId, contactName, sentAt, responseStatus)
        } else if (conn.responseCode == 204) {
            null
        } else {
            throw Exception("HTTP ${conn.responseCode} from $baseUrl")
        }
    }

    suspend fun respondToContactPing(pingId: String, userId: String, message: String): Result<Boolean> = executeWithCandidates { baseUrl ->
        val url = URL("$baseUrl/api/contacts/ping-response")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 2500
            readTimeout = 2500
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val json = JSONObject().apply {
            put("pingId", pingId)
            put("userId", userId)
            put("status", "SAFE")
            put("message", message)
        }

        conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
        if (conn.responseCode in 200..299) {
            true
        } else {
            throw Exception("HTTP ${conn.responseCode} from $baseUrl")
        }
    }
}

data class PairingCodeResult(
    val code: String,
    val expiresAt: String,
    val expiresInSeconds: Long
)

data class PairingStatusResult(
    val status: String,
    val code: String? = null,
    val expiresAt: String? = null,
    val expiresInSeconds: Long? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val connectedAt: String? = null
)

data class PendingContactPingResult(
    val pingId: String,
    val contactName: String,
    val sentAt: String?,
    val responseStatus: String
)

