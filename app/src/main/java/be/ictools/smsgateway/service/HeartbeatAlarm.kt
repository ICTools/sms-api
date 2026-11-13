package be.ictools.smsgateway.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import be.ictools.smsgateway.data.heartbeat.HeartbeatReporter
import be.ictools.smsgateway.receiver.HeartbeatAlarmReceiver

/**
 * Wakes the CPU for each heartbeat. A coroutine delay() does not advance while the CPU sleeps, so
 * a 4 min loop could stretch past the server's 10 min offline threshold with the screen off.
 * ELAPSED_REALTIME_WAKEUP alarms fire on real elapsed time, in Doze too (allow-while-idle).
 */
object HeartbeatAlarm {
    const val ACTION = "be.ictools.smsgateway.action.HEARTBEAT"

    enum class Mode { EXACT, INEXACT }

    /**
     * Exact when the system lets us (SCHEDULE_EXACT_ALARM, granted by default before Android 13).
     * Otherwise inexact: Android may deliver it up to 75 % of the delay late, which still stays
     * below the threshold (see [worstCaseDelayMs]).
     */
    fun mode(sdkInt: Int, canScheduleExact: Boolean): Mode =
        if (sdkInt < Build.VERSION_CODES.S || canScheduleExact) Mode.EXACT else Mode.INEXACT

    fun triggerAt(nowElapsed: Long): Long = nowElapsed + HeartbeatReporter.INTERVAL_MS

    fun worstCaseDelayMs(mode: Mode): Long = when (mode) {
        Mode.EXACT -> HeartbeatReporter.INTERVAL_MS
        Mode.INEXACT -> HeartbeatReporter.INTERVAL_MS * 7 / 4
    }

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        val triggerAt = triggerAt(SystemClock.elapsedRealtime())
        val operation = pendingIntent(context)
        when (mode(Build.VERSION.SDK_INT, canExact)) {
            Mode.EXACT -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
            Mode.INEXACT -> alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, HeartbeatAlarmReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
