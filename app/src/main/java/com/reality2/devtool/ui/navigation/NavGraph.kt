package com.reality2.devtool.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.reality2.devtool.data.repository.Reality2Repository
import com.reality2.devtool.ui.screens.scanner.ScannerScreen
import com.reality2.devtool.ui.screens.scanner.ScannerViewModel
import com.reality2.devtool.ui.screens.scanner.ScannerViewModelFactory
import com.reality2.devtool.ui.screens.sentantlist.SentantListScreen
import com.reality2.devtool.ui.screens.sentantlist.SentantListViewModel
import com.reality2.devtool.ui.screens.sentantlist.SentantListViewModelFactory
import com.reality2.devtool.ui.screens.signalmonitor.SignalMonitorScreen
import com.reality2.devtool.ui.screens.signalmonitor.SignalMonitorViewModel
import com.reality2.devtool.ui.screens.signalmonitor.SignalMonitorViewModelFactory
import com.reality2.devtool.util.Constants

@Composable
fun NavGraph(
    navController: NavHostController,
    repository: Reality2Repository,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Constants.ROUTE_SCANNER,
        modifier = modifier
    ) {
        // Scanner screen
        composable(Constants.ROUTE_SCANNER) {
            val viewModel: ScannerViewModel = viewModel(
                factory = ScannerViewModelFactory(repository)
            )
            ScannerScreen(
                viewModel = viewModel,
                onNodeClick = { address ->
                    navController.navigate("sentant_list/$address")
                }
            )
        }

        // Sentant List screen
        composable(
            route = "sentant_list/{nodeAddress}",
            arguments = listOf(navArgument("nodeAddress") { type = NavType.StringType })
        ) { backStackEntry ->
            val nodeAddress = backStackEntry.arguments?.getString("nodeAddress") ?: return@composable
            val viewModel: SentantListViewModel = viewModel(
                factory = SentantListViewModelFactory(repository, nodeAddress)
            )
            SentantListScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Signal Monitor screen
        composable(Constants.ROUTE_SIGNAL_MONITOR) {
            val viewModel: SignalMonitorViewModel = viewModel(
                factory = SignalMonitorViewModelFactory(repository)
            )
            SignalMonitorScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
