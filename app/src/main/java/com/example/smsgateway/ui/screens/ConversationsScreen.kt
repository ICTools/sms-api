package com.example.smsgateway.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

