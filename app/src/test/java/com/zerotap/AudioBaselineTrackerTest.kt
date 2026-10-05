package com.zerotap

import com.zerotap.sensor.audio.AcousticEnvironment
import com.zerotap.sensor.audio.AudioBaselineTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBaselineTrackerTest {

    @Test
    fun testWarmupBehavior() {
        val tracker = AudioBaselineTracker(warmupSampleCount = 6)
        assertFalse(tracker.isWarmedUp)

        // Provide 5 samples
        repeat(5) { tracker.update(50f) }
        assertFalse(tracker.isWarmedUp)

        // 6th sample completes warm-up
        tracker.update(50f)
        assertTrue(tracker.isWarmedUp)
    }

    @Test
    fun testQuietRoomBaselineLearning() {
        val tracker = AudioBaselineTracker(warmupSampleCount = 6)
        // Simulate quiet room (~36 dB)
        repeat(20) { tracker.update(36f) }

        assertTrue(tracker.isWarmedUp)
        assertTrue("Baseline should adapt close to 36 dB", tracker.baselineDb in 35f..38f)
        assertEquals(AcousticEnvironment.QUIET, tracker.getEnvironmentType())
    }

    @Test
    fun testLoudCampusBaselineLearning() {
        val tracker = AudioBaselineTracker(warmupSampleCount = 6)
        // Simulate loud college campus (~72 dB)
        repeat(25) { tracker.update(72f) }

        assertTrue(tracker.isWarmedUp)
        assertTrue("Baseline should adapt to ~72 dB", tracker.baselineDb in 70f..74f)
        assertEquals(AcousticEnvironment.LOUD_AMBIENT, tracker.getEnvironmentType())

        // In a loud campus, a 74 dB sound is NOT anomalous
        val deviation = tracker.getDeviationDb(74f)
        assertTrue("Deviation should be very small (< 4 dB)", deviation < 4f)
    }

    @Test
    fun testHeavyTrafficBaselineLearning() {
        val tracker = AudioBaselineTracker(warmupSampleCount = 6)
        // Simulate heavy traffic / bus (~80 dB)
        repeat(30) { tracker.update(80f) }

        assertTrue(tracker.isWarmedUp)
        assertTrue("Baseline should adapt to ~80 dB", tracker.baselineDb >= 78f)
        assertEquals(AcousticEnvironment.VERY_LOUD_AMBIENT, tracker.getEnvironmentType())
    }

    @Test
    fun testAsymmetricSpikeRejection() {
        val tracker = AudioBaselineTracker(warmupSampleCount = 6)
        // Establish quiet baseline (~40 dB)
        repeat(15) { tracker.update(40f) }
        val baselineBeforeSpike = tracker.baselineDb
        assertTrue("Baseline before spike should be ~40 dB", baselineBeforeSpike in 39f..42f)

        // A single sudden spike (e.g. horn or door slam: 85 dB)
        tracker.update(85f)

        // Baseline must NOT jump to 85 dB!
        val baselineAfterSpike = tracker.baselineDb
        assertTrue("Baseline should resist sudden spikes", baselineAfterSpike < 45f)
        assertTrue("Spike deviation must remain prominent (> 40 dB)", tracker.getDeviationDb(85f) > 40f)
    }
}
