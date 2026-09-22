package com.zerotap.ui.accident

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.zerotap.data.datastore.UserPreferences
import com.zerotap.domain.accident.AccidentState
import com.zerotap.domain.response.SmsDeliveryState
import com.zerotap.service.ProtectionForegroundService
import com.zerotap.ui.theme.ZtDarkBackground
import com.zerotap.ui.theme.ZtDarkSurface
import com.zerotap.ui.theme.ZtHighRiskRust
import com.zerotap.ui.theme.ZtIncidentCrimson
import com.zerotap.ui.theme.ZtSafeOlive
import com.zerotap.ui.theme.ZtWatchAmber

@Composable
fun AccidentConfirmationScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context.applicationContext) }
    val isDemoCallEnabled by userPreferences.demoEmergencyCallEnabled.collectAsStateWithLifecycle(initialValue = false)

    val accidentState by ProtectionForegroundService.accidentState.collectAsStateWithLifecycle()
    val countdown by ProtectionForegroundService.accidentCountdownSeconds.collectAsStateWithLifecycle()
    val evidence by ProtectionForegroundService.accidentEvidence.collectAsStateWithLifecycle()
    val smsState by ProtectionForegroundService.smsDeliveryState.collectAsStateWithLifecycle()
    val callStatus by ProtectionForegroundService.emergencyCallStatus.collectAsStateWithLifecycle()

    // Gentle pulse vibration per second during grace period countdown
    LaunchedEffect(countdown) {
        if (countdown > 0 && accidentState == AccidentState.USER_CHECK) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(120)
                }
            } catch (_: Exception) {}
        }
    }

    // Auto navigate back when user cancels or accident state normalizes
    LaunchedEffect(accidentState) {
        if (accidentState == AccidentState.NORMAL) {
            navController.popBackStack()
        }
    }

    val isEscalated = accidentState == AccidentState.ESCALATING ||
            accidentState == AccidentState.SMS_SENT ||
            accidentState == AccidentState.CALL_INITIATED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZtDarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP HEADER: DEMO STATUS BANNER
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = if (isDemoCallEnabled) ZtIncidentCrimson.copy(alpha = 0.25f) else ZtWatchAmber.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, if (isDemoCallEnabled) ZtIncidentCrimson else ZtWatchAmber)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isDemoCallEnabled) ZtIncidentCrimson else ZtWatchAmber)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isDemoCallEnabled) "DEMO MODE — REAL CALL ENABLED" else "DEMO MODE — CALL DISABLED",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDemoCallEnabled) Color.White else ZtWatchAmber,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isEscalated) "EMERGENCY ESCALATED" else "POSSIBLE VEHICLE ACCIDENT DETECTED",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isEscalated) ZtIncidentCrimson else ZtHighRiskRust,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isEscalated) "Automatic zero-tap response dispatched" else "Are you okay? An impact signature was detected.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }

        // CENTER: TACTILE COUNTDOWN & EVIDENCE CARD
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = ZtDarkSurface,
                border = BorderStroke(1.dp, Color(0xFF362F2D)),
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isEscalated) {
                        // 15-second visual countdown
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(160.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { (countdown / 15f).coerceIn(0f, 1f) },
                                modifier = Modifier.size(160.dp),
                                strokeWidth = 12.dp,
                                color = ZtIncidentCrimson,
                                trackColor = Color(0xFF2A2420)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$countdown",
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 56.sp
                                )
                                Text(
                                    text = "SECONDS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Kinematic Evidence Breakdown
                        val ev = evidence
                        if (ev != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E1A18),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Kinematic Signatures Evaluated:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "• Impact Deceleration: %.1f m/s²".format(ev.peakAcceleration),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "• Kinematic Jerk: %.1f m/s³".format(ev.jerkMagnitude),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "• Angular Velocity: %.1f rad/s".format(ev.peakGyro),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                    if (ev.preImpactSpeedKmh != null && ev.postImpactSpeedKmh != null) {
                                        Text(
                                            text = "• Speed: %.0f → %.0f km/h".format(ev.preImpactSpeedKmh, ev.postImpactSpeedKmh),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (isDemoCallEnabled) {
                                "If you do not respond before the timer expires, an emergency SMS and real phone call will be placed automatically."
                            } else {
                                "If you do not respond before the timer expires, an emergency SMS will be sent and phone call simulated automatically."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Escalated State: Reactive SMS and Call delivery status
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Icon(
                                if (isDemoCallEnabled) Icons.Default.Call else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isDemoCallEnabled) ZtIncidentCrimson else ZtWatchAmber,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val (smsStatusText, smsColor) = when (val s = smsState) {
                                is SmsDeliveryState.Sending -> Pair("SMS SENDING...", ZtWatchAmber)
                                is SmsDeliveryState.Sent -> Pair("SMS SENT to ${s.contactName}", ZtSafeOlive)
                                is SmsDeliveryState.Failed -> Pair("SMS FAILED: ${s.reason}", ZtHighRiskRust)
                                is SmsDeliveryState.PermissionRequired -> Pair("SMS PERMISSION REQUIRED", ZtHighRiskRust)
                                is SmsDeliveryState.NoPrimaryContact -> Pair("NO TRUSTED CONTACT CONFIGURED", ZtWatchAmber)
                                is SmsDeliveryState.Idle -> Pair("SMS Dispatched", Color.White)
                            }

                            Text(
                                text = smsStatusText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = smsColor,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = callStatus ?: if (isDemoCallEnabled) "Emergency call initiated" else "Call: Simulation Only (No real call made)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // BOTTOM ACTION BUTTON: THE SOLE INTERACTION IS "I'M FINE"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Button(
                onClick = {
                    ProtectionForegroundService.userAffirmsFine()
                    navController.popBackStack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E3830),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (isEscalated) "I'M FINE — DISMISS" else "I'M FINE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}
