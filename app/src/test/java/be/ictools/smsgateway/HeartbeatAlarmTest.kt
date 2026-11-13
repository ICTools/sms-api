package be.ictools.smsgateway

import be.ictools.smsgateway.data.heartbeat.HeartbeatReporter
import be.ictools.smsgateway.receiver.HeartbeatAlarmReceiver
import be.ictools.smsgateway.service.HeartbeatAlarm
import be.ictools.smsgateway.ui.GatewayUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatAlarmTest {
    private val offlineThresholdMs = 10 * 60 * 1000L

    @Test
    fun exactBeforeAndroid12AndWhenAllowed() {
        assertEquals(HeartbeatAlarm.Mode.EXACT, HeartbeatAlarm.mode(sdkInt = 29, canScheduleExact = false))
        assertEquals(HeartbeatAlarm.Mode.EXACT, HeartbeatAlarm.mode(sdkInt = 34, canScheduleExact = true))
    }

    @Test
    fun inexactWhenExactAlarmsAreDenied() {
        assertEquals(HeartbeatAlarm.Mode.INEXACT, HeartbeatAlarm.mode(sdkInt = 34, canScheduleExact = false))
    }

    @Test
    fun triggersOneIntervalLaterOnElapsedRealtime() {
        assertEquals(1_000L + HeartbeatReporter.INTERVAL_MS, HeartbeatAlarm.triggerAt(nowElapsed = 1_000L))
    }

    @Test
    fun worstCaseDelayStaysBelowOfflineThreshold() {
        HeartbeatAlarm.Mode.values().forEach { mode ->
            assertTrue("$mode", HeartbeatAlarm.worstCaseDelayMs(mode) < offlineThresholdMs)
        }
    }

    @Test
    fun wakeLockIsBoundedAndOutlastsTheRequest() {
        assertTrue(HeartbeatAlarmReceiver.SEND_TIMEOUT_MS < HeartbeatAlarmReceiver.WAKE_LOCK_TIMEOUT_MS)
        assertTrue(HeartbeatAlarmReceiver.WAKE_LOCK_TIMEOUT_MS <= 60_000L)
    }

    @Test
    fun uiStateToStringHidesTypedCredentials() {
        val text = GatewayUiState(apiKeyInput = "01M6YBKEY", apiSecretInput = "s3cr3t").toString()
        assertFalse(text.contains("s3cr3t"))
        assertFalse(text.contains("01M6YBKEY"))
        assertTrue(text.contains("apiSecretInput=<redacted>"))
    }
}
