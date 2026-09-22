package com.zerotap.alert

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.zerotap.util.Logger

class CallAlertTransport(
    private val context: Context,
    var isSimulationMode: Boolean = true
) : EmergencyTransport {

    override val transportName: String = "CALL"

    override suspend fun execute(payload: StructuredEmergencyPayload): EmergencyTransportResult {
        val phone = payload.contactPhone?.trim()
        if (phone.isNullOrBlank()) {
            Logger.alert("EmergencyCall", "[ZeroTap][Emergency] Call failed: No valid phone number provided")
            return EmergencyTransportResult.InvalidNumber(transportName, phone ?: "")
        }

        // 1. Verify runtime CALL_PHONE permission
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Logger.alert("EmergencyCall", "[ZeroTap][Emergency] CALL_PHONE permission not granted")
            return EmergencyTransportResult.PermissionDenied(transportName, Manifest.permission.CALL_PHONE)
        }

        // 2. Check Demo Safeguard Toggle
        if (isSimulationMode) {
            Logger.alert("EmergencyCall", "[ZeroTap][Emergency] Demo safeguard active: Simulated call to $phone (No real telecom dispatch)")
            return EmergencyTransportResult.Success(
                transportName = transportName,
                timestamp = System.currentTimeMillis(),
                details = "Simulated call to $phone (Demo Emergency Call is OFF)"
            )
        }

        // 3. Programmatic ACTION_CALL (No dialer button press required)
        return try {
            Logger.alert("EmergencyCall", "[ZeroTap][Emergency] Initiating automated ACTION_CALL to $phone")
            val cleanPhone = phone.replace(" ", "").replace("-", "")
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanPhone")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
            Logger.alert("EmergencyCall", "[ZeroTap][Emergency] ACTION_CALL successfully started for $phone")
            EmergencyTransportResult.Success(
                transportName = transportName,
                timestamp = System.currentTimeMillis(),
                details = "ACTION_CALL initiated to $phone"
            )
        } catch (e: SecurityException) {
            Logger.alert("EmergencyCall", "[ZeroTap][Emergency] SecurityException placing ACTION_CALL: ${e.message}")
            EmergencyTransportResult.PermissionDenied(transportName, Manifest.permission.CALL_PHONE)
        } catch (e: Exception) {
            Logger.alert("EmergencyCall", "[ZeroTap][Emergency] Exception placing ACTION_CALL: ${e.message}")
            EmergencyTransportResult.Failed(transportName, e.message ?: "Failed to place call")
        }
    }
}
