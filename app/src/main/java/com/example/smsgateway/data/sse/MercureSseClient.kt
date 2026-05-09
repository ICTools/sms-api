package com.example.smsgateway.data.sse

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.smsgateway.data.auth.AuthRepository
import com.example.smsgateway.data.auth.SessionStore
import com.example.smsgateway.data.repository.MessageRepository
import com.example.smsgateway.domain.SseState
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val authRepository: AuthRepository,
    private val repository: MessageRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val factory = EventSources.createFactory(okHttpClient)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<SseState>(SseState.Idle)
    val state: StateFlow<SseState> = _state.asStateFlow()

    private var loopJob: Job? = null
    @Volatile private var eventSource: EventSource? = null
    private val wakeup = Channel<Unit>(Channel.CONFLATED)
    private val networkAvailable = AtomicBoolean(true)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            networkAvailable.set(true)
            wakeup.trySend(Unit)
        }

        override fun onLost(network: Network) {
            networkAvailable.set(false)
            eventSource?.cancel()
        }
    }
    private var networkRegistered = false

    fun start() {
        if (loopJob?.isActive == true) return
        registerNetworkCallback()
        loopJob = scope.launch { runLoop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        eventSource?.cancel()
        eventSource = null
        unregisterNetworkCallback()
        _state.value = SseState.Idle
    }

    fun forceReconnect() {
        eventSource?.cancel()
        wakeup.trySend(Unit)
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (scope.isActive) {
            val config = sessionStore.read()
            if (config.baseUrl.isBlank() || config.apiKey.isBlank() || config.apiSecret.isBlank()) {
                _state.value = SseState.Disabled
                wakeup.receive()
                continue
            }
            if (!networkAvailable.get()) {
                _state.value = SseState.WaitingNetwork
                wakeup.receive()
                continue
            }
            if (config.jwt.isBlank()) {
                _state.value = SseState.Reauthenticating
                val refreshed = authRepository.exchangeApiKey()
                if (refreshed.isFailure) {
                    attempt++
                    _state.value = SseState.Reconnecting(attempt, refreshed.exceptionOrNull()?.message)
                    waitOrWake(backoffMillis(attempt))
                    continue
                }
            }
            _state.value = if (attempt == 0) SseState.Connecting else SseState.Reconnecting(attempt)
            when (val outcome = openConnection(sessionStore.read())) {
                Outcome.Closed -> {
                    attempt = 0
                    _state.value = SseState.Idle
                    waitOrWake(2_000)
                }
                Outcome.AuthFailed -> {
                    sessionStore.clearJwt()
                    _state.value = SseState.Reauthenticating
                    authRepository.exchangeApiKey()
                    attempt++
                    waitOrWake(backoffMillis(attempt))
                }
                is Outcome.Error -> {
                    attempt++
                    _state.value = SseState.Reconnecting(attempt, outcome.reason)
                    waitOrWake(backoffMillis(attempt))
                }
            }
        }
    }

    private suspend fun openConnection(config: com.example.smsgateway.data.auth.ServerConfig): Outcome {
        val deferred = CompletableDeferred<Outcome>()
        val url = config.baseUrl.trimEnd('/') + "/.well-known/mercure?topic=sms/outgoing"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.jwt}")
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                _state.value = SseState.Connected
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                scope.launch {
                    runCatching {
                        val ulid = json.decodeFromString<JsonObject>(data)["ulid"]
                            ?.jsonPrimitive
                            ?.content
                            ?.takeIf { it.isNotBlank() }
                            ?: return@runCatching
                        repository.processOutgoingUlid(ulid)
                    }.onFailure { Log.e(TAG, "Event handling failed", it) }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!deferred.isCompleted) deferred.complete(Outcome.Closed)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (deferred.isCompleted) return
                val code = response?.code
                deferred.complete(
                    when {
                        code == 401 -> Outcome.AuthFailed
                        else -> Outcome.Error(t?.message ?: "code=$code")
                    },
                )
            }
        }
        val source = factory.newEventSource(request, listener)
        eventSource = source
        return try {
            deferred.await()
        } finally {
            source.cancel()
            eventSource = null
        }
    }

    private suspend fun waitOrWake(ms: Long) {
        withTimeoutOrNull(ms) { wakeup.receive() }
    }

    private fun backoffMillis(attempt: Int): Long {
        val capped = min(attempt - 1, 5).coerceAtLeast(0)
        return min(60_000L, 2_000L shl capped)
    }

    private fun registerNetworkCallback() {
        if (networkRegistered) return
        runCatching {
            val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
            networkRegistered = true
        }.onFailure { Log.w(TAG, "Network callback registration failed", it) }
    }

    private fun unregisterNetworkCallback() {
        if (!networkRegistered) return
        runCatching {
            appContext.getSystemService(ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(networkCallback)
        }
        networkRegistered = false
    }

    private sealed class Outcome {
        object Closed : Outcome()
        object AuthFailed : Outcome()
        data class Error(val reason: String) : Outcome()
    }

    companion object {
        private const val TAG = "MercureSseClient"
    }
}
