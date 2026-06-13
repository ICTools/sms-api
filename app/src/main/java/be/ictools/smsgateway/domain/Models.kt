package be.ictools.smsgateway.domain

data class ConversationSummary(
    val address: String,
    val title: String,
    val preview: String,
    val timestamp: Long,
)

data class MessageItem(
    val id: Long,
    val address: String,
    val body: String,
    val direction: String,
    val timestamp: Long,
    val smsStatus: String,
    val syncStatus: String,
)

data class ConnectionState(
    val baseUrl: String = "",
    val hasApiKey: Boolean = false,
    val hasApiSecret: Boolean = false,
    val hasJwt: Boolean = false,
    val sseState: SseState = SseState.Idle,
    val pendingActionCount: Int = 0,
    val lastError: String? = null,
) {
    val isSseConnected: Boolean get() = sseState.isConnected
}
