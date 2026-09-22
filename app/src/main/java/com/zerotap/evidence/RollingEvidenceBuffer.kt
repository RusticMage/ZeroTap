package com.zerotap.evidence

import com.zerotap.domain.model.AudioMetadata
import com.zerotap.domain.model.LocationSample
import com.zerotap.domain.model.MotionSample
import com.zerotap.util.RollingBuffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EvidenceSnapshot(
    val motionSamples: List<MotionSample>,
    val locationSamples: List<LocationSample>,
    val audioMetadata: List<AudioMetadata>,
    val timestamp: Long = System.currentTimeMillis()
)

class RollingEvidenceBuffer {
    private val motionBuffer = RollingBuffer<MotionSample>(600)
    private val locationBuffer = RollingBuffer<LocationSample>(12)
    private val audioBuffer = RollingBuffer<AudioMetadata>(120)
    private val mutex = Mutex()

    suspend fun addMotion(sample: MotionSample) = mutex.withLock {
        motionBuffer.add(sample)
    }

    suspend fun addLocation(sample: LocationSample) = mutex.withLock {
        locationBuffer.add(sample)
    }

    suspend fun addAudio(metadata: AudioMetadata) = mutex.withLock {
        audioBuffer.add(metadata)
    }

    suspend fun freeze(): EvidenceSnapshot = mutex.withLock {
        return EvidenceSnapshot(
            motionSamples = motionBuffer.getSnapshot(),
            locationSamples = locationBuffer.getSnapshot(),
            audioMetadata = audioBuffer.getSnapshot()
        )
    }

    suspend fun clear() = mutex.withLock {
        motionBuffer.clear()
        locationBuffer.clear()
        audioBuffer.clear()
    }
}
