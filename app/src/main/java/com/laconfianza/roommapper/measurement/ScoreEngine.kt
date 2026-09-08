package com.laconfianza.roommapper.measurement

import com.laconfianza.roommapper.model.UseProfile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ScoreEngine {
    fun score(
        profile: UseProfile,
        reliabilityPercent: Int,
        latencyMs: Long?,
        jitterMs: Long?,
        downloadMbps: Double?,
        uploadMbps: Double?
    ): Int {
        val reliability = reliabilityPercent.coerceIn(0, 100).toDouble()
        val latency = latencyMs?.let { (100.0 - it.coerceIn(0, 500) / 5.0).coerceIn(0.0, 100.0) } ?: 0.0
        val jitter = jitterMs?.let { (100.0 - it.coerceIn(0, 250) / 2.5).coerceIn(0.0, 100.0) } ?: 0.0
        val download = downloadMbps?.let { (it.coerceIn(0.0, 200.0) / 200.0 * 100).coerceIn(0.0, 100.0) } ?: 0.0
        val upload = uploadMbps?.let { (it.coerceIn(0.0, 100.0) / 100.0 * 100).coerceIn(0.0, 100.0) } ?: 0.0
        val value = when (profile) {
            UseProfile.BALANCED -> reliability * .40 + latency * .15 + jitter * .10 + download * .20 + upload * .15
            UseProfile.VIDEO_CALLS -> reliability * .40 + latency * .15 + jitter * .10 + download * .10 + upload * .25
            UseProfile.STREAMING -> reliability * .30 + latency * .05 + jitter * .05 + download * .50 + upload * .10
            UseProfile.GAMING -> reliability * .40 + latency * .35 + jitter * .15 + download * .05 + upload * .05
        }
        return min(100, max(0, value.roundToInt()))
    }
}
