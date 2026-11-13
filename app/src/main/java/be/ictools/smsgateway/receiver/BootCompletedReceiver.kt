package be.ictools.smsgateway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import be.ictools.smsgateway.service.GatewayServiceLauncher

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (shouldRestart(intent.action)) {
            GatewayServiceLauncher.start(context)
        }
    }

    companion object {
        /** Phone reboot and APK update both leave the gateway stopped until something restarts it. */
        val RESTART_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )

        fun shouldRestart(action: String?): Boolean = action in RESTART_ACTIONS
    }
}
