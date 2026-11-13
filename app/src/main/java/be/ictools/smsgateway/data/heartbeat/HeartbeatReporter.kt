package be.ictools.smsgateway.data.heartbeat

import android.content.Context
import android.util.Log
import be.ictools.smsgateway.BuildConfig
import be.ictools.smsgateway.data.api.GatewayApi
import be.ictools.smsgateway.data.api.HeartbeatRequest
import be.ictools.smsgateway.data.api.HttpException
import be.ictools.smsgateway.data.auth.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HeartbeatState {
    data object Never : HeartbeatState
    data class Sent(val at: Long) : HeartbeatState
    data class Failed(val at: Long, val reason: String) : HeartbeatState
}

/**
 * Tells the server the gateway is alive (POST /api/mobile/heartbeat). The server alerts by e-mail
 * when no heartbeat arrived for SMS_GATEWAY_OFFLINE_THRESHOLD_MINUTES (10 by default), so
 * [INTERVAL_MS] must stay well below that.
 */
class HeartbeatReporter(
    context: Context,
    private val api: GatewayApi,
    private val sessionStore: SessionStore,
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<HeartbeatState>(HeartbeatState.Never)
    val state: StateFlow<HeartbeatState> = _state.asStateFlow()

    suspend fun send(): Result<Unit> {
        if (!sessionStore.isConfigured()) {
            return Result.failure(IllegalStateException("Configuration missing"))
        }
        val status = DeviceStatus.read(appContext)
        val result = api.sendHeartbeat(
            HeartbeatRequest(
                batteryLevel = status.batteryLevel,
                charging = status.charging,
                network = status.network,
                appVersion = BuildConfig.VERSION_NAME,
            ),
        )
        val now = System.currentTimeMillis()
        _state.value = result.fold(
            onSuccess = { HeartbeatState.Sent(now) },
            onFailure = { error ->
                Log.w(TAG, "Heartbeat failed: ${error.message}")
                HeartbeatState.Failed(now, describeFailure(error))
            },
        )
        return result
    }

    companion object {
        private const val TAG = "Heartbeat"

        /** Below the server's 10 min offline threshold, with room for one lost heartbeat. */
        const val INTERVAL_MS = 4 * 60 * 1000L

        fun describeFailure(error: Throwable): String =
            if (error is HttpException && error.code == 403) {
                "403 : la clé API n'a pas le scope sms_gateway"
            } else {
                error.message ?: error.javaClass.simpleName
            }
    }
}
