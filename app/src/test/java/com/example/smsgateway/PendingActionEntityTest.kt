package com.example.smsgateway

import com.example.smsgateway.data.local.entity.PendingActionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingActionEntityTest {
    @Test
    fun defaultsRetryCountToZero() {
        val action = PendingActionEntity(
            type = "push_outgoing",
            messageId = 1L,
            payload = "{}",
            createdAt = 0L,
        )
        assertEquals(0, action.retryCount)
    }

    @Test
    fun copyIncrementsRetryCount() {
        val action = PendingActionEntity(
            type = "push_status",
            messageId = 1L,
            payload = "{}",
            createdAt = 0L,
            retryCount = 3,
        )
        val next = action.copy(retryCount = action.retryCount + 1)
        assertEquals(4, next.retryCount)
    }
}
