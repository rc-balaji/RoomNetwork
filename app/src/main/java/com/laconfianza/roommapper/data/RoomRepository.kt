package com.laconfianza.roommapper.data

import android.content.Context
import com.laconfianza.roommapper.model.AppTab
import com.laconfianza.roommapper.model.Carrier
import com.laconfianza.roommapper.model.HistoryEntry
import com.laconfianza.roommapper.model.Room
import com.laconfianza.roommapper.model.ScanMode
import com.laconfianza.roommapper.model.Spot
import com.laconfianza.roommapper.model.UseProfile
import com.laconfianza.roommapper.model.TestResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small, private, versioned store for user-owned scan history and preferences.
 * The app keeps room data on-device and intentionally has no account or sync layer.
 */
class RoomRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun loadHistory(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        HistoryEntry(
                            id = item.optString("id"),
                            roomName = item.optString("roomName"),
                            carrier = Carrier.entries.firstOrNull { it.name == item.optString("carrier") }
                                ?: Carrier.OTHER,
                            score = item.optInt("score"),
                            spotName = item.optString("spotName"),
                            status = item.optString("status"),
                            completedAtMs = item.optLong("completedAtMs")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun appendHistory(entry: HistoryEntry) {
        val current = loadHistory().toMutableList()
        current.removeAll { it.id == entry.id }
        current.add(0, entry)
        saveHistory(current.take(MAX_HISTORY))
    }

    fun saveHistory(entries: List<HistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("roomName", entry.roomName)
                    put("carrier", entry.carrier.name)
                    put("score", entry.score)
                    put("spotName", entry.spotName)
                    put("status", entry.status)
                    put("completedAtMs", entry.completedAtMs)
                }
            )
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun loadBudget(defaultValue: Int = 25): Int = prefs.getInt(KEY_BUDGET, defaultValue)

    fun saveBudget(value: Int) {
        prefs.edit().putInt(KEY_BUDGET, value).apply()
    }

    fun loadProfile(defaultValue: UseProfile = UseProfile.BALANCED): UseProfile =
        UseProfile.entries.firstOrNull { it.name == prefs.getString(KEY_PROFILE, null) } ?: defaultValue

    fun saveProfile(value: UseProfile) {
        prefs.edit().putString(KEY_PROFILE, value.name).apply()
    }

    fun loadCarrier(defaultValue: Carrier = Carrier.JIO): Carrier =
        Carrier.entries.firstOrNull { it.name == prefs.getString(KEY_CARRIER, null) } ?: defaultValue

    fun saveCarrier(value: Carrier) {
        prefs.edit().putString(KEY_CARRIER, value.name).apply()
    }

    fun loadMode(defaultValue: ScanMode = ScanMode.GUIDED): ScanMode =
        ScanMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: defaultValue

    fun saveMode(value: ScanMode) {
        prefs.edit().putString(KEY_MODE, value.name).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    fun loadRoom(defaultRoom: Room): Room {
        val raw = prefs.getString(KEY_ROOM, null) ?: return defaultRoom
        return runCatching {
            val json = JSONObject(raw)
            val savedSpots = json.optJSONArray("spots") ?: JSONArray()
            val spots = buildList {
                for (index in 0 until savedSpots.length()) {
                    val item = savedSpots.getJSONObject(index)
                    val carrier = Carrier.entries.firstOrNull { it.name == item.optString("carrier") }
                    val result = if (carrier == null || !item.has("score")) null else TestResult(
                        carrier = carrier,
                        score = item.optInt("score"),
                        downloadMbps = item.optDouble("download", Double.NaN).takeUnless { it.isNaN() },
                        uploadMbps = item.optDouble("upload", Double.NaN).takeUnless { it.isNaN() },
                        latencyMs = item.optLong("latency", -1L).takeUnless { it < 0 },
                        jitterMs = item.optLong("jitter", -1L).takeUnless { it < 0 },
                        reliabilityPercent = item.optInt("reliability"),
                        bytesUsed = item.optLong("bytes"),
                        durationMs = item.optLong("duration"),
                        completedAtMs = item.optLong("completedAt"),
                        signal = null,
                        routeVerified = item.optBoolean("routeVerified"),
                        error = item.optString("error").takeUnless { it.isBlank() }
                    )
                    add(
                        Spot(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            x = item.optDouble("x", .5).toFloat(),
                            y = item.optDouble("y", .5).toFloat(),
                            heightLabel = item.optString("height", "Hand height"),
                            orientationLabel = item.optString("orientation", "Upright"),
                            result = result,
                            isVerified = item.optBoolean("verified"),
                            isEstimated = item.optBoolean("estimated")
                        )
                    )
                }
            }
            defaultRoom.copy(
                name = json.optString("name", defaultRoom.name),
                widthMeters = json.optDouble("width", defaultRoom.widthMeters.toDouble()).toFloat(),
                heightMeters = json.optDouble("height", defaultRoom.heightMeters.toDouble()).toFloat(),
                isPreview = json.optBoolean("preview", false),
                spots = spots.ifEmpty { defaultRoom.spots }
            )
        }.getOrDefault(defaultRoom)
    }

    fun saveRoom(room: Room) {
        val spots = JSONArray()
        room.spots.forEach { spot ->
            spots.put(JSONObject().apply {
                put("id", spot.id)
                put("name", spot.name)
                put("x", spot.x)
                put("y", spot.y)
                put("height", spot.heightLabel)
                put("orientation", spot.orientationLabel)
                put("verified", spot.isVerified)
                put("estimated", spot.isEstimated)
                spot.result?.let { result ->
                    put("carrier", result.carrier.name)
                    put("score", result.score)
                    result.downloadMbps?.let { put("download", it) }
                    result.uploadMbps?.let { put("upload", it) }
                    result.latencyMs?.let { put("latency", it) }
                    result.jitterMs?.let { put("jitter", it) }
                    put("reliability", result.reliabilityPercent)
                    put("bytes", result.bytesUsed)
                    put("duration", result.durationMs)
                    put("completedAt", result.completedAtMs)
                    put("routeVerified", result.routeVerified)
                    result.error?.let { put("error", it) }
                }
            })
        }
        prefs.edit().putString(
            KEY_ROOM,
            JSONObject().apply {
                put("name", room.name)
                put("width", room.widthMeters)
                put("height", room.heightMeters)
                put("preview", room.isPreview)
                put("spots", spots)
            }.toString()
        ).apply()
    }

    companion object {
        private const val PREFERENCES = "room_mapper_private_v1"
        private const val KEY_HISTORY = "history"
        private const val KEY_BUDGET = "budget_mb"
        private const val KEY_PROFILE = "profile"
        private const val KEY_CARRIER = "carrier"
        private const val KEY_MODE = "mode"
        private const val KEY_ROOM = "room"
        private const val MAX_HISTORY = 50
    }
}
