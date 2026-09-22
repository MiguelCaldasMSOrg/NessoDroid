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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.miguelcaldas.nessodroid.ui.NessoScreen
import org.miguelcaldas.nessodroid.ui.NessoViewModel
import org.miguelcaldas.nessodroid.ui.theme.NessoTheme
import org.miguelcaldas.nessodroid.transport.NessoBleDevice

class MainActivity : ComponentActivity() {
    private val viewModel: NessoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NessoTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var blePermissionPending by rememberSaveable { mutableStateOf(false) }
                var pendingBleAddress by rememberSaveable { mutableStateOf<String?>(null) }
                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    if (blePermissionPending) {
                        val deviceAddress = pendingBleAddress
                        blePermissionPending = false
                        pendingBleAddress = null
                        if (requiredBlePermissions().all { permission -> hasPermission(this@MainActivity, permission) }) {
                            val device = viewModel.uiState.value.bleDevices.firstOrNull { it.address == deviceAddress }
                            if (device != null) {
                                viewModel.connectBle(device)
                            } else {
                                viewModel.startBleScan()
                            }
                        } else {
                            viewModel.reportBlePermissionDenied()
                        }
                    }
                }

                fun runWithBlePermissions(device: NessoBleDevice? = null) {
                    val missing = requiredBlePermissions().filterNot { hasPermission(this@MainActivity, it) }
                    if (missing.isEmpty()) {
                        if (device == null) {
                            viewModel.startBleScan()
                        } else {
                            viewModel.connectBle(device)
                        }
                    } else {
                        blePermissionPending = true
                        pendingBleAddress = device?.address
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }

                NessoScreen(
                    state = state,
                    onTransportSelected = viewModel::selectTransport,
                    onHttpEndpointChanged = viewModel::setHttpEndpoint,
                    onRefreshHttpStatus = viewModel::refreshHttpStatus,
                    onStartBleScan = {
                        runWithBlePermissions()
                    },
                    onStopBleScan = viewModel::stopBleScan,
                    onConnectBle = { device ->
                        runWithBlePermissions(device)
                    },
                    onDisconnectBle = viewModel::disconnectBle,
                    onCommandChanged = viewModel::setCommand,
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