package com.example.smsgateway.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smsgateway.ui.GatewayViewModel

@Composable
fun ConversationDetailScreen(
    viewModel: GatewayViewModel,
    padding: PaddingValues,
) {
    val state by viewModel.uiState.collectAsState()
    val messages by viewModel.selectedMessages.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(state.selectedAddress.orEmpty(), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(
            modifier = Modifier.weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(message.body)
                        Text(
                            "${message.direction} • ${message.smsStatus} • ${message.syncStatus}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.composeBody,
                onValueChange = viewModel::updateDraft,
                label = { Text("Message") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::sendCurrentDraft) {
                Text("Envoyer")
            }
        }
    }
}
