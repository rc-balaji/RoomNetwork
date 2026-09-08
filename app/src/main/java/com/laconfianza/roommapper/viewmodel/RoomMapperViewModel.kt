package com.laconfianza.roommapper.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.core.ArCoreApk
import com.laconfianza.roommapper.data.RoomRepository
import com.laconfianza.roommapper.measurement.MeasurementEngine
import com.laconfianza.roommapper.measurement.NetworkInspector
import com.laconfianza.roommapper.measurement.TelephonySignalReader
import com.laconfianza.roommapper.model.AppTab
import com.laconfianza.roommapper.model.Carrier
import com.laconfianza.roommapper.model.HistoryEntry
import com.laconfianza.roommapper.model.Room
import com.laconfianza.roommapper.model.ScanMode
import com.laconfianza.roommapper.model.ScanUiState
import com.laconfianza.roommapper.model.Spot
import com.laconfianza.roommapper.model.UseProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class RoomMapperViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RoomRepository(application)
    private val inspector = NetworkInspector(application)
    private val signalReader = TelephonySignalReader(application)
    private val measurementEngine = MeasurementEngine(inspector)
    private var scanJob: Job? = null

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    init {
        refreshEnvironment()
    }

    fun selectTab(tab: AppTab) = _uiState.update { it.copy(selectedTab = tab, errorMessage = null) }

    fun selectCarrier(carrier: Carrier) {
        repository.saveCarrier(carrier)
        _uiState.update { it.copy(selectedCarrier = carrier, errorMessage = null) }
        refreshEnvironment()
    }

    fun selectProfile(profile: UseProfile) {
        repository.saveProfile(profile)
        _uiState.update { it.copy(selectedProfile = profile) }
    }

    fun selectMode(mode: ScanMode) {
        repository.saveMode(mode)
        _uiState.update { it.copy(selectedMode = mode, errorMessage = null) }
    }

    fun selectBudget(mb: Int) {
        repository.saveBudget(mb)
        _uiState.update { it.copy(budgetMb = mb.coerceIn(5, 1000)) }
    }

    fun selectSpot(id: String) = _uiState.update { it.copy(selectedSpotId = id) }

    fun markSpot(x: Float, y: Float) {
        val safeX = x.coerceIn(.04f, .96f)
        val safeY = y.coerceIn(.06f, .94f)
        val current = _uiState.value
        val near = current.room.spots.firstOrNull { spot ->
            kotlin.math.abs(spot.x - safeX) < .07f && kotlin.math.abs(spot.y - safeY) < .07f
        }
        if (near != null) {
            selectSpot(near.id)
            return
        }
        val nextNumber = current.room.spots.count { it.id.startsWith("spot-") } + 1
        val spot = Spot(
            id = "spot-${UUID.randomUUID()}",
            name = "Spot $nextNumber",
            x = safeX,
            y = safeY
        )
        val room = current.room.copy(
            spots = current.room.spots + spot,
            isPreview = false,
            updatedAtMs = System.currentTimeMillis()
        )
        repository.saveRoom(room)
        _uiState.update { it.copy(room = room, selectedSpotId = spot.id, scanStatus = "Spot marked • ready to verify") }
    }

    fun refreshEnvironment() {
        viewModelScope.launch {
            val carrier = _uiState.value.selectedCarrier
            val signal = withContext(Dispatchers.IO) { signalReader.read(carrier) }
            val network = withContext(Dispatchers.IO) {
                inspector.snapshot(_uiState.value.network.routeEpoch)
            }
            _uiState.update {
                it.copy(
                    signal = signal,
                    network = network,
                    requiredPermissionsGranted = signalReader.hasPermissions(),
                    scanStatus = if (network.validated) "Ready to scan" else "Mobile internet not verified"
                )
            }
        }
    }

    fun checkArSupport() {
        viewModelScope.launch(Dispatchers.IO) {
            val available = runCatching {
                ArCoreApk.getInstance().checkAvailability(getApplication<Application>()).isSupported
            }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    arAvailable = available,
                    selectedMode = if (!available && it.selectedMode == ScanMode.AUTO) ScanMode.GUIDED else it.selectedMode
                )
            }
        }
    }

    fun startVerification() {
        if (scanJob?.isActive == true) return
        val snapshot = _uiState.value
        val selectedSpot = snapshot.selectedSpot ?: run {
            markSpot(.5f, .5f)
            _uiState.value.selectedSpot ?: return
        }
        scanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    isMeasuring = true,
                    scanProgress = .04f,
                    scanStatus = "Checking ${it.selectedCarrier.label} route…",
                    errorMessage = null
                )
            }
            val startedAt = SystemClock.elapsedRealtime()
            try {
                repeat(3) { step ->
                    delay(380)
                    val current = _uiState.value
                    val signal = withContext(Dispatchers.IO) { signalReader.read(current.selectedCarrier) }
                    val network = withContext(Dispatchers.IO) { inspector.snapshot(current.network.routeEpoch) }
                    _uiState.update {
                        it.copy(
                            signal = signal,
                            network = network,
                            scanProgress = .12f + step * .08f,
                            scanStatus = when (step) {
                                0 -> "Stabilising the connection…"
                                1 -> "Reading radio conditions…"
                                else -> "Running a bounded performance check…"
                            }
                        )
                    }
                }
                val current = _uiState.value
                val result = withContext(Dispatchers.IO) {
                    measurementEngine.run(
                        carrier = current.selectedCarrier,
                        profile = current.selectedProfile,
                        budgetMb = current.budgetMb,
                        signal = current.signal ?: signalReader.read(current.selectedCarrier),
                        route = current.network
                    )
                }
                val updatedRoom = _uiState.value.room.withResult(selectedSpot.id, result)
                repository.saveRoom(updatedRoom)
                val updatedHistory = HistoryEntry(
                    id = "run-${UUID.randomUUID()}",
                    roomName = updatedRoom.name,
                    carrier = result.carrier,
                    score = result.score,
                    spotName = updatedRoom.spots.firstOrNull { it.id == selectedSpot.id }?.name ?: selectedSpot.name,
                    status = result.statusLabel,
                    completedAtMs = result.completedAtMs
                )
                repository.appendHistory(updatedHistory)
                _uiState.update {
                    it.copy(
                        room = updatedRoom,
                        history = listOf(updatedHistory) + it.history.filterNot { item -> item.id == updatedHistory.id },
                        isScanning = false,
                        isMeasuring = false,
                        scanProgress = 1f,
                        scanStatus = if (result.error == null) "Verification complete" else result.error,
                        lastCompletedAtMs = result.completedAtMs,
                        requiredPermissionsGranted = signalReader.hasPermissions()
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _uiState.update { it.copy(isScanning = false, isMeasuring = false, scanStatus = "Scan paused") }
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        isMeasuring = false,
                        scanStatus = "Scan stopped safely",
                        errorMessage = error.message ?: "Unexpected scan error"
                    )
                }
            } finally {
                withContext(NonCancellable) {
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    if (elapsed < 400L) delay(400L - elapsed)
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false, isMeasuring = false, scanProgress = 0f, scanStatus = "Scan cancelled") }
    }

    fun clearHistory() {
        repository.clearHistory()
        _uiState.update { it.copy(history = emptyList()) }
    }

    fun reportText(): String {
        val state = _uiState.value
        val result = state.bestSpot?.result
        return buildString {
            appendLine("Room Mapper diagnostic report")
            appendLine("Room: ${state.room.name}")
            appendLine("Carrier: ${state.selectedCarrier.label}")
            appendLine("Network: ${state.network.transport} • ${state.signal?.technology ?: "Unavailable"}")
            appendLine("Best tested spot: ${state.bestSpot?.name ?: "No verified spot"}")
            appendLine("Score: ${result?.score ?: "—"}/100")
            appendLine("Download: ${result?.downloadMbps?.let { "%.1f Mbps".format(it) } ?: "Unavailable"}")
            appendLine("Upload: ${result?.uploadMbps?.let { "%.1f Mbps".format(it) } ?: "Unavailable"}")
            appendLine("Latency: ${result?.latencyMs?.let { "$it ms" } ?: "Unavailable"}")
            appendLine("Measured data: ${result?.bytesUsed?.div(1024) ?: 0} KB")
            appendLine("Note: best among tested spots in this session; not a carrier guarantee.")
        }
    }

    private fun initialState(): ScanUiState {
        val default = ScanUiState()
        return default.copy(
            room = repository.loadRoom(default.room),
            selectedCarrier = repository.loadCarrier(),
            selectedProfile = repository.loadProfile(),
            selectedMode = repository.loadMode(),
            budgetMb = repository.loadBudget(),
            history = repository.loadHistory()
        )
    }

    private fun Room.withResult(id: String, result: com.laconfianza.roommapper.model.TestResult): Room =
        copy(
            isPreview = false,
            spots = spots.map { spot ->
                if (spot.id == id) spot.copy(result = result, isVerified = true, isEstimated = false) else spot
            },
            updatedAtMs = System.currentTimeMillis()
        )
}
