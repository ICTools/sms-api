package be.ictools.smsgateway

import be.ictools.smsgateway.data.api.GatewayApi
import be.ictools.smsgateway.data.heartbeat.HeartbeatReporter
import be.ictools.smsgateway.service.HeartbeatAlarm
import be.ictools.smsgateway.service.HeartbeatWakeLock
import be.ictools.smsgateway.ui.GatewayUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatAlarmTest {
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
    fun exactExemptBeatStaysBelowOfflineThreshold() {
        listOf(29, 30, 31, 34, 36).forEach { sdk ->
            val worst = HeartbeatAlarm.worstCaseDelayMs(HeartbeatAlarm.Mode.EXACT, sdk, batteryExempt = true)
            assertEquals("sdk $sdk", HeartbeatReporter.INTERVAL_MS, worst)
        }
    }

    @Test
    fun dozeSpacesBeatsWithoutExemptionBeforeAndroid12() {
        assertEquals(
            HeartbeatAlarm.DOZE_MIN_GAP_MS,
            HeartbeatAlarm.worstCaseDelayMs(HeartbeatAlarm.Mode.EXACT, sdkInt = 30, batteryExempt = false),
        )
        assertEquals(
            HeartbeatReporter.INTERVAL_MS,
            HeartbeatAlarm.worstCaseDelayMs(HeartbeatAlarm.Mode.EXACT, sdkInt = 34, batteryExempt = false),
        )
    }

    @Test
    fun inexactFallbackIsSpacedByDozeFromAndroid12() {
        val worst = HeartbeatAlarm.worstCaseDelayMs(HeartbeatAlarm.Mode.INEXACT, sdkInt = 34, batteryExempt = true)
        assertTrue(worst >= HeartbeatAlarm.DOZE_MIN_GAP_MS)
    }

    @Test
    fun heartbeatCallGivesUpBeforeTheWakeLockExpires() {
        assertTrue(GatewayApi.HEARTBEAT_CALL_TIMEOUT_MS < HeartbeatWakeLock.TIMEOUT_MS)
        assertTrue(HeartbeatWakeLock.TIMEOUT_MS <= 60_000L)
    }

    @Test
    fun uiStateToStringHidesTypedCredentials() {
        val text = GatewayUiState(apiKeyInput = "01M6YBKEY", apiSecretInput = "s3cr3t").toString()
        assertFalse(text.contains("s3cr3t"))
        assertFalse(text.contains("01M6YBKEY"))
        assertTrue(text.contains("apiSecretInput=<redacted>"))
    }
}
