package com.example.smsgateway.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.smsgateway.R
import com.example.smsgateway.domain.SseState
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

        if (state.permissionsDenied) {
            val context = LocalContext.current
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.permissions_denied),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }) {
                        Text("Ouvrir les réglages")
                    }
                }
            }
        }

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
                OutlinedTextField(
                    value = state.apiSecretInput,
                    onValueChange = viewModel::onApiSecretChanged,
                    label = { Text("API secret") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = viewModel::saveConfiguration) {
                    Text("Enregistrer & connecter")
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
                Text("État SSE: ${describeSseState(state.connection.sseState)}")
                Text("File d'attente: ${state.connection.pendingActionCount} actions")
                Text("JWT présent: ${state.connection.hasJwt}")
                Text("API key présente: ${state.connection.hasApiKey}")
                Text("API secret présent: ${state.connection.hasApiSecret}")
                state.connection.lastError?.let {
                    Text("Erreur: $it", color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = viewModel::reconnectSse) {
                    Text("Forcer reconnexion")
                }
            }
        }
    }
}

@Composable
private fun describeSseState(state: SseState): String = when (state) {
    SseState.Idle -> stringResource(R.string.sse_state_idle)
    SseState.Disabled -> stringResource(R.string.sse_state_disabled)
    SseState.WaitingNetwork -> stringResource(R.string.sse_state_waiting_network)
    SseState.Connecting -> stringResource(R.string.sse_state_connecting)
    SseState.Connected -> stringResource(R.string.sse_state_connected)
    SseState.Reauthenticating -> stringResource(R.string.sse_state_reauthenticating)
    is SseState.Reconnecting -> stringResource(R.string.sse_state_reconnecting, state.attempt) +
        (state.reason?.let { " — $it" } ?: "")
}
