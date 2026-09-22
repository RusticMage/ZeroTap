package com.zerotap.ui.debug

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.zerotap.ai.hierarchical.InferenceTier
import com.zerotap.domain.model.ContributionLevel
import com.zerotap.ui.components.HospitableSectionHeader

@Composable
fun DebugDashboardScreen(
    navController: NavHostController,
    viewModel: DebugViewModel = viewModel()
) {
    val diag by viewModel.diagnostics.collectAsStateWithLifecycle()
    val syntheticResult by viewModel.syntheticRiskResult.collectAsStateWithLifecycle()
    val lastSignal by viewModel.lastInjectedSignalName.collectAsStateWithLifecycle()
    val aiTier by viewModel.liveAiTier.collectAsStateWithLifecycle()
    val accidentState by viewModel.liveAccidentState.collectAsStateWithLifecycle()
    val accidentCountdown by viewModel.liveAccidentCountdown.collectAsStateWithLifecycle()
    val accidentEvidence by viewModel.liveAccidentEvidence.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Developer & Test Console",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Isolated synthetic pipeline testing & hardware transparency",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ==========================================
        // 1. ISOLATION BANNER (Requirement P20A)
        // ==========================================
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Test Harness Notice",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "DEVELOPER TEST HARNESS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Isolated Synthetic Evaluation Engine (Non-Production). Injected signals test the multi-modal risk scoring mathematics offline without altering live sensors or triggering actual alerts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2. SYNTHETIC COMPOSITE RISK SCORE CARD (P20A)
        // ==========================================
        item {
            HospitableSectionHeader(title = "Synthetic Risk Calculation")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Synthetic Composite Score",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = lastSignal,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${syntheticResult.scorePercent} / 100",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                syntheticResult.scorePercent >= 75 -> MaterialTheme.colorScheme.error
                                syntheticResult.scorePercent >= 45 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Mathematically Derived Factor Breakdown:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    ContributionBarRow("Motion Kinematics", syntheticResult.motionContribution, syntheticResult.motionLevel)
                    Spacer(modifier = Modifier.height(6.dp))
                    ContributionBarRow("Acoustic / Voice", syntheticResult.audioContribution, syntheticResult.voiceLevel)
                    Spacer(modifier = Modifier.height(6.dp))
                    ContributionBarRow("Location Anomaly", syntheticResult.locationContribution, syntheticResult.locationLevel)
                    Spacer(modifier = Modifier.height(6.dp))
                    ContributionBarRow("Persistence & Synergy", syntheticResult.temporalContribution, syntheticResult.persistenceLevel)

                    if (syntheticResult.contributingFactors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Active Factor Descriptors:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        syntheticResult.contributingFactors.forEach { factor ->
                            Text("• $factor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. SYNTHETIC SIGNAL INJECTIONS (P20A)
        // ==========================================
        item {
            HospitableSectionHeader(title = "Developer Injections")
        }

        item {
            Button(
                onClick = { viewModel.injectSuddenJerk() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Inject Sudden Jerk Motion Signal")
            }
        }

        item {
            Button(
                onClick = { viewModel.injectDistressAudio() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Inject Distress Audio Signal")
            }
        }

        item {
            Button(
                onClick = { viewModel.injectUnexpectedStop() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("Inject Unexpected Stop Signal", color = MaterialTheme.colorScheme.onSurface)
            }
        }

        item {
            Button(
                onClick = { viewModel.injectCombinedIncident() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Inject Combined Multi-Modal Incident")
            }
        }

        item {
            Button(
                onClick = { viewModel.injectSuspectedAccident() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Inject Suspected Vehicle Accident (High Impact)")
            }
        }

        item {
            Button(
                onClick = { viewModel.injectSpeedBumpReject() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("Inject Speed Bump (Verify Kinematic Rejection)", color = MaterialTheme.colorScheme.onSurface)
            }
        }

        item {
            Button(
                onClick = { viewModel.injectPhoneDropReject() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("Inject Phone Drop (Verify Walking Rejection)", color = MaterialTheme.colorScheme.onSurface)
            }
        }

        item {
            Button(
                onClick = { viewModel.triggerZeroTapTimeoutEscalation() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text("Trigger Zero-Tap Timeout Escalation (Auto SMS+Call)", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    viewModel.resetSyntheticSignals()
                    viewModel.resetAccident()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset All Signals & Accidents to Baseline")
            }
        }

        // ==========================================
        // 4. EDGE AI & ACCIDENT TELEMETRY
        // ==========================================
        item {
            HospitableSectionHeader(title = "Edge AI & Vehicle Accident Telemetry")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TelemetryRow("Active Edge AI Tier", when (aiTier) {
                        InferenceTier.TIER_0_LOW_POWER_REST -> "TIER 0: Quiescent Sleep / Low Power"
                        InferenceTier.TIER_1_LIGHTWEIGHT_INFERENCE -> "TIER 1: Lightweight Feature Inference"
                        InferenceTier.TIER_2_INCIDENT_REASONING -> "TIER 2: Event-Triggered Incident Reasoning"
                    })
                    TelemetryRow("Accident Detector State", accidentState.name)
                    if (accidentState == com.zerotap.domain.accident.AccidentState.USER_CHECK) {
                        TelemetryRow("Grace Period Remaining", "$accidentCountdown seconds")
                    }
                    if (accidentEvidence != null) {
                        TelemetryRow("Accident Evidence", "%.0f%% Confidence (%s)".format(
                            accidentEvidence!!.confidence * 100,
                            accidentEvidence!!.evidenceLevel.name
                        ))
                    }
                }
            }
        }

        // ==========================================
        // 5. LIVE HARDWARE TELEMETRY (Read-Only)
        // ==========================================
        item {
            HospitableSectionHeader(title = "Live Physical Sensors (Nothing 2a)")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TelemetryRow("Accelerometer", if (diag.isAccelerometerAvailable) "READY (Peak: %.1f m/s²)".format(diag.peakAccelMagnitude) else "UNAVAILABLE")
                    TelemetryRow("Gyroscope", if (diag.isGyroscopeAvailable) "READY (Jerk: %.1f m/s³)".format(diag.jerkMagnitude) else "UNAVAILABLE")
                    TelemetryRow("Microphone", if (diag.isAudioRecording) "RECORDING (%.1f dB)".format(diag.audioAmplitudeDb) else if (diag.isAudioAvailable) "STANDBY" else "UNAVAILABLE")
                    TelemetryRow("Location (GPS)", if (diag.locationLatitude != null) "%.4f, %.4f (±%.1fm)".format(diag.locationLatitude!!, diag.locationLongitude!!, diag.locationAccuracy ?: 0f) else "SEARCHING / STANDBY")
                }
            }
        }

        // ==========================================
        // 5. LOCAL INFERENCE TRANSPARENCY
        // ==========================================
        item {
            HospitableSectionHeader(title = "Privacy & Processing Model")
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TelemetryRow("Inference Location", "100% ON-DEVICE")
                    TelemetryRow("Sensor Data Egress", "NONE (Zero Cloud Dependency)")
                    TelemetryRow("Emergency Call", "Guarded by explicit toggle (Default: Simulated)")
                    TelemetryRow("Emergency SMS", "Direct GSM Cellular SMS via SmsManager")
                }
            }
        }
    }
}

@Composable
private fun ContributionBarRow(name: String, fraction: Float, level: ContributionLevel) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "${(fraction * 100).toInt()}% (${level.displayName})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = when (level) {
                    ContributionLevel.HIGH -> MaterialTheme.colorScheme.error
                    ContributionLevel.ELEVATED -> MaterialTheme.colorScheme.tertiary
                    ContributionLevel.MODERATE -> MaterialTheme.colorScheme.secondary
                    ContributionLevel.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = when (level) {
                ContributionLevel.HIGH -> MaterialTheme.colorScheme.error
                ContributionLevel.ELEVATED -> MaterialTheme.colorScheme.tertiary
                ContributionLevel.MODERATE -> MaterialTheme.colorScheme.secondary
                ContributionLevel.LOW -> MaterialTheme.colorScheme.outlineVariant
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
