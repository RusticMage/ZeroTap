package com.zerotap.sensor.audio

import com.zerotap.domain.model.AudioClassification
import com.zerotap.domain.model.AudioContext
import com.zerotap.domain.model.AudioMetadata
import com.zerotap.domain.model.AudioPrediction
import com.zerotap.util.Logger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adaptive On-Device Acoustic Engine for ZeroTap.
 *
 * Implements:
 * 1. Dynamic acoustic baseline tracking (EMA) to adapt to environments (quiet rooms, campuses, buses, heavy traffic).
 * 2. Temporal persistence and hysteresis to filter single-frame transient spikes (horns, door slams).
 * 3. Spectral zero-crossing rate and peak-to-RMS energy analysis for vocal/impact discrimination.
 * 4. Contextual audio anomaly scoring (0.0 to 1.0) feeding into the multi-signal Risk Engine.
 * 5. Bounded audio influence — audio provides evidence, but NEVER independently triggers an emergency.
 */
class RobustAudioEngine(
    val baselineTracker: AudioBaselineTracker = AudioBaselineTracker(),
    val temporalTracker: AcousticTemporalTracker = AcousticTemporalTracker()
) : AudioInferenceEngine {

    override val engineName: String = "ZeroTap Adaptive Acoustic Engine v2.0"

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    /**
     * Standard AudioInferenceEngine classification interface.
     */
    override suspend fun classify(
        metadata: AudioMetadata,
        recentHistory: List<AudioMetadata>
    ): AudioPrediction {
        val context = analyzeToContext(metadata, recentHistory)
        return AudioPrediction(
            classification = context.detectedClass,
            confidence = context.confidence,
            timestamp = metadata.timestamp
        )
    }

    /**
     * Full feature extraction and acoustic context generation.
     */
    fun analyzeToContext(
        metadata: AudioMetadata,
        recentHistory: List<AudioMetadata>
    ): AudioContext {
        try {
            val currentDb = metadata.amplitudeDb
            val rmsDb = metadata.rmsDb
            val zcr = metadata.zeroCrossingRate

            // Update dynamic baseline with current acoustic floor
            baselineTracker.update(if (rmsDb > 0f) rmsDb else currentDb)

            val baseline = baselineTracker.baselineDb
            val deviation = baselineTracker.getDeviationDb(currentDb)
            val env = baselineTracker.getEnvironmentType()

            // 1. Candidate Classification & Raw Anomaly Score
            val (candidateClass, rawScore, confidence) = evaluateCandidate(
                currentDb = currentDb,
                rmsDb = rmsDb,
                zcr = zcr,
                deviation = deviation,
                baseline = baseline,
                env = env
            )

            // 2. Temporal Persistence & Hysteresis Filtering
            val (smoothedAnomaly, confirmedClass) = temporalTracker.processFrame(rawScore, candidateClass)

            // 3. Formatted Debug Telemetry (Required by ZeroTap Acoustic Protocol)
            logDebugTelemetry(
                timestamp = metadata.timestamp,
                confirmedClass = confirmedClass,
                confidence = confidence,
                ambientDb = currentDb,
                baselineDb = baseline,
                anomalyScore = smoothedAnomaly
            )

            val isVoice = candidateClass == AudioClassification.SPEECH ||
                    candidateClass == AudioClassification.SHOUTING ||
                    candidateClass == AudioClassification.DISTRESS_SOUND ||
                    confirmedClass == AudioClassification.SPEECH ||
                    confirmedClass == AudioClassification.SHOUTING ||
                    confirmedClass == AudioClassification.DISTRESS_SOUND

            val isElevatedVocal = candidateClass == AudioClassification.SHOUTING ||
                    candidateClass == AudioClassification.DISTRESS_SOUND ||
                    confirmedClass == AudioClassification.SHOUTING ||
                    confirmedClass == AudioClassification.DISTRESS_SOUND

            val isDistress = confirmedClass == AudioClassification.DISTRESS_SOUND
            val isImpact = confirmedClass == AudioClassification.LOUD_ACOUSTIC_EVENT

            return AudioContext(
                timestamp = metadata.timestamp,
                detectedClass = confirmedClass,
                confidence = confidence,
                anomalyScore = smoothedAnomaly,
                ambientLevelDb = currentDb,
                baselineDb = baseline,
                isBaselineWarmedUp = baselineTracker.isWarmedUp,
                modelAvailable = true,
                voiceActivityDetected = isVoice,
                elevatedVocalEnergy = isElevatedVocal,
                distressLikePattern = isDistress,
                loudImpactDetected = isImpact,
                classificationLabel = confirmedClass.displayName
            )
        } catch (e: Exception) {
            Logger.sensor("AudioEngine", "Acoustic evaluation error: ${e.message}")
            return AudioContext(
                timestamp = metadata.timestamp,
                detectedClass = AudioClassification.NORMAL,
                confidence = 0.5f,
                anomalyScore = 0.0f,
                ambientLevelDb = metadata.amplitudeDb,
                baselineDb = 45f,
                isBaselineWarmedUp = false,
                modelAvailable = false,
                classificationLabel = "Unavailable"
            )
        }
    }

    private fun evaluateCandidate(
        currentDb: Float,
        rmsDb: Float,
        zcr: Float,
        deviation: Float,
        baseline: Float,
        env: AcousticEnvironment
    ): Triple<AudioClassification, Float, Float> {
        // A. Extremely quiet
        if (currentDb < 25f && deviation < 3f) {
            return Triple(AudioClassification.SILENCE, 0.01f, 0.85f)
        }

        // B. Heavy Ambient Noise (Traffic / Bus / Train / Highway)
        // High continuous baseline, low high-frequency content (low ZCR), small deviation
        if (env == AcousticEnvironment.VERY_LOUD_AMBIENT && deviation < 10f && zcr < 0.10f) {
            return Triple(AudioClassification.TRAFFIC, 0.05f, 0.88f)
        }

        // C. Loud Ambient (Campus / Cafeteria / Busy Street)
        // Loud background, but within the expected ambient baseline
        if (env == AcousticEnvironment.LOUD_AMBIENT && deviation < 8f) {
            return Triple(AudioClassification.NORMAL, 0.06f, 0.85f)
        }

        // D. Sudden Acoustic Impact (Crash, glass, sharp impact spike)
        val peakToRms = currentDb - rmsDb
        if (currentDb > 85f && (peakToRms > 20f || deviation > 28f)) {
            val score = ((currentDb - 75f) / 30f).coerceIn(0.6f, 0.95f)
            return Triple(AudioClassification.LOUD_ACOUSTIC_EVENT, score, 0.82f)
        }

        // E. Vocal Distress / Screaming / Shouting
        // High acoustic energy + high ZCR (fricatives/screaming vocal signatures) + large deviation
        val hasVocalZcr = zcr in 0.12f..0.45f
        if (hasVocalZcr && deviation > 18f && currentDb > 70f) {
            // Sustained high vocal energy well above baseline
            val score = (0.50f + (deviation / 40f)).coerceIn(0.65f, 0.95f)
            return Triple(AudioClassification.DISTRESS_SOUND, score, 0.84f)
        }

        if (hasVocalZcr && deviation > 12f && currentDb > 65f) {
            // Shouting / loud speech above baseline
            val score = (0.35f + (deviation / 50f)).coerceIn(0.40f, 0.70f)
            return Triple(AudioClassification.SHOUTING, score, 0.80f)
        }

        // F. Normal Conversational Speech
        if (zcr in 0.06f..0.25f && deviation < 12f && currentDb in 45f..68f) {
            return Triple(AudioClassification.SPEECH, 0.08f, 0.85f)
        }

        // G. Generic Loud Noise (e.g. transient horn, engine rev)
        if (deviation > 12f || currentDb > 78f) {
            val score = ((deviation) / 35f).coerceIn(0.20f, 0.45f)
            return Triple(AudioClassification.LOUD_NOISE, score, 0.75f)
        }

        // H. Normal Ambient
        return Triple(AudioClassification.NORMAL, 0.03f, 0.90f)
    }

    private fun logDebugTelemetry(
        timestamp: Long,
        confirmedClass: AudioClassification,
        confidence: Float,
        ambientDb: Float,
        baselineDb: Float,
        anomalyScore: Float
    ) {
        val timeStr = timeFormat.format(Date(timestamp))
        val normalizedAmbient = (ambientDb / 100f).coerceIn(0f, 1f)
        val normalizedBaseline = (baselineDb / 100f).coerceIn(0f, 1f)

        // Structured developer telemetry adhering to requirement Section 17
        Logger.sensor(
            "AudioEngine",
            "$timeStr\nclass=${confirmedClass.name.lowercase()}\nconfidence=%.2f\nambient=%.2f (%.1fdB)\nbaseline=%.2f (%.1fdB)\nanomaly=%.2f".format(
                confidence,
                normalizedAmbient,
                ambientDb,
                normalizedBaseline,
                baselineDb,
                anomalyScore
            )
        )
    }

    fun reset() {
        baselineTracker.reset()
        temporalTracker.reset()
    }
}
