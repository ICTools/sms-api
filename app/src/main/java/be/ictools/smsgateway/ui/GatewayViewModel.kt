package be.ictools.smsgateway.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import be.ictools.smsgateway.data.AppContainer
import be.ictools.smsgateway.data.auth.ServerConfig
import be.ictools.smsgateway.domain.ConnectionState
import be.ictools.smsgateway.domain.ConversationSummary
import be.ictools.smsgateway.domain.MessageItem
import be.ictools.smsgateway.domain.SseState
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
    val baseUrlInput: String = "",
    val apiKeyInput: String = "",
    val apiSecretInput: String = "",
    val permissionsDenied: Boolean = false,
)

class GatewayViewModel(
    private val appContainer: AppContainer,
) : ViewModel() {
    private val selectedAddress = MutableStateFlow<String?>(null)
    private val draftBody = MutableStateFlow("")
    private val configRefresh = MutableStateFlow(0L)
    private val lastError = MutableStateFlow<String?>(null)
    private val permissionsDenied = MutableStateFlow(false)

    private val initialConfig = appContainer.sessionStore.read()
    private val baseUrlInput = MutableStateFlow(initialConfig.baseUrl)
    private val apiKeyInput = MutableStateFlow(initialConfig.apiKey)
    private val apiSecretInput = MutableStateFlow(initialConfig.apiSecret)

    private val configFlow: Flow<ServerConfig> = configRefresh.map { appContainer.sessionStore.read() }

    private val connectionStateFlow: StateFlow<ConnectionState> = combine(
        configFlow,
        appContainer.sseClient.state,
        appContainer.repository.observePendingActionCount(),
        lastError,
    ) { config, sseState, pending, error ->
        ConnectionState(
            baseUrl = config.baseUrl,
            hasApiKey = config.apiKey.isNotBlank(),
            hasApiSecret = config.apiSecret.isNotBlank(),
            hasJwt = config.jwt.isNotBlank(),
            sseState = sseState,
            pendingActionCount = pending,
            lastError = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState())

    val uiState: StateFlow<GatewayUiState> = combine(
        appContainer.repository.observeConversations(),
        selectedAddress,
        draftBody,
        connectionStateFlow,
        combine(baseUrlInput, apiKeyInput, apiSecretInput, permissionsDenied) { baseUrl, apiKey, apiSecret, denied ->
            InputBundle(baseUrl, apiKey, apiSecret, denied)
        },
    ) { conversations, address, body, connection, inputs ->
        GatewayUiState(
            connection = connection,
            conversations = conversations,
            selectedAddress = address,
            composeBody = body,
            baseUrlInput = inputs.baseUrl,
            apiKeyInput = inputs.apiKey,
            apiSecretInput = inputs.apiSecret,
            permissionsDenied = inputs.permissionsDenied,
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

    fun onBaseUrlChanged(value: String) { baseUrlInput.value = value }
    fun onApiKeyChanged(value: String) { apiKeyInput.value = value }
    fun onApiSecretChanged(value: String) { apiSecretInput.value = value }

    fun saveConfiguration() {
        viewModelScope.launch {
            appContainer.repository.saveSettings(baseUrlInput.value, apiKeyInput.value, apiSecretInput.value)
            configRefresh.value = System.currentTimeMillis()
            appContainer.authRepository.exchangeApiKey()
                .onSuccess {
                    lastError.value = null
                    configRefresh.value = System.currentTimeMillis()
                    appContainer.sseClient.forceReconnect()
                }
                .onFailure { lastError.value = it.message }
        }
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

    class Factory(
        private val appContainer: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GatewayViewModel(appContainer) as T
        }
    }
}

private data class InputBundle(
    val baseUrl: String,
    val apiKey: String,
    val apiSecret: String,
    val permissionsDenied: Boolean,
)
