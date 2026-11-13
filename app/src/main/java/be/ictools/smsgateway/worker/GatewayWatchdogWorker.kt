package be.ictools.smsgateway.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import be.ictools.smsgateway.service.GatewayForegroundService
import be.ictools.smsgateway.service.GatewayServiceLauncher

/**
 * Safety net run every 15 minutes: restarts the gateway service when the system stopped it.
 * Starting a foreground service from the background is only allowed while the app is exempt
 * from battery optimizations, which the app asks for at first launch.
 */
class GatewayWatchdogWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (decide(GatewayForegroundService.isRunning) == Action.RESTART) {
            val started = GatewayServiceLauncher.start(applicationContext)
            Log.i(TAG, "Gateway service was not running, restart ${if (started) "requested" else "refused"}")
        }
        return Result.success()
    }

    enum class Action { NONE, RESTART }

    companion object {
        private const val TAG = "GatewayWatchdog"
        const val INTERVAL_MINUTES = 15L

        fun decide(serviceRunning: Boolean): Action = if (serviceRunning) Action.NONE else Action.RESTART
    }
}
