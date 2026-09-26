@file:OptIn(ExperimentalMaterial3Api::class)

package com.masarif.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.masarif.app.MasarifApp
import com.masarif.app.data.prefs.SettingsStore
import com.masarif.app.ui.screens.CategoriesScreen
import com.masarif.app.ui.screens.HomeScreen
import com.masarif.app.ui.screens.OnboardingScreen
import com.masarif.app.ui.screens.QueueScreen
import com.masarif.app.ui.screens.RulesScreen
import com.masarif.app.ui.screens.SendersScreen
import com.masarif.app.ui.screens.SettingsScreen
import com.masarif.app.ui.screens.SubscriptionsScreen
import com.masarif.app.ui.screens.TransactionEditScreen
import com.masarif.app.ui.screens.TransactionsScreen
import com.masarif.app.ui.screens.UnparsedScreen
import kotlinx.coroutines.flow.StateFlow

object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val QUEUE = "queue"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SETTINGS = "settings"
    const val SETTINGS_SENDERS = "settings/senders"
    const val SETTINGS_UNPARSED = "settings/unparsed"
    const val SETTINGS_CATEGORIES = "settings/categories"
    const val SETTINGS_RULES = "settings/rules"
    const val TRANSACTION_EDIT = "transaction/{id}"
    fun transactionEdit(id: Long) = "transaction/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val tag: String)

private val tabs = listOf(
    Tab(Routes.HOME, "الرئيسية", Icons.Filled.Home, "tab_home"),
    Tab(Routes.TRANSACTIONS, "العمليات", Icons.AutoMirrored.Filled.List, "tab_transactions"),
    Tab(Routes.QUEUE, "الانتظار", Icons.Filled.Notifications, "tab_queue"),
    Tab(Routes.SUBSCRIPTIONS, "الاشتراكات", Icons.Filled.Refresh, "tab_subscriptions"),
    Tab(Routes.SETTINGS, "الإعدادات", Icons.Filled.Settings, "tab_settings"),
)

@Composable
fun AppRoot(app: MasarifApp, openQueueRequests: StateFlow<Int>) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Permissions.hasSms(context)) }
    val setupVersion by app.settings.setupVersion.collectAsState(initial = null)

    if (setupVersion == null) return // بانتظار قراءة الإعدادات (لحظة واحدة)
    if (!granted || (setupVersion ?: 0) < SettingsStore.CURRENT_SETUP_VERSION) {
        OnboardingScreen(app = app, onDone = { granted = true })
        return
    }

    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val pending by app.repository.observePending().collectAsState(initial = emptyList())
    val queueRequest by openQueueRequests.collectAsState()

    LaunchedEffect(queueRequest) {
        if (queueRequest > 0) nav.navigateTab(Routes.QUEUE)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val backStack by nav.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = current == tab.route || (current?.startsWith("${tab.route}/") == true)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.navigateTab(tab.route) },
                        modifier = Modifier.testTag(tab.tag),
                        label = { Text(tab.label, maxLines = 1) },
                        icon = {
                            if (tab.route == Routes.QUEUE && pending.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text(pending.size.toString()) } }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { HomeScreen(app) }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(app, snackbar, onOpen = { id -> nav.navigate(Routes.transactionEdit(id)) })
            }
            composable(Routes.TRANSACTION_EDIT) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: -1L
                TransactionEditScreen(app, id, snackbar, onClose = { nav.popBackStack() })
            }
            composable(Routes.QUEUE) { QueueScreen(app, snackbar) }
            composable(Routes.SUBSCRIPTIONS) { SubscriptionsScreen(app, onOpenTransaction = { id -> nav.navigate(Routes.transactionEdit(id)) }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(app, snackbar, onNavigate = { route -> nav.navigate(route) }, onPermissionsLost = { granted = false })
            }
            composable(Routes.SETTINGS_SENDERS) { SendersScreen(app, snackbar, onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS_UNPARSED) { UnparsedScreen(app, snackbar, onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS_CATEGORIES) { CategoriesScreen(app, snackbar, onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS_RULES) { RulesScreen(app, snackbar, onBack = { nav.popBackStack() }) }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
