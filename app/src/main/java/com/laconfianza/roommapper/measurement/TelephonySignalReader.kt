package com.laconfianza.roommapper.measurement

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.SignalStrength
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.laconfianza.roommapper.model.Carrier
import com.laconfianza.roommapper.model.SignalSnapshot

/** Reads the newest signal object the device is willing to expose. */
class TelephonySignalReader(private val context: Context) {
    private val subscriptionManager: SubscriptionManager? = context.getSystemService(SubscriptionManager::class.java)
    private val telephony: TelephonyManager? = context.getSystemService(TelephonyManager::class.java)

    fun hasPermissions(): Boolean =
        telephony != null &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun read(carrier: Carrier): SignalSnapshot {
        val baseTelephony = telephony
        if (!hasPermissions() || baseTelephony == null) {
            return SignalSnapshot(
                carrier = carrier,
                carrierName = carrier.label,
                technology = "",
                level = null,
                dbm = null,
                rsrp = null,
                rsrq = null,
                snr = null,
                sourceTimeMs = System.currentTimeMillis(),
                permissionLimited = true,
                carrierMatched = false
            )
        }

        val info = findSubscription(carrier)
        val carrierMatched = carrier == Carrier.OTHER || info?.carrierName?.toString()?.contains(carrier.label, ignoreCase = true) == true
        val manager = runCatching {
            if (info != null && Build.VERSION.SDK_INT >= 24) {
                baseTelephony.createForSubscriptionId(info.subscriptionId)
            } else {
                baseTelephony
            }
        }.getOrDefault(baseTelephony)

        // TelephonyManager.signalStrength was added in API 28. Older devices
        // still get a valid snapshot, but without a strength object.
        val strength = if (Build.VERSION.SDK_INT >= 28) {
            readSignalStrengthApi28(manager)
        } else {
            null
        }
        return toSnapshot(carrier, info, manager, strength, carrierMatched)
    }

    @RequiresApi(28)
    private fun readSignalStrengthApi28(manager: TelephonyManager): SignalStrength? =
        runCatching { manager.signalStrength }.getOrNull()

    @SuppressLint("MissingPermission")
    private fun findSubscription(carrier: Carrier): SubscriptionInfo? {
        val subscriptions = runCatching { subscriptionManager?.activeSubscriptionInfoList }.getOrNull()
            .orEmpty()
        val requested = subscriptions.firstOrNull { item ->
            val name = item.carrierName?.toString().orEmpty()
            when (carrier) {
                Carrier.JIO -> name.contains("jio", ignoreCase = true)
                Carrier.AIRTEL -> name.contains("airtel", ignoreCase = true)
                Carrier.OTHER -> true
            }
        }
        if (requested != null) return requested
        val activeId = if (Build.VERSION.SDK_INT >= 30) {
            SubscriptionManager.getActiveDataSubscriptionId()
        } else {
            SubscriptionManager.getDefaultDataSubscriptionId()
        }
        return subscriptions.firstOrNull { it.subscriptionId == activeId } ?: subscriptions.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun toSnapshot(
        carrier: Carrier,
        info: SubscriptionInfo?,
        manager: TelephonyManager,
        strength: SignalStrength?,
        carrierMatched: Boolean
    ): SignalSnapshot {
        var dbm: Int? = null
        var rsrp: Int? = null
        var rsrq: Int? = null
        var snr: Int? = null

        if (Build.VERSION.SDK_INT >= 29) {
            strength?.cellSignalStrengths.orEmpty().forEach { cell ->
                when (cell) {
                    is CellSignalStrengthNr -> {
                        dbm = cell.dbm.takeUnless { it == Int.MIN_VALUE }
                        rsrp = cell.ssRsrp.takeUnless { it == Int.MAX_VALUE }
                        rsrq = cell.ssRsrq.takeUnless { it == Int.MAX_VALUE }
                        snr = cell.ssSinr.takeUnless { it == Int.MAX_VALUE }
                    }
                    is CellSignalStrengthLte -> {
                        if (rsrp == null) rsrp = cell.rsrp.takeUnless { it == Int.MAX_VALUE }
                        if (rsrq == null) rsrq = cell.rsrq.takeUnless { it == Int.MAX_VALUE }
                        if (snr == null) snr = cell.rssnr.takeUnless { it == Int.MAX_VALUE }
                        if (dbm == null) dbm = cell.dbm.takeUnless { it == Int.MIN_VALUE }
                    }
                }
            }
        }

        return SignalSnapshot(
            carrier = carrier,
            carrierName = info?.carrierName?.toString()?.ifBlank { null }
                ?: manager.networkOperatorName.ifBlank { carrier.label },
            technology = networkTypeLabel(manager.dataNetworkType),
            level = strength?.level,
            dbm = dbm,
            rsrp = rsrp,
            rsrq = rsrq,
            snr = snr,
            sourceTimeMs = System.currentTimeMillis(),
            carrierMatched = carrierMatched
        )
    }

    private fun networkTypeLabel(type: Int): String {
        // NETWORK_TYPE_NR was added in API 29; keep the constant behind the
        // same runtime check so minSdk 26 devices remain lint- and crash-safe.
        if (Build.VERSION.SDK_INT >= 29) {
            networkTypeNrLabelApi29(type)?.let { return it }
        }
        return when (type) {
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT -> "2G"
            else -> "Mobile"
        }
    }

    @RequiresApi(29)
    private fun networkTypeNrLabelApi29(type: Int): String? =
        if (type == TelephonyManager.NETWORK_TYPE_NR) "5G NR" else null
}
