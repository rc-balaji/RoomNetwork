package com.laconfianza.roommapper.model

import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

enum class Carrier(val label: String, val shortLabel: String) {
    JIO("Jio", "Jio"),
    AIRTEL("Airtel", "Airtel"),
    OTHER("Other", "Other")
}

enum class ScanMode(val label: String) {
    GUIDED("Guided"),
    AUTO("Auto map")
}

enum class UseProfile(val label: String, val description: String) {
    BALANCED("Balanced", "A practical blend for everyday use"),
    VIDEO_CALLS("Video calls", "Reliable upload and low delay"),
    STREAMING("Streaming", "Download speed with consistent delivery"),
    GAMING("Gaming", "Low delay and low variation")
}

data class SignalSnapshot(
    val carrier: Carrier,
    val carrierName: String,
    val technology: String,
    val level: Int?,
    val dbm: Int?,
    val rsrp: Int?,
    val rsrq: Int?,
    val snr: Int?,
    val sourceTimeMs: Long,
    val permissionLimited: Boolean = false,
    val carrierMatched: Boolean = false
) {
    val radioLabel: String
        get() = technology.ifBlank { "Mobile" }

    val strengthLabel: String
        get() = when {
            rsrp != null -> "${rsrp} dBm RSRP"
            dbm != null -> "${dbm} dBm"
            level != null -> "$level/4 bars"
            permissionLimited -> "Permission needed"
            else -> "Unavailable"
        }
}

data class NetworkSnapshot(
    val transport: String = "Cellular",
    val validated: Boolean = false,
    val metered: Boolean = true,
    val networkLabel: String = "Checking…",
    val activeSubscriptionId: Int? = null,
    val routeEpoch: Long = 0L
)

data class Spot(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val heightLabel: String = "Hand height",
    val orientationLabel: String = "Upright",
    val result: TestResult? = null,
    val isVerified: Boolean = false,
    val isEstimated: Boolean = false
)

data class TestResult(
    val carrier: Carrier,
    val score: Int,
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val latencyMs: Long?,
    val jitterMs: Long?,
    val reliabilityPercent: Int,
    val bytesUsed: Long,
    val durationMs: Long,
    val completedAtMs: Long,
    val signal: SignalSnapshot?,
    val routeVerified: Boolean,
    val error: String? = null
) {
    val statusLabel: String
        get() = when {
            error != null -> "Needs recheck"
            !routeVerified -> "Unverified route"
            score >= 80 -> "Excellent"
            score >= 60 -> "Good"
            score >= 35 -> "Fair"
            else -> "Weak"
        }

    val compactSpeed: String
        get() = downloadMbps?.let { "${formatNumber(it)} Mbps down" } ?: "No download result"

    companion object {
        private fun formatNumber(value: Double): String =
            if (value >= 10) "%.0f".format(value) else "%.1f".format(value)
    }
}

data class Room(
    val id: String,
    val name: String,
    val widthMeters: Float = 3.0f,
    val heightMeters: Float = 3.0f,
    val spots: List<Spot> = emptyList(),
    val isPreview: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class HistoryEntry(
    val id: String,
    val roomName: String,
    val carrier: Carrier,
    val score: Int,
    val spotName: String,
    val status: String,
    val completedAtMs: Long
) {
    val formattedDate: String
        get() = DateTimeFormatter.ISO_LOCAL_DATE
            .format(Instant.ofEpochMilli(completedAtMs).atZone(java.time.ZoneId.systemDefault()))
}

data class ScanUiState(
    val selectedTab: AppTab = AppTab.HOME,
    val room: Room = Room(
        id = "demo-room",
        name = "Bedroom",
        widthMeters = 3.2f,
        heightMeters = 3.0f,
        isPreview = true,
        spots = listOf(
            Spot("window", "Window side", 0.78f, 0.18f, result = TestResult(
                carrier = Carrier.JIO, score = 92, downloadMbps = 86.4, uploadMbps = 18.2,
                latencyMs = 28, jitterMs = 5, reliabilityPercent = 99, bytesUsed = 1_572_864,
                durationMs = 32_000, completedAtMs = System.currentTimeMillis() - 86_400_000,
                signal = null, routeVerified = true
            ), isVerified = true),
            Spot("desk", "Desk", 0.50f, 0.58f, result = TestResult(
                carrier = Carrier.AIRTEL, score = 74, downloadMbps = 54.1, uploadMbps = 12.7,
                latencyMs = 41, jitterMs = 11, reliabilityPercent = 97, bytesUsed = 1_048_576,
                durationMs = 28_000, completedAtMs = System.currentTimeMillis() - 172_800_000,
                signal = null, routeVerified = true
            ), isVerified = true),
            Spot("door", "Door side", 0.15f, 0.82f, result = null, isVerified = false)
        )
    ),
    val selectedCarrier: Carrier = Carrier.JIO,
    val selectedProfile: UseProfile = UseProfile.BALANCED,
    val selectedMode: ScanMode = ScanMode.GUIDED,
    val budgetMb: Int = 25,
    val isScanning: Boolean = false,
    val isMeasuring: Boolean = false,
    val scanProgress: Float = 0f,
    val scanStatus: String = "Ready to scan",
    val selectedSpotId: String? = "window",
    val signal: SignalSnapshot? = null,
    val network: NetworkSnapshot = NetworkSnapshot(),
    val history: List<HistoryEntry> = emptyList(),
    val arAvailable: Boolean = false,
    val requiredPermissionsGranted: Boolean = false,
    val errorMessage: String? = null,
    val lastCompletedAtMs: Long? = null
) {
    val selectedSpot: Spot?
        get() = room.spots.firstOrNull { it.id == selectedSpotId }

    val bestSpot: Spot?
        get() = room.spots
            .filter { it.isVerified && it.result?.error == null }
            .maxByOrNull { it.result?.score ?: 0 }

    val verifiedCount: Int
        get() = room.spots.count { it.isVerified }

    val coveragePercent: Int
        get() = min(100, max(0, verifiedCount * 20))
}

enum class AppTab(val label: String) {
    HOME("Overview"),
    SCAN("Scan"),
    HISTORY("History"),
    SETTINGS("Settings")
}
