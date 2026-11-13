package be.ictools.smsgateway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import be.ictools.smsgateway.service.GatewayForegroundService
import be.ictools.smsgateway.service.GatewayServiceLauncher
import be.ictools.smsgateway.service.HeartbeatAlarm
import be.ictools.smsgateway.service.HeartbeatWakeLock

/**
 * Returns at once and lets the running service send the beat: the HTTP call blocks and could
 * outlast the broadcast limit, and an ANR there would kill the gateway process.
 */
class HeartbeatAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HeartbeatAlarm.ACTION) return
        if (!GatewayForegroundService.isRunning) {
            // Only a running gateway may say it is alive; the service beats and reschedules on start.
            GatewayServiceLauncher.start(context)
            return
        }
        // Reschedule first so a slow or failed beat never breaks the chain.
        HeartbeatAlarm.schedule(context)
        HeartbeatWakeLock.acquire(context)
        runCatching {
            context.startService(
                Intent(context, GatewayForegroundService::class.java).setAction(GatewayForegroundService.ACTION_HEARTBEAT),
            )
        }.onFailure { error ->
            Log.w("HeartbeatAlarm", "Heartbeat hand-off refused: ${error.message}")
            HeartbeatWakeLock.release()
        }
    }
}
