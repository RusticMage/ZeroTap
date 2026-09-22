package com.zerotap.ui.debug

import androidx.lifecycle.ViewModel
import com.zerotap.domain.model.*
import com.zerotap.domain.risk.DevelopmentRiskPredictionEngine
import com.zerotap.domain.risk.RiskPredictionEngine
import com.zerotap.service.ProtectionForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DebugViewModel : ViewModel() {

    // 1. Live hardware telemetry from foreground service (read-only for transparent display)
    val diagnostics: StateFlow<SensorDiagnostics> = ProtectionForegroundService.diagnostics
    val livePredictionResult: StateFlow<RiskPredictionResult?> = ProtectionForegroundService.predictionResult
    val liveTemporalRiskState: StateFlow<TemporalRiskState> = ProtectionForegroundService.temporalRiskState

    // 2. ISOLATED TEST HARNESS EVALUATION ENGINE (Requirement P20A)
    // Runs exclusively within this ViewModel. NEVER alters production protection service,
    // NEVER dispatches live SMS, and NEVER initiates 112 calls.
    private val testRiskPredictionEngine: RiskPredictionEngine = DevelopmentRiskPredictionEngine()

    private var syntheticMotion: MotionContext? = null
    private var syntheticAudio: AudioContext? = null
    private var syntheticLocation: LocationContext? = null
    private var syntheticTemporalState: TemporalRiskState = TemporalRiskState.NORMAL

    // Baseline initial evaluation
    private val _syntheticRiskResult = MutableStateFlow<RiskPredictionResult>(
        testRiskPredictionEngine.evaluate(
            UnifiedSensorContext(motion = null, audio = null, location = null, timestamp = System.currentTimeMillis()),
            TemporalRiskState.NORMAL
        )
    )
    val syntheticRiskResult: StateFlow<RiskPredictionResult> = _syntheticRiskResult.asStateFlow()

    private val _lastInjectedSignalName = MutableStateFlow<String>("Baseline (No Injections)")
    val lastInjectedSignalName: StateFlow<String> = _lastInjectedSignalName.asStateFlow()

    fun injectSuddenJerk() {
        val now = System.currentTimeMillis()
        syntheticMotion = MotionContext(
            timestamp = now,
            stationaryConfidence = 0.0f,
            walkingConfidence = 0.10f,
            runningConfidence = 0.05f,
            abruptMotionConfidence = 0.88f,
            impactConfidence = 0.40f,
            fallConfidence = 0.20f,
            peakAcceleration = 22.8f,
            meanAcceleration = 14.5f,
            jerkMagnitude = 72.4f,
            peakGyro = 4.2f
        )
        _lastInjectedSignalName.value = "Sudden Jerk Kinematics (+Motion Risk)"
        recalculateSyntheticRisk()
    }

    fun injectDistressAudio() {
        val now = System.currentTimeMillis()
        syntheticAudio = AudioContext(
            timestamp = now,
            voiceActivityDetected = true,
            elevatedVocalEnergy = true,
            distressLikePattern = true,
            loudImpactDetected = false,
            ambientLevelDb = 82.5f,
            classificationLabel = "Distress Sound",
            confidence = 0.92f
        )
        _lastInjectedSignalName.value = "Distress Acoustic Pattern (+Voice Risk)"
        recalculateSyntheticRisk()
    }

    fun injectUnexpectedStop() {
        val now = System.currentTimeMillis()
        syntheticLocation = LocationContext(
            timestamp = now,
            latitude = 13.0418,
            longitude = 80.2341,
            accuracy = 4.2f,
            speed = 0.0f,
            bearing = 0.0f,
            isMoving = false,
            isUnexpectedStop = true,
            stopDurationMs = 45000L,
            historicalSafetyContext = null
        )
        _lastInjectedSignalName.value = "Unexpected Stop (>30s stationary)"
        recalculateSyntheticRisk()
    }

    fun injectCombinedIncident() {
        val now = System.currentTimeMillis()
        syntheticMotion = MotionContext(
            timestamp = now,
            stationaryConfidence = 0.0f,
            walkingConfidence = 0.0f,
            runningConfidence = 0.0f,
            abruptMotionConfidence = 0.92f,
            impactConfidence = 0.85f,
            fallConfidence = 0.80f,
            peakAcceleration = 28.5f,
            meanAcceleration = 18.2f,
            jerkMagnitude = 95.0f,
            peakGyro = 6.8f
        )
        syntheticAudio = AudioContext(
            timestamp = now,
            voiceActivityDetected = true,
            elevatedVocalEnergy = true,
            distressLikePattern = true,
            loudImpactDetected = true,
            ambientLevelDb = 89.0f,
            classificationLabel = "Distress Sound + High Decibel Spike",
            confidence = 0.95f
        )
        syntheticLocation = LocationContext(
            timestamp = now,
            latitude = 13.0418,
            longitude = 80.2341,
            accuracy = 3.5f,
            speed = 0.0f,
            bearing = 0.0f,
            isMoving = false,
            isUnexpectedStop = true,
            stopDurationMs = 65000L,
            historicalSafetyContext = null
        )
        syntheticTemporalState = TemporalRiskState.HIGH_RISK
        _lastInjectedSignalName.value = "Multi-Modal Incident (Motion + Audio + Location)"
        recalculateSyntheticRisk()
    }

    fun resetSyntheticSignals() {
        syntheticMotion = null
        syntheticAudio = null
        syntheticLocation = null
        syntheticTemporalState = TemporalRiskState.NORMAL
        _lastInjectedSignalName.value = "Baseline (Cleared)"
        recalculateSyntheticRisk()
    }

    private fun recalculateSyntheticRisk() {
        val context = UnifiedSensorContext(
            motion = syntheticMotion,
            audio = syntheticAudio,
            location = syntheticLocation,
            timestamp = System.currentTimeMillis()
        )
        val result = testRiskPredictionEngine.evaluate(context, syntheticTemporalState)
        _syntheticRiskResult.value = result
    }
}
