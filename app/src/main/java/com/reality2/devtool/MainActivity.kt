package com.reality2.devtool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.reality2.devtool.data.ble.BleManager
import com.reality2.devtool.data.repository.Reality2Repository
import com.reality2.devtool.ui.components.PermissionHandler
import com.reality2.devtool.ui.navigation.NavGraph
import com.reality2.devtool.ui.theme.Reality2DevToolTheme

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleManager
    private lateinit var repository: Reality2Repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize BLE manager and repository
        bleManager = BleManager(applicationContext)
        repository = Reality2Repository(bleManager)

        setContent {
            Reality2DevToolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var permissionsGranted by remember { mutableStateOf(false) }

                    PermissionHandler(
                        onPermissionsGranted = { permissionsGranted = true }
                    ) {
                        if (permissionsGranted) {
                            val navController = rememberNavController()
                            NavGraph(
                                navController = navController,
                                repository = repository
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop scanning when app goes to background to save battery
        bleManager.stopScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Disconnect all GATT connections
        bleManager.disconnectAll()
    }
}
