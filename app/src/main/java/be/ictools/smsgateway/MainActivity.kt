package be.ictools.smsgateway

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import be.ictools.smsgateway.service.BatteryOptimization
import be.ictools.smsgateway.ui.GatewayViewModel
import be.ictools.smsgateway.ui.navigation.GatewayNavHost
import be.ictools.smsgateway.ui.screens.BatteryOptimizationDialog
import be.ictools.smsgateway.ui.theme.SmsGatewayTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GatewayViewModel by viewModels {
        GatewayViewModel.Factory((application as SmsGatewayApp).appContainer)
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            viewModel.onPermissionsResult(results)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmsGatewayTheme {
                LaunchedEffect(Unit) {
                    permissionLauncher.launch(requiredPermissions())
                }
                val state by viewModel.uiState.collectAsState()
                // Shown once per launch while the exemption is missing; the config screen keeps a reminder.
                var batteryDialogDismissed by remember { mutableStateOf(false) }
                if (state.batteryOptimized && !batteryDialogDismissed) {
                    BatteryOptimizationDialog(
                        onConfirm = {
                            batteryDialogDismissed = true
                            startActivity(BatteryOptimization.requestExemptionIntent(this))
                        },
                        onDismiss = { batteryDialogDismissed = true },
                    )
                }
                GatewayNavHost(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onBatteryOptimizationChecked(BatteryOptimization.isExempt(this))
    }

    override fun onStop() {
        viewModel.cancelEditing()
        super.onStop()
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions.toTypedArray()
    }
}
