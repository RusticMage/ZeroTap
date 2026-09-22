package com.zerotap.alert

import com.zerotap.domain.model.AlertPayload
import com.zerotap.domain.model.DeliveryResult
import com.zerotap.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class InternetAlertTransport : AlertTransport {
    override val transportName: String = "Internet"
    var endpointUrl: String = ""

    override suspend fun send(alert: AlertPayload): DeliveryResult = withContext(Dispatchers.IO) {
        if (endpointUrl.isEmpty()) {
            Logger.alert("InternetAlertTransport", "Endpoint URL is empty, logging alert locally.")
            return@withContext DeliveryResult(success = true, transportName = transportName, timestamp = System.currentTimeMillis())
        }
        
        try {
            val url = URL(endpointUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val jsonPayload = "{\"incidentId\":\"${alert.incidentId}\", \"message\":\"${alert.message}\"}"
            connection.outputStream.use { os ->
                os.write(jsonPayload.toByteArray(Charsets.UTF_8))
            }
            
            val code = connection.responseCode
            if (code in 200..299) {
                DeliveryResult(success = true, transportName = transportName, timestamp = System.currentTimeMillis())
            } else {
                DeliveryResult(success = false, transportName = transportName, timestamp = System.currentTimeMillis(), errorMessage = "HTTP $code")
            }
        } catch (e: Exception) {
            DeliveryResult(success = false, transportName = transportName, timestamp = System.currentTimeMillis(), errorMessage = e.message ?: "Unknown error")
        }
    }

    override fun isAvailable(): Boolean {
        return true
    }
}
