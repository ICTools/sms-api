package com.example.smsgateway.domain

sealed class SseState {
    object Idle : SseState()
    object Disabled : SseState()
    object WaitingNetwork : SseState()
    object Connecting : SseState()
    object Connected : SseState()
    object Reauthenticating : SseState()
    data class Reconnecting(val attempt: Int, val reason: String? = null) : SseState()

    val isConnected: Boolean get() = this is Connected
}
