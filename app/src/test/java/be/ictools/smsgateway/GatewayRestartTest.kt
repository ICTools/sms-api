package be.ictools.smsgateway

import be.ictools.smsgateway.data.heartbeat.HeartbeatReporter
import be.ictools.smsgateway.receiver.BootCompletedReceiver
import be.ictools.smsgateway.ui.security.ConfigEditGate
import be.ictools.smsgateway.ui.security.UnlockRequirement
import be.ictools.smsgateway.worker.GatewayWatchdogWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayRestartTest {
    @Test
    fun restartsAfterBoot() {
        assertTrue(BootCompletedReceiver.shouldRestart("android.intent.action.BOOT_COMPLETED"))
    }

    @Test
    fun restartsAfterApkUpdate() {
        assertTrue(BootCompletedReceiver.shouldRestart("android.intent.action.MY_PACKAGE_REPLACED"))
    }

    @Test
    fun ignoresOtherBroadcasts() {
        assertFalse(BootCompletedReceiver.shouldRestart("android.intent.action.PACKAGE_REPLACED"))
        assertFalse(BootCompletedReceiver.shouldRestart(null))
    }

    @Test
    fun watchdogRestartsStoppedService() {
        assertEquals(GatewayWatchdogWorker.Action.RESTART, GatewayWatchdogWorker.decide(serviceRunning = false))
    }

    @Test
    fun watchdogLeavesRunningServiceAlone() {
        assertEquals(GatewayWatchdogWorker.Action.NONE, GatewayWatchdogWorker.decide(serviceRunning = true))
    }

    @Test
    fun watchdogRunsAtMostEveryFifteenMinutes() {
        assertTrue(GatewayWatchdogWorker.INTERVAL_MINUTES <= 15)
    }

    @Test
    fun heartbeatIsMoreFrequentThanServerOfflineThreshold() {
        // chapitreneuf SMS_GATEWAY_OFFLINE_THRESHOLD_MINUTES defaults to 10: two beats must fit in it.
        assertTrue(HeartbeatReporter.INTERVAL_MS * 2 < 10 * 60 * 1000L)
    }

    @Test
    fun firstConfigurationNeedsNoUnlock() {
        assertEquals(UnlockRequirement.NONE, ConfigEditGate.requirement(hasStoredCredentials = false, deviceSecure = true))
    }

    @Test
    fun savedCredentialsNeedDeviceLock() {
        assertEquals(
            UnlockRequirement.DEVICE_CREDENTIAL,
            ConfigEditGate.requirement(hasStoredCredentials = true, deviceSecure = true),
        )
    }

    @Test
    fun savedCredentialsWithoutScreenLockAreReported() {
        assertEquals(
            UnlockRequirement.NO_DEVICE_LOCK,
            ConfigEditGate.requirement(hasStoredCredentials = true, deviceSecure = false),
        )
    }
}
