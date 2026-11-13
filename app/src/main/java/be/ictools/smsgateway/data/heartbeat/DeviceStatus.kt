package be.ictools.smsgateway.data.heartbeat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

/** Snapshot of the phone state sent in the heartbeat. */
data class DeviceStatus(
    val batteryLevel: Int?,
    val charging: Boolean?,
    val network: String?,
) {
    companion object {
        const val NETWORK_WIFI = "wifi"
        const val NETWORK_CELLULAR = "cellular"
        const val NETWORK_ETHERNET = "ethernet"
        const val NETWORK_OTHER = "other"
        const val NETWORK_NONE = "none"

        /** Battery percentage 0-100 from the raw level/scale of ACTION_BATTERY_CHANGED, null if unknown. */
        fun batteryPercent(level: Int, scale: Int): Int? {
            if (level < 0 || scale <= 0) return null
            return (level * 100 / scale).coerceIn(0, 100)
        }

        fun isCharging(status: Int): Boolean? = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }

        fun networkLabel(connected: Boolean, wifi: Boolean, cellular: Boolean, ethernet: Boolean): String = when {
            !connected -> NETWORK_NONE
            wifi -> NETWORK_WIFI
            ethernet -> NETWORK_ETHERNET
            cellular -> NETWORK_CELLULAR
            else -> NETWORK_OTHER
        }

        fun read(context: Context): DeviceStatus {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
            val network = networkLabel(
                connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
                wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
                cellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
                ethernet = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true,
            )
            return DeviceStatus(batteryPercent(level, scale), isCharging(status), network)
        }
    }
}
