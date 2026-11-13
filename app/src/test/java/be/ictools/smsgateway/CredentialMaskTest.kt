package be.ictools.smsgateway

import be.ictools.smsgateway.data.auth.CredentialMask
import be.ictools.smsgateway.data.auth.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialMaskTest {
    @Test
    fun ulidKeyKeepsSixFirstAndThreeLastCharacters() {
        assertEquals("01M6YB…XKV", CredentialMask.maskApiKey("01M6YBQ7Z3H8K2N4P5R6S7TXKV"))
    }

    @Test
    fun shortKeyIsFullyMasked() {
        assertEquals("…", CredentialMask.maskApiKey("ABCDEFGHIJ"))
    }

    @Test
    fun blankKeyGivesEmptyMask() {
        assertEquals("", CredentialMask.maskApiKey("  "))
    }

    @Test
    fun maskedKeyNeverContainsTheMiddle() {
        val key = "01M6YBQ7Z3H8K2N4P5R6S7TXKV"
        val masked = CredentialMask.maskApiKey(key)
        assertFalse(masked.contains(key.substring(6, key.length - 3)))
    }

    @Test
    fun blankInputKeepsStoredValue() {
        assertEquals("stored-secret", CredentialMask.resolveInput("   ", "stored-secret"))
    }

    @Test
    fun typedInputReplacesStoredValue() {
        assertEquals("new-secret", CredentialMask.resolveInput(" new-secret ", "stored-secret"))
    }

    @Test
    fun displayExposesOnlyPresenceOfSecret() {
        val config = ServerConfig(
            baseUrl = "https://example.test",
            apiKey = "01M6YBQ7Z3H8K2N4P5R6S7TXKV",
            apiSecret = "super-secret-value",
            jwt = "jwt-token",
        )
        val display = config.toDisplay()
        assertTrue(display.hasSecret)
        assertTrue(display.hasStoredCredentials)
        assertEquals("01M6YB…XKV", display.maskedApiKey)
        assertFalse(display.toString().contains("super-secret-value"))
    }

    @Test
    fun serverConfigToStringLeaksNoCredential() {
        val text = ServerConfig(
            baseUrl = "https://example.test",
            apiKey = "01M6YBQ7Z3H8K2N4P5R6S7TXKV",
            apiSecret = "super-secret-value",
            jwt = "jwt-token",
        ).toString()
        assertFalse(text.contains("super-secret-value"))
        assertFalse(text.contains("jwt-token"))
        assertFalse(text.contains("01M6YBQ7Z3H8K2N4P5R6S7TXKV"))
    }

    @Test
    fun configIsCompleteOnlyWithUrlKeyAndSecret() {
        assertTrue(ServerConfig("https://example.test", "key", "secret").isComplete)
        assertFalse(ServerConfig("https://example.test", "key", "").isComplete)
        assertFalse(ServerConfig("", "key", "secret").isComplete)
    }
}
