package be.ictools.smsgateway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import be.ictools.smsgateway.MainActivity
import be.ictools.smsgateway.R
import be.ictools.smsgateway.SmsGatewayApp
import be.ictools.smsgateway.domain.SseState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class GatewayForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val app = application as SmsGatewayApp
        startInForeground(buildNotification(SseState.Idle))
        app.appContainer.sseClient.start()
        stateJob = scope.launch {
            app.appContainer.sseClient.state.collect { state ->
                refreshNotification(state)
            }
        }
    }

    override fun onDestroy() {
        stateJob?.cancel()
        scope.cancel()
        (application as SmsGatewayApp).appContainer.sseClient.stop()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground(notification: Notification) {
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun refreshNotification(state: SseState) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: SseState): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(describeState(state))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapIntent)
            .build()
    }

    private fun describeState(state: SseState): String = when (state) {
        SseState.Idle -> getString(R.string.notification_text)
        SseState.Disabled -> getString(R.string.sse_state_disabled)
        SseState.WaitingNetwork -> getString(R.string.sse_state_waiting_network)
        SseState.Connecting -> getString(R.string.sse_state_connecting)
        SseState.Connected -> getString(R.string.sse_state_connected)
        SseState.Reauthenticating -> getString(R.string.sse_state_reauthenticating)
        is SseState.Reconnecting -> getString(R.string.sse_state_reconnecting, state.attempt)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "sms_gateway_sync"
        private const val NOTIFICATION_ID = 1001
    }
}

