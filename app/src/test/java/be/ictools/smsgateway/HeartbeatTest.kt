package be.ictools.smsgateway

import be.ictools.smsgateway.data.api.HeartbeatRequest
import be.ictools.smsgateway.data.api.HttpException
import be.ictools.smsgateway.data.heartbeat.DeviceStatus
import be.ictools.smsgateway.data.heartbeat.HeartbeatReporter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatTest {
    @Test
    fun serializesServerContractFieldNames() {
        val body = Json.encodeToString(
            HeartbeatRequest.serializer(),
            HeartbeatRequest(batteryLevel = 87, charging = true, network = "wifi", appVersion = "1.0"),
        )
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals(87, json.getValue("batteryLevel").jsonPrimitive.int)
        assertEquals(true, json.getValue("charging").jsonPrimitive.boolean)
        assertEquals("wifi", json.getValue("network").jsonPrimitive.content)
        assertEquals("1.0", json.getValue("appVersion").jsonPrimitive.content)
    }

    @Test
    fun batteryPercentFromLevelAndScale() {
        assertEquals(50, DeviceStatus.batteryPercent(level = 50, scale = 100))
        assertEquals(75, DeviceStatus.batteryPercent(level = 150, scale = 200))
        assertEquals(100, DeviceStatus.batteryPercent(level = 120, scale = 100))
    }

    @Test
    fun unknownBatteryIsNull() {
        assertNull(DeviceStatus.batteryPercent(level = -1, scale = 100))
        assertNull(DeviceStatus.batteryPercent(level = 50, scale = 0))
    }

    @Test
    fun chargingFromBatteryStatus() {
        assertEquals(true, DeviceStatus.isCharging(2)) // BATTERY_STATUS_CHARGING
        assertEquals(true, DeviceStatus.isCharging(5)) // BATTERY_STATUS_FULL
        assertEquals(false, DeviceStatus.isCharging(3)) // BATTERY_STATUS_DISCHARGING
        assertNull(DeviceStatus.isCharging(1)) // BATTERY_STATUS_UNKNOWN
    }

    @Test
    fun networkLabel() {
        assertEquals("none", DeviceStatus.networkLabel(connected = false, wifi = true, cellular = false, ethernet = false))
        assertEquals("wifi", DeviceStatus.networkLabel(connected = true, wifi = true, cellular = true, ethernet = false))
        assertEquals("cellular", DeviceStatus.networkLabel(connected = true, wifi = false, cellular = true, ethernet = false))
        assertEquals("ethernet", DeviceStatus.networkLabel(connected = true, wifi = false, cellular = false, ethernet = true))
        assertEquals("other", DeviceStatus.networkLabel(connected = true, wifi = false, cellular = false, ethernet = false))
    }

    @Test
    fun forbiddenHeartbeatPointsToMissingScope() {
        val reason = HeartbeatReporter.describeFailure(HttpException(403, "Heartbeat failed: 403"))
        assertTrue(reason.contains("sms_gateway"))
    }

    @Test
    fun otherFailuresKeepTheirMessage() {
        assertEquals("Heartbeat failed: 503", HeartbeatReporter.describeFailure(HttpException(503, "Heartbeat failed: 503")))
    }
}
