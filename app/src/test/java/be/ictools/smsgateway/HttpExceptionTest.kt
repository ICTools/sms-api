package be.ictools.smsgateway

import be.ictools.smsgateway.data.api.HttpException
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpExceptionTest {
    @Test
    fun carriesHttpCode() {
        val ex = HttpException(401, "Auth failed: 401")
        assertEquals(401, ex.code)
        assertEquals("Auth failed: 401", ex.message)
    }

    @Test
    fun nonAuthCodesArePreserved() {
        val ex = HttpException(503, "Service Unavailable")
        assertEquals(503, ex.code)
    }
}
