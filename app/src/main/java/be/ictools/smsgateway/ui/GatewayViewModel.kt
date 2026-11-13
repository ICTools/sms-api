package be.ictools.smsgateway.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import be.ictools.smsgateway.data.AppContainer
import be.ictools.smsgateway.data.auth.CredentialsDisplay
import be.ictools.smsgateway.data.heartbeat.HeartbeatState
import be.ictools.smsgateway.domain.ConnectionState
import be.ictools.smsgateway.domain.ConversationSummary
import be.ictools.smsgateway.domain.MessageItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GatewayUiState(
    val connection: ConnectionState = ConnectionState(),
    val conversations: List<ConversationSummary> = emptyList(),
    val selectedAddress: String? = null,
    val composeBody: String = "",
    val credentials: CredentialsDisplay = CredentialsDisplay(),
    val baseUrlInput: String = "",
    // Key and secret inputs start empty: saved values are never put back on screen.
    val apiKeyInput: String = "",
    val apiSecretInput: String = "",
    val editUnlocked: Boolean = false,
    val permissionsDenied: Boolean = false,
    val batteryOptimized: Boolean = false,
    val heartbeat: HeartbeatState = HeartbeatState.Never,
) {
    // Logs and crash reports must not carry what the user is typing as credentials.
    override fun toString(): String =
        "GatewayUiState(connection=$connection, conversations=${conversations.size}, " +
            "selectedAddress=$selectedAddress, credentials=$credentials, baseUrlInput=$baseUrlInput, " +
            "apiKeyInput=${redact(apiKeyInput)}, apiSecretInput=${redact(apiSecretInput)}, " +
            "editUnlocked=$editUnlocked, permissionsDenied=$permissionsDenied, " +
            "batteryOptimized=$batteryOptimized, heartbeat=$heartbeat)"

    private fun redact(value: String): String = if (value.isEmpty()) "" else "<redacted>"
}

class GatewayViewModel(
    private val appContainer: AppContainer,
) : ViewModel() {
    private val selectedAddress = MutableStateFlow<String?>(null)
    private val draftBody = MutableStateFlow("")
    private val configRefresh = MutableStateFlow(0L)
    private val lastError = MutableStateFlow<String?>(null)
    private val permissionsDenied = MutableStateFlow(false)
    private val batteryOptimized = MutableStateFlow(false)
    private val editUnlocked = MutableStateFlow(false)

    private val baseUrlInput = MutableStateFlow(appContainer.sessionStore.display().baseUrl)
    private val apiKeyInput = MutableStateFlow("")
    private val apiSecretInput = MutableStateFlow("")

    private val credentialsFlow: Flow<CredentialsDisplay> = configRefresh.map { appContainer.sessionStore.display() }

    private val connectionStateFlow: StateFlow<ConnectionState> = combine(
        credentialsFlow,
        appContainer.sseClient.state,
        appContainer.repository.observePendingActionCount(),
        lastError,
    ) { credentials, sseState, pending, error ->
        ConnectionState(
            baseUrl = credentials.baseUrl,
            hasApiKey = credentials.hasApiKey,
            hasApiSecret = credentials.hasSecret,
            hasJwt = credentials.hasJwt,
            sseState = sseState,
            pendingActionCount = pending,
            lastError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState())

    private val formFlow: Flow<FormBundle> = combine(
        combine(baseUrlInput, apiKeyInput, apiSecretInput, ::Triple),
        editUnlocked,
        credentialsFlow,
    ) { (baseUrl, apiKey, apiSecret), unlocked, credentials ->
        FormBundle(baseUrl, apiKey, apiSecret, unlocked, credentials)
    }

    private val deviceFlow: Flow<DeviceBundle> = combine(
        permissionsDenied,
        batteryOptimized,
        appContainer.heartbeatReporter.state,
        ::DeviceBundle,
    )

    val uiState: StateFlow<GatewayUiState> = combine(
        appContainer.repository.observeConversations(),
        combine(selectedAddress, draftBody, ::Pair),
        connectionStateFlow,
        formFlow,
        deviceFlow,
    ) { conversations, (address, body), connection, form, device ->
        GatewayUiState(
            connection = connection,
            conversations = conversations,
            selectedAddress = address,
            composeBody = body,
            credentials = form.credentials,
            baseUrlInput = form.baseUrl,
            apiKeyInput = form.apiKey,
            apiSecretInput = form.apiSecret,
            editUnlocked = form.unlocked,
            permissionsDenied = device.permissionsDenied,
            batteryOptimized = device.batteryOptimized,
            heartbeat = device.heartbeat,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GatewayUiState(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedMessages: StateFlow<List<MessageItem>> = selectedAddress
        .flatMapLatest { address ->
            if (address == null) flowOf(emptyList()) else appContainer.repository.observeMessages(address)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            runCatching { appContainer.repository.importDeviceHistory() }
        }
    }

    fun onPermissionsResult(results: Map<String, Boolean>) {
        permissionsDenied.value = results.values.any { !it }
    }

    fun onBatteryOptimizationChecked(exempt: Boolean) {
        batteryOptimized.value = !exempt
    }

    fun onBaseUrlChanged(value: String) { baseUrlInput.value = value }
    fun onApiKeyChanged(value: String) { apiKeyInput.value = value }
    fun onApiSecretChanged(value: String) { apiSecretInput.value = value }

    /** Called once the device lock was confirmed (or was not required). */
    fun unlockEditing() {
        baseUrlInput.value = appContainer.sessionStore.display().baseUrl
        editUnlocked.value = true
    }

    /** Also called when the app leaves the screen, so an unlocked form does not stay open. */
    fun cancelEditing() {
        lockEditing()
    }

    fun onUnlockFailed(reason: String) {
        lastError.value = reason
    }

    fun saveConfiguration() {
        viewModelScope.launch {
            appContainer.repository.saveSettings(baseUrlInput.value, apiKeyInput.value, apiSecretInput.value)
            lockEditing()
            configRefresh.value = System.currentTimeMillis()
            appContainer.authRepository.exchangeApiKey()
                .onSuccess {
                    lastError.value = null
                    configRefresh.value = System.currentTimeMillis()
                    appContainer.sseClient.forceReconnect()
                    appContainer.heartbeatReporter.send()
                }
                .onFailure { lastError.value = it.message }
        }
    }

    /** Forgets the API key, secret and JWT; the gateway stays disabled until reconfigured. */
    fun resetConfiguration() {
        appContainer.sessionStore.clearCredentials()
        lockEditing()
        lastError.value = null
        configRefresh.value = System.currentTimeMillis()
        appContainer.sseClient.forceReconnect()
    }

    fun reconnectSse() {
        appContainer.sseClient.forceReconnect()
    }

    fun selectConversation(address: String) { selectedAddress.value = address }
    fun updateDraft(value: String) { draftBody.value = value }

    fun sendCurrentDraft() {
        val address = selectedAddress.value ?: return
        val body = draftBody.value.trim()
        if (body.isBlank()) return
        viewModelScope.launch {
            runCatching { appContainer.repository.sendFromPhone(address, body) }
                .onFailure { lastError.value = it.message }
            draftBody.value = ""
        }
    }

    private fun lockEditing() {
        editUnlocked.value = false
        apiKeyInput.value = ""
        apiSecretInput.value = ""
    }

    class Factory(
        private val appContainer: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GatewayViewModel(appContainer) as T
        }
    }
}

private data class FormBundle(
    val baseUrl: String,
    val apiKey: String,
    val apiSecret: String,
    val unlocked: Boolean,
    val credentials: CredentialsDisplay,
)

private data class DeviceBundle(
    val permissionsDenied: Boolean,
    val batteryOptimized: Boolean,
    val heartbeat: HeartbeatState,
)
