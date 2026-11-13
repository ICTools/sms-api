package be.ictools.smsgateway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import be.ictools.smsgateway.SmsGatewayApp
import be.ictools.smsgateway.service.GatewayForegroundService
import be.ictools.smsgateway.service.GatewayServiceLauncher
import be.ictools.smsgateway.service.HeartbeatAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
        val app = context.applicationContext as SmsGatewayApp
        val wakeLock = context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(SEND_TIMEOUT_MS) { app.appContainer.heartbeatReporter.send() }
            } catch (t: Throwable) {
                Log.w("HeartbeatAlarm", "Heartbeat failed", t)
            } finally {
                if (wakeLock?.isHeld == true) wakeLock.release()
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val WAKE_LOCK_TAG = "smsgateway:heartbeat"

        /** Bounded: the lock is released by the system even if the request hangs. */
        const val WAKE_LOCK_TIMEOUT_MS = 60_000L

        /** Under the wake lock timeout and the background broadcast limit. */
        const val SEND_TIMEOUT_MS = 30_000L
    }
}
