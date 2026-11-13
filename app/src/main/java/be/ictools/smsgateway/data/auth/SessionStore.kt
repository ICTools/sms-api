package be.ictools.smsgateway.data.auth

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import be.ictools.smsgateway.BuildConfig

data class ServerConfig(
    val baseUrl: String = BuildConfig.DEFAULT_BASE_URL,
    val apiKey: String = "",
    val apiSecret: String = "",
    val jwt: String = "",
) {
    val isComplete: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && apiSecret.isNotBlank()

    /** Everything the UI may show: the key masked, the secret only as present/absent. */
    fun toDisplay(): CredentialsDisplay = CredentialsDisplay(
        baseUrl = baseUrl,
        maskedApiKey = CredentialMask.maskApiKey(apiKey),
        hasApiKey = apiKey.isNotBlank(),
        hasSecret = apiSecret.isNotBlank(),
        hasJwt = jwt.isNotBlank(),
    )

    // The secret, key and JWT must never reach logs through the data class toString().
    override fun toString(): String = "ServerConfig(baseUrl=$baseUrl, ${toDisplay()})"
}

data class CredentialsDisplay(
    val baseUrl: String = "",
    val maskedApiKey: String = "",
    val hasApiKey: Boolean = false,
    val hasSecret: Boolean = false,
    val hasJwt: Boolean = false,
) {
    val hasStoredCredentials: Boolean get() = hasApiKey || hasSecret
}

class SessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "gateway_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun read(): ServerConfig = ServerConfig(
        baseUrl = prefs.getString(KEY_BASE_URL, BuildConfig.DEFAULT_BASE_URL).orEmpty(),
        apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        apiSecret = prefs.getString(KEY_API_SECRET, "").orEmpty(),
        jwt = prefs.getString(KEY_JWT, "").orEmpty(),
    )

    fun isConfigured(): Boolean = read().isComplete

    /** What the configuration screen may show; never the secret itself. */
    fun display(): CredentialsDisplay = read().toDisplay()

    /** Forgets the credentials and the JWT; the base URL is kept, it is not secret. */
    fun clearCredentials() {
        prefs.edit {
            remove(KEY_API_KEY)
            remove(KEY_API_SECRET)
            remove(KEY_JWT)
        }
    }

    fun saveBaseUrl(baseUrl: String) {
        prefs.edit { putString(KEY_BASE_URL, baseUrl.trimEnd('/')) }
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit { putString(KEY_API_KEY, apiKey.trim()) }
    }

    fun saveApiSecret(apiSecret: String) {
        prefs.edit { putString(KEY_API_SECRET, apiSecret.trim()) }
    }

    fun saveJwt(jwt: String) {
        prefs.edit { putString(KEY_JWT, jwt.trim()) }
    }

    fun clearJwt() {
        prefs.edit { remove(KEY_JWT) }
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_JWT = "jwt"
    }
}
