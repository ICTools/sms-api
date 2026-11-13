package be.ictools.smsgateway.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import be.ictools.smsgateway.R
import be.ictools.smsgateway.data.heartbeat.HeartbeatState
import be.ictools.smsgateway.domain.SseState
import be.ictools.smsgateway.service.BatteryOptimization
import be.ictools.smsgateway.ui.GatewayUiState
import be.ictools.smsgateway.ui.GatewayViewModel
import be.ictools.smsgateway.ui.security.ConfigEditGate
import be.ictools.smsgateway.ui.security.DeviceLock
import be.ictools.smsgateway.ui.security.UnlockRequirement
import java.text.DateFormat
import java.util.Date

@Composable
fun ConfigurationScreen(
    viewModel: GatewayViewModel,
    padding: PaddingValues,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Connexion serveur", style = MaterialTheme.typography.headlineMedium)

        if (state.permissionsDenied) {
            WarningCard(
                text = stringResource(R.string.permissions_denied),
                action = "Ouvrir les réglages",
                onAction = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
            )
        }

        if (state.batteryOptimized) {
            WarningCard(
                text = stringResource(R.string.battery_optimization_warning),
                action = "Désactiver l'optimisation",
                onAction = { context.startActivity(BatteryOptimization.requestExemptionIntent(context)) },
            )
        }

        if (state.editUnlocked) {
            CredentialsForm(state, viewModel)
        } else {
            CredentialsSummary(
                state = state,
                onEdit = {
                    val requirement = ConfigEditGate.requirement(
                        hasStoredCredentials = state.credentials.hasStoredCredentials,
                        deviceSecure = DeviceLock.isDeviceSecure(context),
                    )
                    val activity = context.findActivity()
                    when {
                        requirement != UnlockRequirement.DEVICE_CREDENTIAL -> viewModel.unlockEditing()
                        activity == null -> viewModel.onUnlockFailed("Déverrouillage indisponible")
                        else -> DeviceLock.authenticate(
                            activity = activity,
                            title = context.getString(R.string.unlock_title),
                            onSuccess = viewModel::unlockEditing,
                            onError = viewModel::onUnlockFailed,
                        )
                    }
                },
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("État SSE: ${describeSseState(state.connection.sseState)}")
                Text("Heartbeat: ${describeHeartbeat(state.heartbeat)}")
                Text("File d'attente: ${state.connection.pendingActionCount} actions")
                Text("JWT présent: ${state.connection.hasJwt}")
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
private fun CredentialsSummary(state: GatewayUiState, onEdit: () -> Unit) {
    val credentials = state.credentials
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Base URL: ${credentials.baseUrl.ifBlank { "—" }}")
            Text("API key: ${credentials.maskedApiKey.ifBlank { "non configurée" }}")
            Text(if (credentials.hasSecret) stringResource(R.string.secret_configured) else "API secret: non configuré")
            Button(onClick = onEdit) {
                Text(if (credentials.hasStoredCredentials) "Modifier (déverrouillage requis)" else "Configurer")
            }
        }
    }
}

@Composable
private fun CredentialsForm(state: GatewayUiState, viewModel: GatewayViewModel) {
    var confirmReset by remember { mutableStateOf(false) }
    val credentials = state.credentials
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
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.apiKeyInput,
                onValueChange = viewModel::onApiKeyChanged,
                label = { Text("API key") },
                placeholder = { Text(credentials.maskedApiKey) },
                supportingText = if (credentials.hasApiKey) {
                    { Text("Laisser vide pour garder ${credentials.maskedApiKey}") }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.apiSecretInput,
                onValueChange = viewModel::onApiSecretChanged,
                label = { Text("Nouveau API secret") },
                supportingText = if (credentials.hasSecret) {
                    { Text(stringResource(R.string.secret_configured_keep)) }
                } else {
                    null
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::saveConfiguration) {
                    Text("Enregistrer & connecter")
                }
                OutlinedButton(onClick = viewModel::cancelEditing) {
                    Text("Annuler")
                }
            }
            if (credentials.hasStoredCredentials) {
                TextButton(onClick = { confirmReset = true }) {
                    Text("Réinitialiser la configuration", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Réinitialiser la configuration ?") },
            text = { Text(stringResource(R.string.reset_explanation)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.resetConfiguration()
                }) { Text("Réinitialiser") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
fun BatteryOptimizationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_optimization_title)) },
        text = { Text(stringResource(R.string.battery_optimization_explanation)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Autoriser") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Plus tard") } },
    )
}

@Composable
private fun WarningCard(text: String, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onAction) {
                Text(action)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun describeHeartbeat(state: HeartbeatState): String = when (state) {
    HeartbeatState.Never -> "pas encore envoyé"
    is HeartbeatState.Sent -> "OK à ${formatTime(state.at)}"
    is HeartbeatState.Failed -> "échec à ${formatTime(state.at)} (${state.reason})"
}

private fun formatTime(millis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

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
