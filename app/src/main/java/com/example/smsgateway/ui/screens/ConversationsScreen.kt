package com.example.smsgateway.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.smsgateway.R
import com.example.smsgateway.domain.SseState
import com.example.smsgateway.ui.GatewayViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun ConversationsScreen(
    viewModel: GatewayViewModel,
    padding: PaddingValues,
    openConversation: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Conversations locales", style = MaterialTheme.typography.headlineMedium)
        StatusBar(state.connection.sseState, state.connection.pendingActionCount)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.conversations) { conversation ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openConversation(conversation.address) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(conversation.title, style = MaterialTheme.typography.titleMedium)
                        Text(conversation.preview, maxLines = 2)
                        Text(
                            DateFormat.getDateTimeInstance().format(Date(conversation.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBar(sseState: SseState, pending: Int) {
    val color = when (sseState) {
        SseState.Connected -> MaterialTheme.colorScheme.tertiaryContainer
        is SseState.Reconnecting, SseState.Reauthenticating -> MaterialTheme.colorScheme.secondaryContainer
        SseState.Disabled -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val label = when (sseState) {
        SseState.Idle -> stringResource(R.string.sse_state_idle)
        SseState.Disabled -> stringResource(R.string.sse_state_disabled)
        SseState.WaitingNetwork -> stringResource(R.string.sse_state_waiting_network)
        SseState.Connecting -> stringResource(R.string.sse_state_connecting)
        SseState.Connected -> stringResource(R.string.sse_state_connected)
        SseState.Reauthenticating -> stringResource(R.string.sse_state_reauthenticating)
        is SseState.Reconnecting -> stringResource(R.string.sse_state_reconnecting, sseState.attempt)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("File: $pending", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
