package com.example.smsgateway.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
fun ConfigurationScreen(
    viewModel: GatewayViewModel,
    padding: PaddingValues,
) {
    val state by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Connexion serveur", style = MaterialTheme.typography.headlineMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.baseUrlInput,
                    onValueChange = viewModel::onBaseUrlChanged,
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.apiKeyInput,
                    onValueChange = viewModel::onApiKeyChanged,
                    label = { Text("API key") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = viewModel::saveConfiguration) {
                    Text("Enregistrer")
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("JWT présent: ${state.connection.hasJwt}")
                Text("API key présente: ${state.connection.hasApiKey}")
                Text("SSE connectée: ${state.connection.isSseConnected}")
                state.connection.lastError?.let { Text("Erreur: $it") }
            }
        }
    }
}

