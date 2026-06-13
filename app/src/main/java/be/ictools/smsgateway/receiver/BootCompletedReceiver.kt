package be.ictools.smsgateway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import be.ictools.smsgateway.service.GatewayServiceLauncher

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            GatewayServiceLauncher.start(context)
        }
    }
}

