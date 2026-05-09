package com.example.smsgateway.data.api

import com.example.smsgateway.data.auth.AuthRepository
import com.example.smsgateway.data.auth.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HttpException(val code: Int, message: String) : RuntimeException(message)

class GatewayApi(
    private val okHttpClient: OkHttpClient,
    private val sessionStore: SessionStore,
    private val authRepository: AuthRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mediaType = "application/json".toMediaType()

    suspend fun fetchRemoteMessage(ulid: String): Result<RemoteMessageDto> = withAuthorizedCall {
        val url = baseUrlBuilder()
            .addPathSegments("api/mobile/messages")
            .addPathSegment(ulid)
            .build()
        val request = Request.Builder().url(url).get().authorized().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, "Fetch failed: ${response.code}")
            json.decodeFromString<RemoteMessageDto>(response.body?.string().orEmpty())
        }
    }

    suspend fun publishIncomingMessage(payload: IncomingMessageRequest): Result<Unit> = withAuthorizedCall {
        val request = Request.Builder()
            .url(baseUrlBuilder().addPathSegments("api/mobile/incoming-sms").build())
            .post(json.encodeToString(IncomingMessageRequest.serializer(), payload).toRequestBody(mediaType))
            .authorized()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, "Push failed: ${response.code}")
        }
    }

    suspend fun publishOutgoingMessage(payload: OutgoingMessageRequest): Result<Unit> = withAuthorizedCall {
        val request = Request.Builder()
            .url(baseUrlBuilder().addPathSegments("api/mobile/outgoing-sms").build())
            .post(json.encodeToString(OutgoingMessageRequest.serializer(), payload).toRequestBody(mediaType))
            .authorized()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, "Outgoing push failed: ${response.code}")
        }
    }

    suspend fun publishStatus(ulid: String, payload: MessageStatusRequest): Result<Unit> = withAuthorizedCall {
        val request = Request.Builder()
            .url(baseUrlBuilder().addPathSegments("api/mobile/messages").addPathSegment(ulid).addPathSegment("status").build())
            .post(json.encodeToString(MessageStatusRequest.serializer(), payload).toRequestBody(mediaType))
            .authorized()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, "Status update failed: ${response.code}")
        }
    }

    fun buildMercureUrl(): HttpUrl? {
        val config = sessionStore.read()
        return config.baseUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments(".well-known/mercure")
            ?.addQueryParameter("topic", "sms/outgoing")
            ?.build()
    }

    private suspend fun <T> withAuthorizedCall(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        authRepository.ensureJwt().getOrElse { return@withContext Result.failure(it) }
        runCatching { block() }.recoverCatching { error ->
            if (error is HttpException && error.code == 401) {
                sessionStore.clearJwt()
                authRepository.exchangeApiKey().getOrThrow()
                block()
            } else {
                throw error
            }
        }
    }

    private fun baseUrlBuilder(): HttpUrl.Builder {
        return sessionStore.read().baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?: error("Invalid base URL")
    }

    private suspend fun Request.Builder.authorized(): Request.Builder {
        val jwt = authRepository.ensureJwt().getOrThrow()
        return header("Authorization", "Bearer $jwt")
    }
}
