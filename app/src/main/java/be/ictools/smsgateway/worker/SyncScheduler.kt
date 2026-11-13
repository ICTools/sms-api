package be.ictools.smsgateway.worker

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SyncScheduler(
    private val workManager: WorkManager,
) {
    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncPendingMessagesWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** No network constraint: the service must come back even before the network does. */
    fun scheduleWatchdog() {
        val request = PeriodicWorkRequestBuilder<GatewayWatchdogWorker>(
            GatewayWatchdogWorker.INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WATCHDOG,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        const val UNIQUE_PERIODIC_SYNC = "periodic_sms_sync"
        const val UNIQUE_WATCHDOG = "gateway_watchdog"
    }
}

