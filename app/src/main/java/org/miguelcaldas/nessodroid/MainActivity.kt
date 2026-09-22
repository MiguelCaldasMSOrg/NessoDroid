package org.miguelcaldas.nessodroid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.miguelcaldas.nessodroid.ui.NessoScreen
import org.miguelcaldas.nessodroid.ui.NessoViewModel
import org.miguelcaldas.nessodroid.ui.theme.NessoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: NessoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NessoTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var pendingBleAction by remember { mutableStateOf<(() -> Unit)?>(null) }
                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                    if (grants.values.all { it }) {
                        pendingBleAction?.invoke()
                    } else {
                        viewModel.reportBlePermissionDenied()
                    }
                    pendingBleAction = null
                }

                fun runWithBlePermissions(action: () -> Unit) {
                    val missing = requiredBlePermissions().filterNot { hasPermission(this@MainActivity, it) }
                    if (missing.isEmpty()) {
                        action()
                    } else {
                        pendingBleAction = action
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }

                NessoScreen(
                    state = state,
                    onTransportSelected = viewModel::selectTransport,
                    onHttpEndpointChanged = viewModel::setHttpEndpoint,
                    onRefreshHttpStatus = viewModel::refreshHttpStatus,
                    onStartBleScan = {
                        runWithBlePermissions(viewModel::startBleScan)
                    },
                    onStopBleScan = viewModel::stopBleScan,
                    onConnectBle = { device ->
                        runWithBlePermissions {
                            viewModel.connectBle(device)
                        }
                    },
                    onDisconnectBle = viewModel::disconnectBle,
                    onCommandChanged = viewModel::setCommand,
                    onPresetSelected = viewModel::setCommand,
                    onSendCommand = viewModel::sendCommand,
                    onClearLog = viewModel::clearLog,
                )
            }
        }
    }

    private fun requiredBlePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}