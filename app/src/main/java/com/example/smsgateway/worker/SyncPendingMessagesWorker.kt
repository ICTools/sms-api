package com.example.smsgateway.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smsgateway.SmsGatewayApp

class SyncPendingMessagesWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as SmsGatewayApp).appContainer.repository
        return runCatching {
            repository.syncPendingActions()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "sync_pending_messages"
    }
}

