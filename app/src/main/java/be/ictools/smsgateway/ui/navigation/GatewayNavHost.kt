package be.ictools.smsgateway.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import be.ictools.smsgateway.ui.GatewayViewModel
import be.ictools.smsgateway.ui.screens.ConfigurationScreen
import be.ictools.smsgateway.ui.screens.ConversationDetailScreen
import be.ictools.smsgateway.ui.screens.ConversationsScreen

private enum class RootDestination(val route: String, val label: String) {
    Config("config", "Config"),
    Conversations("conversations", "Messages"),
}

@Composable
fun GatewayNavHost(viewModel: GatewayViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                RootDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = { navController.navigate(destination.route) },
                        label = { Text(destination.label) },
                        icon = {},
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = RootDestination.Config.route,
        ) {
            composable(RootDestination.Config.route) {
                ConfigurationScreen(
                    viewModel = viewModel,
                    padding = padding,
                )
            }
            composable(RootDestination.Conversations.route) {
                ConversationsScreen(
                    viewModel = viewModel,
                    padding = padding,
                    openConversation = { address ->
                        viewModel.selectConversation(address)
                        navController.navigate("conversation/$address")
                    },
                )
            }
            composable("conversation/{address}") {
                ConversationDetailScreen(
                    viewModel = viewModel,
                    padding = padding,
                )
            }
        }
    }
}

