package be.ictools.smsgateway.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object GatewayServiceLauncher {
    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, GatewayForegroundService::class.java),
        )
    }
}

