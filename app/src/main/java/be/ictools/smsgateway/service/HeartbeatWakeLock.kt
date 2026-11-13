package be.ictools.smsgateway.service

import android.content.Context
import android.os.PowerManager

/**
 * Keeps the CPU awake from the heartbeat alarm until the service has sent the beat. The alarm only
 * holds the CPU during onReceive, and the receiver returns at once (a blocking HTTP call there
 * would hold the broadcast and end in an ANR), so the lock bridges the hand-off to the service.
 */
object HeartbeatWakeLock {
    /** Bounded: the system releases it even if the request hangs. */
    const val TIMEOUT_MS = 60_000L

    private const val TAG = "smsgateway:heartbeat"
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context) {
        val lock = wakeLock ?: context.applicationContext.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
            ?.apply { setReferenceCounted(false) }
            ?.also { wakeLock = it }
        lock?.acquire(TIMEOUT_MS)
    }

    @Synchronized
    fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }
}
