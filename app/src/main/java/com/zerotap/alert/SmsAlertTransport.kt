package com.zerotap.alert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.zerotap.domain.model.AlertPayload
import com.zerotap.domain.model.DeliveryResult
import com.zerotap.util.Logger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detailed result of an SMS alert delivery attempt.
 */
sealed class SmsDeliveryResult {
    data class Success(val timestamp: Long, val recipient: String) : SmsDeliveryResult()
    data class Failed(val reason: String) : SmsDeliveryResult()
    data class PermissionRequired(val missingPermission: String) : SmsDeliveryResult()
}

class SmsAlertTransport(private val context: Context? = null) : AlertTransport {
    override val transportName: String = "SMS"

    suspend fun sendSms(alert: AlertPayload): SmsDeliveryResult {
        val phone = alert.contactPhone
        if (phone.isNullOrBlank()) {
            return SmsDeliveryResult.Failed("No trusted contact phone number configured")
        }

        // 1. Verify runtime SEND_SMS permission
        if (context != null) {
            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                Logger.alert("SmsTransport", "SEND_SMS permission missing")
                return SmsDeliveryResult.PermissionRequired(Manifest.permission.SEND_SMS)
            }
        }

        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context != null) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(alert.timestamp))

            val messageText = if (alert.isTest) {
                "ZeroTap DEMO TEST — no emergency has been detected. Contact verification successful."
            } else {
                val locText = if (alert.latitude != null && alert.longitude != null) {
                    "Location:\n%.6f, %.6f\nhttps://maps.google.com/?q=%.6f,%.6f".format(
                        alert.latitude, alert.longitude, alert.latitude, alert.longitude
                    )
                } else {
                    "Location:\nLocation unavailable"
                }

                val accText = if (alert.locationAccuracy != null) "Location accuracy:\n%.0f m\n".format(alert.locationAccuracy) else ""
                val durText = if (alert.riskDurationSeconds > 0) "Risk duration:\n%d seconds\n".format(alert.riskDurationSeconds) else ""

                val factorsText = if (alert.contributingFactors.isNotEmpty()) {
                    "Contributing context:\n" + alert.contributingFactors.take(3).joinToString("\n") { "- $it" } + "\n"
                } else ""

                """
ZERO TAP EMERGENCY ALERT

Risk state: ${alert.riskState.displayName}
Risk score: ${(alert.riskScore * 100).toInt()}/100
Time: $timeStr

$locText
$accText$durText$factorsText
Current status:
Emergency response triggered.

Please check on the user immediately.
                """.trimIndent()
            }

            val parts = smsManager.divideMessage(messageText)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phone, null, messageText, null, null)
            }

            Logger.alert("SmsTransport", "Emergency SMS accepted by radio for $phone (${parts.size} parts, isTest: ${alert.isTest})")
            SmsDeliveryResult.Success(System.currentTimeMillis(), phone)
        } catch (e: SecurityException) {
            Logger.alert("SmsTransport", "SecurityException sending SMS: ${e.message}")
            SmsDeliveryResult.PermissionRequired(Manifest.permission.SEND_SMS)
        } catch (e: Exception) {
            Logger.alert("SmsTransport", "Exception sending SMS: ${e.message}")
            SmsDeliveryResult.Failed(e.message ?: "Failed to dispatch SMS")
        }
    }

    override suspend fun send(alert: AlertPayload): DeliveryResult {
        return when (val res = sendSms(alert)) {
            is SmsDeliveryResult.Success -> DeliveryResult(true, transportName, res.timestamp)
            is SmsDeliveryResult.Failed -> DeliveryResult(false, transportName, System.currentTimeMillis(), res.reason)
            is SmsDeliveryResult.PermissionRequired -> DeliveryResult(false, transportName, System.currentTimeMillis(), "SEND_SMS permission required")
        }
    }

    override fun isAvailable(): Boolean = true
}
