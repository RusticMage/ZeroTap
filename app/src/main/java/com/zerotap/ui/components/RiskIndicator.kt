package com.zerotap.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotap.domain.model.ContributionLevel
import com.zerotap.domain.model.RiskState
import com.zerotap.ui.theme.contributionLevelColor
import com.zerotap.ui.theme.riskStateColor

@Composable
fun RiskStateBadge(
    state: RiskState,
    modifier: Modifier = Modifier
) {
    val color = riskStateColor(state)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = state.displayName.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
fun ContributionLevelBadge(
    level: ContributionLevel,
    modifier: Modifier = Modifier
) {
    val color = contributionLevelColor(level)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = level.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
fun LiveRiskMeterCard(
    state: RiskState,
    score: Float,
    isProtectionActive: Boolean,
    prediction: com.zerotap.domain.model.RiskPredictionResult? = null,
    modifier: Modifier = Modifier
) {
    val animatedScore by animateFloatAsState(
        targetValue = if (isProtectionActive) score else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "riskScore"
    )
    val stateColor = if (isProtectionActive) riskStateColor(state) else MaterialTheme.colorScheme.onSurfaceVariant
    val scoreInt = (animatedScore * 100).toInt().coerceIn(0, 100)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REAL-TIME SAFETY STATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                RiskStateBadge(state = if (isProtectionActive) state else RiskState.NORMAL)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tactile Circular Risk Gauge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                // Background Track
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(160.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 14.dp,
                    trackColor = Color.Transparent,
                )
                // Live Dynamic Progress Arc
                CircularProgressIndicator(
                    progress = { if (isProtectionActive) (animatedScore).coerceIn(0.02f, 1f) else 0.02f },
                    modifier = Modifier.size(160.dp),
                    color = stateColor,
                    strokeWidth = 14.dp,
                    trackColor = Color.Transparent,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isProtectionActive) "$scoreInt" else "--",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isProtectionActive) "/ 100" else "PAUSED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isProtectionActive) {
                    when (state) {
                        RiskState.NORMAL -> "Sensors steady - No anomalous threat detected"
                        RiskState.WATCH -> "Mild physical/audio variation under observation"
                        RiskState.SUSPICIOUS -> "Multiple sensor anomalies detected"
                        RiskState.HIGH_RISK -> "Elevated risk pattern detected - preparing safeguards"
                        RiskState.INCIDENT -> "Critical safety incident in progress"
                        RiskState.RESOLVED -> "Incident marked resolved - monitoring resumed"
                    }
                } else {
                    "Protection is currently paused. Tap toggle below to start."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // EXPLAINABLE CONTRIBUTING FACTORS (Requirement 1 & 16)
            if (isProtectionActive && prediction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "CONTRIBUTING FACTORS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FactorBadgeColumn(label = "Motion", level = prediction.motionLevel)
                    FactorBadgeColumn(label = "Voice", level = prediction.voiceLevel)
                    FactorBadgeColumn(label = "Location", level = prediction.locationLevel)
                    FactorBadgeColumn(label = "Persistence", level = prediction.persistenceLevel)
                }

                if (prediction.durationInCurrentStateSeconds > 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Risk has remained elevated for ${prediction.durationInCurrentStateSeconds} seconds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun FactorBadgeColumn(label: String, level: ContributionLevel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        ContributionLevelBadge(level = level)
    }
}

@Composable
fun HospitableProtectionCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isEnabled) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        label = "containerColor"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = BorderStroke(
            1.dp,
            if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = if (isEnabled) "Active Protection" else "Protection Inactive",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isEnabled) "Foreground sensor analysis running" else "Phone sensors idle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}

@Composable
fun HospitableSensorTile(
    name: String,
    statusText: String,
    isActive: Boolean,
    isAvailable: Boolean,
    detailText: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (detailText != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = detailText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val (badgeBg, badgeTextColor, label) = when {
                !isAvailable -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "UNAVAILABLE")
                isActive -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.secondary, statusText.uppercase())
                else -> Triple(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.colorScheme.onSurfaceVariant, "OFF")
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeTextColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun HospitableSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}
