package com.laconfianza.roommapper.measurement

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.SubscriptionManager
import com.laconfianza.roommapper.model.NetworkSnapshot

class NetworkInspector(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    @SuppressLint("MissingPermission")
    fun snapshot(previousEpoch: Long = 0L): NetworkSnapshot {
        val network = connectivity.activeNetwork
            ?: return NetworkSnapshot(transport = "Disconnected", routeEpoch = previousEpoch + 1)
        val caps = connectivity.getNetworkCapabilities(network)
            ?: return NetworkSnapshot(transport = "Unknown", routeEpoch = previousEpoch + 1)

        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val activeSubscription = if (android.os.Build.VERSION.SDK_INT >= 30) {
            runCatching { SubscriptionManager.getActiveDataSubscriptionId() }
                .getOrNull()
                ?.takeUnless { it == SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        } else {
            null
        }
        return NetworkSnapshot(
            transport = transport,
            validated = validated,
            metered = metered,
            networkLabel = if (validated) "Internet verified" else "Connected, not verified",
            activeSubscriptionId = activeSubscription,
            routeEpoch = previousEpoch + 1
        )
    }

    fun activeNetwork() = connectivity.activeNetwork
}
