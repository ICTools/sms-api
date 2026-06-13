package be.ictools.smsgateway.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import be.ictools.smsgateway.SmsGatewayApp

class SyncPendingMessagesWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as SmsGatewayApp).appContainer.repository
        return runCatching {
            repository.syncPendingActions()
            Result.success()
        }.getOrElse { error ->
            android.util.Log.w("SyncWorker", "Sync failed: ${error.message}")
            if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "sync_pending_messages"
        const val MAX_ATTEMPTS = 8
        val BACKOFF_POLICY = BackoffPolicy.EXPONENTIAL
    }
}
