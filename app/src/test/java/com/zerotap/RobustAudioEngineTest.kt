package com.zerotap

import com.zerotap.domain.model.AudioClassification
import com.zerotap.domain.model.AudioMetadata
import com.zerotap.sensor.audio.RobustAudioEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobustAudioEngineTest {

    @Test
    fun testQuietRoomProcessing() = runBlocking {
        val engine = RobustAudioEngine()
        val now = System.currentTimeMillis()

        // Feed quiet room baseline (35 dB, low ZCR)
        var lastContext = engine.analyzeToContext(
            AudioMetadata(now, amplitudeDb = 35f, isRecording = true, rmsDb = 35f, zeroCrossingRate = 0.04f),
            emptyList()
        )
        repeat(8) { i ->
            lastContext = engine.analyzeToContext(
                AudioMetadata(now + i * 500L, amplitudeDb = 35f, isRecording = true, rmsDb = 35f, zeroCrossingRate = 0.04f),
                emptyList()
            )
        }

        assertTrue("Quiet room anomaly score must be low (< 0.15)", lastContext.anomalyScore < 0.15f)
        assertFalse("No vocal distress in quiet room", lastContext.distressLikePattern)
        assertFalse("No loud impact in quiet room", lastContext.loudImpactDetected)
    }

    @Test
    fun testNormalConversationalSpeech() = runBlocking {
        val engine = RobustAudioEngine()
        val now = System.currentTimeMillis()

        // Moderate background (52 dB)
        repeat(8) { i ->
            engine.analyzeToContext(
                AudioMetadata(now + i * 500L, amplitudeDb = 52f, isRecording = true, rmsDb = 50f, zeroCrossingRate = 0.06f),
                emptyList()
            )
        }

        // Conversational speech (60 dB, moderate ZCR 0.14)
        val speechContext = engine.analyzeToContext(
            AudioMetadata(now + 5000L, amplitudeDb = 60f, isRecording = true, rmsDb = 58f, zeroCrossingRate = 0.14f),
            emptyList()
        )

        assertTrue("Voice activity should be detected", speechContext.voiceActivityDetected)
        assertTrue("Speech anomaly score should be low (< 0.20)", speechContext.anomalyScore < 0.20f)
        assertFalse("Conversational speech must not trigger distress", speechContext.distressLikePattern)
    }

    @Test
    fun testHeavyTrafficAndBusNoiseDoesNotTriggerDistress() = runBlocking {
        val engine = RobustAudioEngine()
        val now = System.currentTimeMillis()

        // Simulate being in heavy traffic or on a bus: sustained loud noise (78 dB, low ZCR ~0.05)
        var ctx = engine.analyzeToContext(
            AudioMetadata(now, amplitudeDb = 78f, isRecording = true, rmsDb = 76f, zeroCrossingRate = 0.05f),
            emptyList()
        )
        repeat(15) { i ->
            ctx = engine.analyzeToContext(
                AudioMetadata(now + i * 500L, amplitudeDb = 78f, isRecording = true, rmsDb = 76f, zeroCrossingRate = 0.05f),
                emptyList()
            )
        }

        // Heavy traffic matches learned baseline -> anomaly score must remain low!
        assertTrue("Traffic anomaly score must be low (< 0.20) once baseline is learned", ctx.anomalyScore < 0.20f)
        assertFalse("Loud traffic must NEVER trigger distress vocal pattern", ctx.distressLikePattern)
        assertFalse("Loud traffic must not be classified as impact", ctx.loudImpactDetected)
    }

    @Test
    fun testTransientHornSpikeIsFilteredByTemporalPersistence() = runBlocking {
        val engine = RobustAudioEngine()
        val now = System.currentTimeMillis()

        // Established campus/traffic ambient baseline (65 dB)
        repeat(10) { i ->
            engine.analyzeToContext(
                AudioMetadata(now + i * 500L, amplitudeDb = 65f, isRecording = true, rmsDb = 63f, zeroCrossingRate = 0.06f),
                emptyList()
            )
        }

        // Frame 1: Transient car horn (85 dB for 500ms)
        val frame1 = engine.analyzeToContext(
            AudioMetadata(now + 6000L, amplitudeDb = 85f, isRecording = true, rmsDb = 83f, zeroCrossingRate = 0.09f),
            emptyList()
        )

        // Single isolated frame should not jump to full distress
        assertFalse("Single horn spike should not be classified as distress", frame1.distressLikePattern)
        assertTrue("Single horn spike smoothed anomaly should remain bounded (< 0.35)", frame1.anomalyScore < 0.35f)

        // Frame 2: Sound returns to normal ambient (65 dB)
        val frame2 = engine.analyzeToContext(
            AudioMetadata(now + 6500L, amplitudeDb = 65f, isRecording = true, rmsDb = 63f, zeroCrossingRate = 0.06f),
            emptyList()
        )

        assertTrue("Anomaly drops back quickly", frame2.anomalyScore < 0.25f)
    }

    @Test
    fun testSustainedDistressSoundDetected() = runBlocking {
        val engine = RobustAudioEngine()
        val now = System.currentTimeMillis()

        // Quiet/moderate ambient baseline (45 dB)
        repeat(10) { i ->
            engine.analyzeToContext(
                AudioMetadata(now + i * 500L, amplitudeDb = 45f, isRecording = true, rmsDb = 43f, zeroCrossingRate = 0.05f),
                emptyList()
            )
        }

        // Frame 1: High vocal distress screaming (78 dB, vocal ZCR 0.22, deviation 33 dB)
        val f1 = engine.analyzeToContext(
            AudioMetadata(now + 6000L, amplitudeDb = 78f, isRecording = true, rmsDb = 75f, zeroCrossingRate = 0.22f),
            emptyList()
        )
        assertTrue("Frame 1 recognizes candidate vocal energy", f1.voiceActivityDetected)

        // Frame 2: Sustained screaming (temporal persistence confirmed)
        val f2 = engine.analyzeToContext(
            AudioMetadata(now + 6500L, amplitudeDb = 80f, isRecording = true, rmsDb = 77f, zeroCrossingRate = 0.24f),
            emptyList()
        )

        assertTrue("Sustained distress must confirm distressLikePattern", f2.distressLikePattern)
        assertTrue("Sustained distress anomaly score must be elevated (> 0.40)", f2.anomalyScore > 0.40f)
        assertEquals(AudioClassification.DISTRESS_SOUND, f2.detectedClass)
    }

    @Test
    fun testAcousticImpactDetection() = runBlocking {
        val engine = RobustAudioEngine()
        val now = System.currentTimeMillis()

        // Normal ambient (48 dB)
        repeat(8) { i ->
            engine.analyzeToContext(
                AudioMetadata(now + i * 500L, amplitudeDb = 48f, isRecording = true, rmsDb = 46f, zeroCrossingRate = 0.05f),
                emptyList()
            )
        }

        // Sudden violent impact spike (92 dB with high peak-to-RMS)
        val impactCtx = engine.analyzeToContext(
            AudioMetadata(now + 5000L, amplitudeDb = 92f, isRecording = true, rmsDb = 68f, zeroCrossingRate = 0.28f),
            emptyList()
        )

        assertTrue("Loud impact detected", impactCtx.loudImpactDetected)
        assertEquals(AudioClassification.LOUD_ACOUSTIC_EVENT, impactCtx.detectedClass)
    }
}
