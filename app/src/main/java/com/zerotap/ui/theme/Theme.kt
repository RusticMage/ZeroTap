package com.zerotap.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.zerotap.domain.model.ContributionLevel
import com.zerotap.domain.model.RiskState
import com.zerotap.domain.model.TemporalRiskState

private val LightColorScheme = lightColorScheme(
    primary = ZtTerracotta,
    onPrimary = Color.White,
    primaryContainer = ZtTerracottaContainer,
    onPrimaryContainer = ZtWarmCharcoal,

    secondary = ZtSage,
    onSecondary = Color.White,
    secondaryContainer = ZtSageContainer,
    onSecondaryContainer = ZtWarmCharcoal,

    background = ZtWarmIvory,
    onBackground = ZtWarmCharcoal,

    surface = ZtWarmSurface,
    onSurface = ZtWarmCharcoal,
    surfaceVariant = ZtWarmSurfaceVariant,
    onSurfaceVariant = ZtWarmTextSecondary,

    outline = ZtWarmCardBorder,
    outlineVariant = ZtWarmCream,

    error = ZtHighRiskRust,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = ZtDarkPrimaryAccent,
    onPrimary = ZtDarkBackground,
    primaryContainer = Color(0xFF332520),
    onPrimaryContainer = ZtDarkPrimaryText,

    secondary = ZtDarkSecondaryAccent,
    onSecondary = ZtDarkBackground,
    secondaryContainer = Color(0xFF242C27),
    onSecondaryContainer = ZtDarkPrimaryText,

    background = ZtDarkBackground,
    onBackground = ZtDarkPrimaryText,

    surface = ZtDarkSurface,
    onSurface = ZtDarkPrimaryText,
    surfaceVariant = Color(0xFF2A2522),
    onSurfaceVariant = ZtDarkSecondaryText,

    outline = ZtDarkDivider,
    outlineVariant = Color(0xFF3D3633),

    error = ZtHighRiskRust,
    onError = Color.White
)

fun riskStateColor(state: RiskState): Color {
    return when (state) {
        RiskState.NORMAL -> ZtSafeOlive
        RiskState.WATCH -> ZtWatchAmber
        RiskState.SUSPICIOUS -> ZtSuspiciousClay
        RiskState.HIGH_RISK -> ZtHighRiskRust
        RiskState.INCIDENT -> ZtIncidentCrimson
        RiskState.RESOLVED -> ZtResolvedSage
    }
}

fun temporalStateColor(state: TemporalRiskState): Color {
    return when (state) {
        TemporalRiskState.NORMAL -> ZtSafeOlive
        TemporalRiskState.ELEVATED -> ZtWatchAmber
        TemporalRiskState.HIGH_RISK -> ZtHighRiskRust
        TemporalRiskState.EMERGENCY_PENDING -> ZtIncidentCrimson
        TemporalRiskState.EMERGENCY_TRIGGERED -> ZtIncidentCrimson
    }
}

fun contributionLevelColor(level: ContributionLevel): Color {
    return when (level) {
        ContributionLevel.LOW -> ZtSafeOlive
        ContributionLevel.MODERATE -> ZtWatchAmber
        ContributionLevel.ELEVATED -> ZtSuspiciousClay
        ContributionLevel.HIGH -> ZtHighRiskRust
    }
}

@Composable
fun ZeroTapTheme(
    themeMode: String = "SYSTEM",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
