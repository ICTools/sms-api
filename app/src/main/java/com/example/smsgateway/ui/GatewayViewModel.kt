package com.example.smsgateway.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smsgateway.data.AppContainer
import com.example.smsgateway.domain.ConnectionState
import com.example.smsgateway.domain.ConversationSummary
import com.example.smsgateway.domain.MessageItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GatewayUiState(
    val connection: ConnectionState = ConnectionState(),
    val conversations: List<ConversationSummary> = emptyList(),
    val selectedAddress: String? = null,
    val messages: List<MessageItem> = emptyList(),
    val composeBody: String = "",
    val baseUrlInput: String = "",
    val apiKeyInput: String = "",
    val apiSecretInput: String = "",
)

class GatewayViewModel(
    private val appContainer: AppContainer,
) : ViewModel() {
    private val selectedAddress = MutableStateFlow<String?>(null)
    private val draftBody = MutableStateFlow("")
    private val connectionState = MutableStateFlow(appContainer.repository.readConnectionState())
    private val baseUrlInput = MutableStateFlow(appContainer.sessionStore.read().baseUrl)
    private val apiKeyInput = MutableStateFlow(appContainer.sessionStore.read().apiKey)
    private val apiSecretInput = MutableStateFlow(appContainer.sessionStore.read().apiSecret)
    private val connectionInputs = combine(connectionState, baseUrlInput, apiKeyInput, apiSecretInput) { connection, baseUrl, apiKey, apiSecret ->
        ConnectionInputs(connection, baseUrl, apiKey, apiSecret)
    }

    val uiState: StateFlow<GatewayUiState> = combine(
        appContainer.repository.observeConversations(),
        selectedAddress,
        draftBody,
        connectionInputs,
    ) { conversations, address, body, connectionInputs ->
        val connection = connectionInputs.connection
        val baseUrl = connectionInputs.baseUrl
        val apiKey = connectionInputs.apiKey
        val apiSecret = connectionInputs.apiSecret
        GatewayUiState(
            connection = connection,
            conversations = conversations,
            selectedAddress = address,
            messages = emptyList(),
            composeBody = body,
            baseUrlInput = baseUrl,
            apiKeyInput = apiKey,
            apiSecretInput = apiSecret,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GatewayUiState(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedMessages: StateFlow<List<MessageItem>> = selectedAddress
        .flatMapLatest { address ->
            if (address == null) {
                flowOf(emptyList())
            } else {
                appContainer.repository.observeMessages(address)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            runCatching { appContainer.repository.importDeviceHistory() }
        }
    }

    fun onPermissionsResult(results: Map<String, Boolean>) {
        if (results.values.any { !it }) {
            connectionState.value = connectionState.value.copy(lastError = "Certaines permissions sont refusées")
        }
    }

    fun onBaseUrlChanged(value: String) {
        baseUrlInput.value = value
    }

    fun onApiKeyChanged(value: String) {
        apiKeyInput.value = value
    }

    fun onApiSecretChanged(value: String) {
        apiSecretInput.value = value
    }

    fun saveConfiguration() {
        viewModelScope.launch {
            appContainer.repository.saveSettings(baseUrlInput.value, apiKeyInput.value, apiSecretInput.value)
            appContainer.authRepository.exchangeApiKey()
                .onSuccess {
                    appContainer.sseClient.disconnect()
                    appContainer.sseClient.connect()
                    connectionState.value = appContainer.repository.readConnectionState(isSseConnected = true)
                }
                .onFailure {
                    connectionState.value = appContainer.repository.readConnectionState(
                        isSseConnected = false,
                        lastError = it.message,
                    )
                }
        }
    }

    fun selectConversation(address: String) {
        selectedAddress.value = address
    }

    fun updateDraft(value: String) {
        draftBody.value = value
    }

    fun sendCurrentDraft() {
        val address = selectedAddress.value ?: return
        val body = draftBody.value.trim()
        if (body.isBlank()) return
        viewModelScope.launch {
            appContainer.repository.sendFromPhone(address, body)
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

private data class ConnectionInputs(
    val connection: ConnectionState,
    val baseUrl: String,
    val apiKey: String,
    val apiSecret: String,
)
