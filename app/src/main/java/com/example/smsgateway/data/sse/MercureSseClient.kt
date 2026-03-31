package com.example.smsgateway.data.sse

import android.content.Context
import android.util.Log
import com.example.smsgateway.data.auth.AuthRepository
import com.example.smsgateway.data.auth.SessionStore
import com.example.smsgateway.data.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

class MercureSseClient(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val sessionStore: SessionStore,
    private val repository: MessageRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val factory = EventSources.createFactory(okHttpClient)
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var eventSource: EventSource? = null

    fun connect() {
        if (eventSource != null) return
        val config = sessionStore.read()
        val url = config.baseUrl.trimEnd('/') + "/.well-known/mercure?topic=sms/outgoing"
        if (config.jwt.isBlank()) return
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.jwt}")
            .build()
        eventSource = factory.newEventSource(request, listener)
    }

    fun disconnect() {
        eventSource?.cancel()
        eventSource = null
    }

    private val listener = object : EventSourceListener() {
        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            scope.launch {
                runCatching {
                    val ulid = json.decodeFromString<JsonObject>(data)["ulid"]?.toString()?.trim('"')
                    if (!ulid.isNullOrBlank()) {
                        repository.processOutgoingUlid(ulid)
                    }
                }.onFailure {
                    Log.e(TAG, "Failed to process SSE event", it)
                }
            }
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            Log.e(TAG, "SSE connection failed", t)
            disconnect()
        }
    }

    companion object {
        private const val TAG = "MercureSseClient"
    }
}
