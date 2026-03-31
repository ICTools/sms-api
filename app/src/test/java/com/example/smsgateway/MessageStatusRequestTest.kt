package com.example.smsgateway

import com.example.smsgateway.data.api.MessageStatusRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageStatusRequestTest {
    @Test
    fun keepsProvidedStatus() {
        val payload = MessageStatusRequest(
            status = "delivered",
            deviceMessageId = 12L,
        )

        assertEquals("delivered", payload.status)
        assertEquals(12L, payload.deviceMessageId)
    }
}

