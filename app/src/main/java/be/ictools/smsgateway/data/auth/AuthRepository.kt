package be.ictools.smsgateway.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class TokenResponse(
    @SerialName("token") val token: String,
)

class AuthRepository(
    private val okHttpClient: OkHttpClient,
    private val sessionStore: SessionStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val emptyJsonBody = "{}".toRequestBody("application/json".toMediaType())

    suspend fun ensureJwt(): Result<String> = withContext(Dispatchers.IO) {
        val current = sessionStore.read()
        if (current.jwt.isNotBlank()) return@withContext Result.success(current.jwt)
        return@withContext exchangeApiKey()
    }

    suspend fun exchangeApiKey(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val config = sessionStore.read()
            require(config.baseUrl.isNotBlank()) { "Base URL missing" }
            require(config.apiKey.isNotBlank()) { "API key missing" }
            require(config.apiSecret.isNotBlank()) { "API secret missing" }

            val url = config.baseUrl.toHttpUrlOrNull()
                ?.newBuilder()
                ?.addPathSegments("api/authentication_token")
                ?.build()
                ?: error("Invalid base URL")

            val request = Request.Builder()
                .url(url)
                .post(emptyJsonBody)
                .header("X-Api-Key", config.apiKey)
                .header("X-Api-Secret", config.apiSecret)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Authentication failed: ${response.code}")
                val token = json.decodeFromString<TokenResponse>(
                    response.body?.string().orEmpty(),
                ).token
                sessionStore.saveJwt(token)
                token
            }
        }
    }
}
