package com.zerotap.ui.emergency

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
import com.zerotap.domain.model.TemporalRiskState
import com.zerotap.domain.response.SmsDeliveryState
import com.zerotap.service.ProtectionForegroundService
import com.zerotap.ui.theme.ZtDarkBackground
import com.zerotap.ui.theme.ZtDarkSurface
import com.zerotap.ui.theme.ZtHighRiskRust
import com.zerotap.ui.theme.ZtIncidentCrimson
import com.zerotap.ui.theme.ZtSafeOlive
import com.zerotap.ui.theme.ZtWatchAmber

@Composable
fun EmergencyCountdownScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context.applicationContext) }
    val isDemoCallEnabled by userPreferences.demoEmergencyCallEnabled.collectAsStateWithLifecycle(initialValue = false)

    val temporalState by ProtectionForegroundService.temporalRiskState.collectAsStateWithLifecycle()
    val countdown by ProtectionForegroundService.countdownSeconds.collectAsStateWithLifecycle()
    val prediction by ProtectionForegroundService.predictionResult.collectAsStateWithLifecycle()
    val diag by ProtectionForegroundService.diagnostics.collectAsStateWithLifecycle()
    val smsState by ProtectionForegroundService.smsDeliveryState.collectAsStateWithLifecycle()
    val callStatus by ProtectionForegroundService.emergencyCallStatus.collectAsStateWithLifecycle()

    // Per-second subtle haptic feedback during countdown
    LaunchedEffect(countdown) {
        if (countdown > 0 && temporalState == TemporalRiskState.EMERGENCY_PENDING) {
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

    // Auto navigate back when user cancels or state normalizes
    LaunchedEffect(temporalState) {
        if (temporalState == TemporalRiskState.NORMAL) {
            navController.popBackStack()
        }
    }

    val isTriggered = temporalState == TemporalRiskState.EMERGENCY_TRIGGERED
    val riskScore = prediction?.scorePercent ?: ((diag.currentRiskScore * 100).toInt())
    val durationSeconds = prediction?.durationInCurrentStateSeconds ?: 0L

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
                text = if (isTriggered) "EMERGENCY TRIGGERED" else "EMERGENCY RESPONSE PENDING",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isTriggered) ZtIncidentCrimson else ZtHighRiskRust,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Autonomous incident persistence: ${durationSeconds}s",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        // CENTER: TACTILE COUNTDOWN & RISK CARD
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RISK SCORE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$riskScore / 100",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = ZtIncidentCrimson
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFF362F2D))
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isTriggered) {
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
                    } else {
                        // Triggered: Reactive SMS and Call delivery status
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

                            // P14: Reactive SMS status display
                            val (smsStatusText, smsColor) = when (val s = smsState) {
                                is SmsDeliveryState.Sending -> Pair("SMS SENDING...", ZtWatchAmber)
                                is SmsDeliveryState.Sent -> Pair("SMS SENT to ${s.contactName}", ZtSafeOlive)
                                is SmsDeliveryState.Failed -> Pair("SMS FAILED: ${s.reason}", ZtHighRiskRust)
                                is SmsDeliveryState.PermissionRequired -> Pair("SMS PERMISSION REQUIRED", ZtHighRiskRust)
                                is SmsDeliveryState.NoPrimaryContact -> Pair("NO TRUSTED CONTACT CONFIGURED", ZtWatchAmber)
                                is SmsDeliveryState.Idle -> Pair("SMS Prepared", Color.White)
                            }

                            Text(
                                text = smsStatusText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = smsColor,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // P4: Reactive 112 Call status display
                            Text(
                                text = callStatus ?: if (isDemoCallEnabled) "112 emergency call initiated" else "112 Call: Simulation Only (No real call made)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (!isTriggered) {
                            if (isDemoCallEnabled) {
                                "Emergency SMS and a real 112 call will be placed automatically when countdown ends. Tap CANCEL below if safe."
                            } else {
                                "Emergency SMS will be sent and 112 call will be simulated when countdown ends. Tap CANCEL below if safe."
                            }
                        } else {
                            "Emergency response workflow executed. Dispatched alerts to configured primary contact."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // BOTTOM ACTION BUTTONS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // CANCEL BUTTON (Always prominent)
            Button(
                onClick = {
                    ProtectionForegroundService.cancelEmergency()
                    navController.popBackStack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E3830),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "I'M SAFE — CANCEL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // IMMEDIATE ACTION BUTTON
            if (!isTriggered) {
                OutlinedButton(
                    onClick = {
                        ProtectionForegroundService.triggerEmergencyNow()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, if (isDemoCallEnabled) ZtIncidentCrimson else ZtWatchAmber),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isDemoCallEnabled) ZtIncidentCrimson else ZtWatchAmber
                    )
                ) {
                    Text(
                        text = if (isDemoCallEnabled) "CALL 112 NOW" else "SIMULATE 112 NOW",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
