package com.laconfianza.roommapper.measurement

import android.net.Network
import com.laconfianza.roommapper.model.Carrier
import com.laconfianza.roommapper.model.NetworkSnapshot
import com.laconfianza.roommapper.model.SignalSnapshot
import com.laconfianza.roommapper.model.TestResult
import com.laconfianza.roommapper.model.UseProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/**
 * Bounded, user-visible measurements. The server endpoints are intentionally
 * simple and replaceable; all results carry route and protocol context.
 */
class MeasurementEngine(private val inspector: NetworkInspector) {
    suspend fun run(
        carrier: Carrier,
        profile: UseProfile,
        budgetMb: Int,
        signal: SignalSnapshot,
        route: NetworkSnapshot
    ): TestResult = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val currentRoute = inspector.snapshot(route.routeEpoch)
        val network = inspector.activeNetwork()
        if (network == null || currentRoute.transport != "Cellular" || !currentRoute.validated) {
            return@withContext failed(carrier, signal, currentRoute, "Cellular internet is not verified")
        }
        if (!signal.carrierMatched && !signal.permissionLimited) {
            return@withContext failed(carrier, signal, currentRoute, "${carrier.label} is not the active data route")
        }

        val budgetBytes = budgetMb.coerceIn(5, 1000).toLong() * 1024L * 1024L
        var used = 0L
        val latencies = mutableListOf<Long>()
        var successes = 0
        var attempts = 0

        repeat(4) {
            coroutineContext.ensureActive()
            attempts++
            val latency = requestLatency(network)
            if (latency != null) {
                successes++
                latencies += latency
            }
        }

        val downloadBytes = min(1_048_576L, max(256_000L, budgetBytes * 60 / 100))
        val download = download(network, downloadBytes)
        used += download.bytes
        val remaining = max(0L, budgetBytes - used)
        val uploadBytes = min(256_000L, remaining)
        val upload = if (uploadBytes >= 32_000L) upload(network, uploadBytes) else Transfer(null, 0L)
        used += upload.bytes

        val latencyMedian = latencies.sorted().let { values ->
            if (values.isEmpty()) null else values[values.size / 2]
        }
        val jitter = latencies.sorted().let { values ->
            if (values.size < 2) null else values.zipWithNext().map { (a, b) -> kotlin.math.abs(b - a) }.average().toLong()
        }
        val elapsed = (System.nanoTime() - started) / 1_000_000L
        val reliability = if (attempts == 0) 0 else (successes * 100 / attempts)
        val routeVerified = currentRoute.transport == "Cellular" && currentRoute.activeSubscriptionId != null && signal.carrierMatched
        val score = ScoreEngine.score(profile, reliability, latencyMedian, jitter, download.mbps, upload.mbps)

        TestResult(
            carrier = carrier,
            score = score,
            downloadMbps = download.mbps,
            uploadMbps = upload.mbps,
            latencyMs = latencyMedian,
            jitterMs = jitter,
            reliabilityPercent = reliability,
            bytesUsed = used,
            durationMs = elapsed,
            completedAtMs = System.currentTimeMillis(),
            signal = signal,
            routeVerified = routeVerified,
            error = when {
                download.error != null && upload.error != null -> "Transfer test failed"
                else -> null
            }
        )
    }

    private fun requestLatency(network: Network): Long? = runCatching {
        val connection = network.openConnection(URL(LATENCY_URL)) as HttpURLConnection
        connection.connectTimeout = 4_000
        connection.readTimeout = 4_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Cache-Control", "no-cache")
        val started = System.nanoTime()
        val code = connection.responseCode
        connection.disconnect()
        if (code in 200..399) (System.nanoTime() - started) / 1_000_000L else null
    }.getOrNull()

    private suspend fun download(network: Network, requested: Long): Transfer = try {
        val connection = network.openConnection(URL("$DOWNLOAD_URL$requested")) as HttpURLConnection
        connection.connectTimeout = 7_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("Cache-Control", "no-cache")
        val started = System.nanoTime()
        val count = connection.inputStream.use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (total < requested) {
                val read = input.read(buffer, 0, min(buffer.size.toLong(), requested - total).toInt())
                if (read <= 0) break
                total += read
            }
            total
        }
        val elapsed = max(1L, (System.nanoTime() - started) / 1_000_000L)
        connection.disconnect()
        Transfer(count.toDouble() * 8 / 1_000_000 / (elapsed / 1000.0), count)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Transfer(null, 0L, error.message)
    }

    private suspend fun upload(network: Network, requested: Long): Transfer = try {
        val connection = network.openConnection(URL(UPLOAD_URL)) as HttpURLConnection
        connection.connectTimeout = 7_000
        connection.readTimeout = 12_000
        connection.doOutput = true
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/octet-stream")
        connection.setFixedLengthStreamingMode(requested)
        val payload = ByteArray(16 * 1024) { 7 }
        val started = System.nanoTime()
        connection.outputStream.use { output: OutputStream ->
            var remaining = requested
            while (remaining > 0) {
                val size = min(payload.size.toLong(), remaining).toInt()
                output.write(payload, 0, size)
                remaining -= size
            }
        }
        val code = connection.responseCode
        connection.inputStream.use { it.readBytes() }
        val elapsed = max(1L, (System.nanoTime() - started) / 1_000_000L)
        connection.disconnect()
        if (code !in 200..399) Transfer(null, requested, "HTTP $code")
        else Transfer(requested.toDouble() * 8 / 1_000_000 / (elapsed / 1000.0), requested)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Transfer(null, 0L, error.message)
    }

    private fun failed(
        carrier: Carrier,
        signal: SignalSnapshot,
        route: NetworkSnapshot,
        error: String
    ) = TestResult(
        carrier = carrier,
        score = 0,
        downloadMbps = null,
        uploadMbps = null,
        latencyMs = null,
        jitterMs = null,
        reliabilityPercent = 0,
        bytesUsed = 0L,
        durationMs = 0L,
        completedAtMs = System.currentTimeMillis(),
        signal = signal,
        routeVerified = route.transport == "Cellular" && route.validated && signal.carrierMatched,
        error = error
    )

    private data class Transfer(val mbps: Double?, val bytes: Long, val error: String? = null)

    companion object {
        private const val LATENCY_URL = "https://www.google.com/generate_204"
        private const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes="
        private const val UPLOAD_URL = "https://speed.cloudflare.com/__up"
    }
}
