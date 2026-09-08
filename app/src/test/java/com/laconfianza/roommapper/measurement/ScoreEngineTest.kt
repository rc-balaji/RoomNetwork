package com.laconfianza.roommapper.measurement

import com.laconfianza.roommapper.model.UseProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreEngineTest {
    @Test
    fun perfectBalancedInputsProduceMaximumScore() {
        val score = ScoreEngine.score(
            profile = UseProfile.BALANCED,
            reliabilityPercent = 100,
            latencyMs = 0,
            jitterMs = 0,
            downloadMbps = 200.0,
            uploadMbps = 100.0
        )
        assertEquals(100, score)
    }

    @Test
    fun missingMeasurementsDoNotCreateAFalsePositive() {
        val score = ScoreEngine.score(
            profile = UseProfile.STREAMING,
            reliabilityPercent = 0,
            latencyMs = null,
            jitterMs = null,
            downloadMbps = null,
            uploadMbps = null
        )
        assertEquals(0, score)
    }

    @Test
    fun scoreIsAlwaysBounded() {
        val score = ScoreEngine.score(
            profile = UseProfile.GAMING,
            reliabilityPercent = 200,
            latencyMs = -100,
            jitterMs = -100,
            downloadMbps = 10_000.0,
            uploadMbps = 10_000.0
        )
        assertTrue(score in 0..100)
    }
}
