package com.zerotap.sensor.location

import android.location.Location
import com.zerotap.domain.model.LocationSample
import com.zerotap.util.RollingBuffer

data class LocationAnalysis(
    val isMoving: Boolean,
    val currentSpeed: Float,
    val averageSpeed: Float,
    val isUnexpectedStop: Boolean,
    val stopDurationMs: Long
)

class LocationContextAnalyzer {
    
    fun analyzeMovement(samples: List<LocationSample>): LocationAnalysis {
        if (samples.isEmpty()) {
            return LocationAnalysis(false, 0f, 0f, false, 0)
        }
        
        val currentSpeed = samples.last().speed ?: 0f
        val isMoving = currentSpeed > 0.5f
        
        val validSpeeds = samples.mapNotNull { it.speed }
        val averageSpeed = if (validSpeeds.isNotEmpty()) validSpeeds.average().toFloat() else 0f
        
        val isUnexpectedStop = detectUnexpectedStop(samples)
        var stopDurationMs = 0L
        
        if (!isMoving && samples.size > 1) {
            val stopStart = samples.indexOfLast { (it.speed ?: 0f) > 0.5f }
            if (stopStart != -1 && stopStart < samples.size - 1) {
                stopDurationMs = samples.last().timestamp - samples[stopStart + 1].timestamp
            } else if (stopStart == -1) {
                stopDurationMs = samples.last().timestamp - samples.first().timestamp
            }
        }

        return LocationAnalysis(
            isMoving = isMoving,
            currentSpeed = currentSpeed,
            averageSpeed = averageSpeed,
            isUnexpectedStop = isUnexpectedStop,
            stopDurationMs = stopDurationMs
        )
    }

    fun detectUnexpectedStop(samples: List<LocationSample>): Boolean {
        if (samples.size < 2) return false
        
        val recentSamples = samples.takeLast(10) // Approx 50 seconds if 5s interval
        var maxSpeed = 0f
        var stoppedTimeStart = -1L
        
        for (sample in recentSamples) {
            val speed = sample.speed ?: 0f
            if (speed > 2f) {
                maxSpeed = speed
                stoppedTimeStart = -1L // reset
            } else if (speed < 0.5f && maxSpeed > 2f) {
                if (stoppedTimeStart == -1L) {
                    stoppedTimeStart = sample.timestamp
                } else {
                    if (sample.timestamp - stoppedTimeStart > 30000L) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun detectSpeedAnomaly(samples: List<LocationSample>): Boolean {
        if (samples.size < 3) return false
        val recentSpeed = samples.last().speed ?: return false
        val previousSpeeds = samples.dropLast(1).mapNotNull { it.speed }
        if (previousSpeeds.isEmpty()) return false
        
        val avgPrevious = previousSpeeds.average()
        return recentSpeed > (avgPrevious * 3) && recentSpeed > 5f
    }

    fun calculateRouteDeviation(current: LocationSample, plannedRoute: List<LocationSample>): Float {
        if (plannedRoute.isEmpty()) return 0f
        
        var minDistance = Float.MAX_VALUE
        val results = FloatArray(1)
        
        for (point in plannedRoute) {
            Location.distanceBetween(
                current.latitude, current.longitude,
                point.latitude, point.longitude,
                results
            )
            if (results[0] < minDistance) {
                minDistance = results[0]
            }
        }
        return minDistance
    }
}
