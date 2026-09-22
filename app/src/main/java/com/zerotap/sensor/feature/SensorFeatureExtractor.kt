package com.zerotap.sensor.feature

import com.zerotap.domain.model.MotionSample
import com.zerotap.domain.model.MotionWindow
import kotlin.math.sqrt

/**
 * Extracted statistical and kinematic features from a motion window.
 * Decoupled from raw sensor hardware and downstream classifiers.
 * Ready for future on-device ML (e.g. TFLite, ONNX, Qualcomm QNN).
 */
data class ExtractedMotionFeatures(
    val meanAccelMagnitude: Float,
    val peakAccelMagnitude: Float,
    val minAccelMagnitude: Float,
    val varianceAccelMagnitude: Float,
    val stdDevAccelMagnitude: Float,
    val meanGyroMagnitude: Float,
    val peakGyroMagnitude: Float,
    val jerkMagnitude: Float,
    val sampleCount: Int,
    val durationMs: Long
)

interface SensorFeatureExtractor {
    fun extractMotionFeatures(window: MotionWindow): ExtractedMotionFeatures
}

class StandardSensorFeatureExtractor : SensorFeatureExtractor {
    override fun extractMotionFeatures(window: MotionWindow): ExtractedMotionFeatures {
        val samples = window.samples
        if (samples.isEmpty()) {
            return ExtractedMotionFeatures(
                meanAccelMagnitude = 0f,
                peakAccelMagnitude = 0f,
                minAccelMagnitude = 0f,
                varianceAccelMagnitude = 0f,
                stdDevAccelMagnitude = 0f,
                meanGyroMagnitude = 0f,
                peakGyroMagnitude = 0f,
                jerkMagnitude = 0f,
                sampleCount = 0,
                durationMs = window.durationMs
            )
        }

        var sumAccel = 0f
        var maxAccel = Float.MIN_VALUE
        var minAccel = Float.MAX_VALUE
        var sumGyro = 0f
        var maxGyro = Float.MIN_VALUE
        var totalJerk = 0f

        val accelMags = FloatArray(samples.size)

        for (i in samples.indices) {
            val sample = samples[i]
            val aMag = sample.accelerationMagnitude
            accelMags[i] = aMag
            sumAccel += aMag
            if (aMag > maxAccel) maxAccel = aMag
            if (aMag < minAccel) minAccel = aMag

            val gMag = sample.gyroMagnitude
            sumGyro += gMag
            if (gMag > maxGyro) maxGyro = gMag

            if (i > 0) {
                val prev = samples[i - 1]
                val dt = (sample.timestamp - prev.timestamp).coerceAtLeast(1L) / 1000f
                val deltaA = aMag - prev.accelerationMagnitude
                totalJerk += kotlin.math.abs(deltaA / dt)
            }
        }

        val count = samples.size
        val meanAccel = sumAccel / count
        val meanGyro = sumGyro / count
        val avgJerk = if (count > 1) totalJerk / (count - 1) else 0f

        var varianceSum = 0f
        for (mag in accelMags) {
            val diff = mag - meanAccel
            varianceSum += diff * diff
        }
        val varianceAccel = varianceSum / count
        val stdDevAccel = sqrt(varianceAccel.toDouble()).toFloat()

        return ExtractedMotionFeatures(
            meanAccelMagnitude = meanAccel,
            peakAccelMagnitude = if (maxAccel == Float.MIN_VALUE) 0f else maxAccel,
            minAccelMagnitude = if (minAccel == Float.MAX_VALUE) 0f else minAccel,
            varianceAccelMagnitude = varianceAccel,
            stdDevAccelMagnitude = stdDevAccel,
            meanGyroMagnitude = meanGyro,
            peakGyroMagnitude = if (maxGyro == Float.MIN_VALUE) 0f else maxGyro,
            jerkMagnitude = avgJerk,
            sampleCount = count,
            durationMs = window.durationMs
        )
    }
}
