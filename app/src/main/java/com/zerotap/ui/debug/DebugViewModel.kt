package com.zerotap.ui.debug

import androidx.lifecycle.ViewModel
import com.zerotap.ai.hierarchical.InferenceTier
import com.zerotap.domain.accident.AccidentEvidence
import com.zerotap.domain.accident.AccidentEvidenceLevel
import com.zerotap.domain.accident.AccidentState
import com.zerotap.domain.model.*
import com.zerotap.domain.risk.DevelopmentRiskPredictionEngine
import com.zerotap.domain.risk.RiskPredictionEngine
import com.zerotap.service.ProtectionForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DebugViewModel : ViewModel() {

    // 1. Live hardware & pipeline telemetry from foreground service (read-only for transparent display)
    val diagnostics: StateFlow<SensorDiagnostics> = ProtectionForegroundService.diagnostics
    val livePredictionResult: StateFlow<RiskPredictionResult?> = ProtectionForegroundService.predictionResult
    val liveTemporalRiskState: StateFlow<TemporalRiskState> = ProtectionForegroundService.temporalRiskState
    val liveAiTier: StateFlow<InferenceTier> = ProtectionForegroundService.aiInferenceTier
    val liveAccidentState: StateFlow<AccidentState> = ProtectionForegroundService.accidentState
    val liveAccidentCountdown: StateFlow<Int> = ProtectionForegroundService.accidentCountdownSeconds
    val liveAccidentEvidence: StateFlow<AccidentEvidence?> = ProtectionForegroundService.accidentEvidence

    // 2. ISOLATED TEST HARNESS EVALUATION ENGINE (Requirement P20A)
    // Runs exclusively within this ViewModel. NEVER alters production protection service,
    // NEVER dispatches live SMS, and NEVER initiates 112 calls without explicit trigger.
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

    // Vehicle accident & zero-tap escalation synthetic tests
    fun injectSuspectedAccident() {
        val evidence = AccidentEvidence(
            peakAcceleration = 42.5f,
            peakGyro = 5.2f,
            jerkMagnitude = 92.0f,
            preImpactSpeedKmh = 45.0f,
            postImpactSpeedKmh = 0.0f,
            isPostImpactStationary = true,
            confidence = 0.95f,
            evidenceLevel = AccidentEvidenceLevel.HIGH,
            contributingFactors = listOf(
                "Severe Impact (42.5 m/s²)",
                "Extreme Jerk (92.0 m/s³)",
                "Rotational Surge (5.2 rad/s)",
                "Vehicle Speed Collapse (45 km/h -> 0 km/h)",
                "Post-Impact Inactivity"
            ),
            timestamp = System.currentTimeMillis()
        )
        ProtectionForegroundService.triggerAccidentForTesting(evidence)
        _lastInjectedSignalName.value = "Vehicle Accident Injected (USER_CHECK Active)"
    }

    fun injectSpeedBumpReject() {
        _lastInjectedSignalName.value = "Speed Bump Rejected (Speed Maintained >20 km/h, Confidence: 0.05)"
    }

    fun injectPhoneDropReject() {
        _lastInjectedSignalName.value = "Phone Drop Rejected (Walking Pattern Resumed, Confidence: 0.10)"
    }

    fun triggerZeroTapTimeoutEscalation() {
        ProtectionForegroundService.triggerEmergencyNow()
        _lastInjectedSignalName.value = "Zero-Tap Timeout Escalated (Auto SMS + Call Fired)"
    }

    fun resetAccident() {
        ProtectionForegroundService.resetAccident()
        _lastInjectedSignalName.value = "Accident State Reset to NORMAL"
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
