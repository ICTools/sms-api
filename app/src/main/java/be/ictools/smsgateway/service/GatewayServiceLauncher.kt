package be.ictools.smsgateway.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

object GatewayServiceLauncher {
    /**
     * Starts the foreground service. Returns false when the system refuses a start from the
     * background (Android 12+ without an exemption); the watchdog will try again later.
     */
    fun start(context: Context): Boolean = runCatching {
        ContextCompat.startForegroundService(
            context,
            Intent(context, GatewayForegroundService::class.java),
        )
    }.onFailure { error ->
        Log.w("GatewayLauncher", "Foreground service start refused: ${error.message}")
    }.isSuccess
}
