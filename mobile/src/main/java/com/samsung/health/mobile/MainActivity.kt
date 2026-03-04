package com.samsung.health.mobile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Hub // Added for Causal Report
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemSecurityUpdateGood
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.samsung.health.mobile.ui.CausalReportScreen // Added
import com.samsung.health.mobile.ui.DataScreen
import com.samsung.health.mobile.ui.InsightsScreen
import com.samsung.health.mobile.ui.OverviewScreen
import com.samsung.health.mobile.ui.SleepAnalysisScreen
import com.samsung.health.mobile.ui.SystemScreen
import com.samsung.health.mobile.util.PerformanceMonitor
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PerformanceMonitor.start(applicationContext)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainAppScaffold(viewModel)
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Vitals : Screen("vitals", "Vitals", Icons.Rounded.MonitorHeart)
    object Insights : Screen("insights", "Insights", Icons.Rounded.Analytics)
    object Causal : Screen("causal", "Causal AI", Icons.Rounded.Hub) // Added New Tab
    object Data : Screen("data", "Storage", Icons.Rounded.Storage)
    object System : Screen("system", "System", Icons.Rounded.SystemSecurityUpdateGood)
    object SleepAnalysis : Screen("sleep_analysis", "Deep Sleep", Icons.Rounded.Bedtime)
}

@Composable
fun MainAppScaffold(viewModel: MainViewModel) {
    val navController = rememberNavController()
    // Added Causal to main bottom navigation tabs
    val tabs = listOf(Screen.Vitals, Screen.Insights, Screen.Causal, Screen.Data, Screen.System)
    var selectedScreen by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val exportStatus by viewModel.exportStatus.collectAsState()

    LaunchedEffect(exportStatus) {
        exportStatus?.let {
            if (it != "Exporting...") Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            if (currentRoute != Screen.SleepAnalysis.route) {
                NavigationBar {
                    tabs.forEachIndexed { index, screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = selectedScreen == index,
                            onClick = {
                                selectedScreen = index
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val healthData by viewModel.healthData.collectAsState()

        NavHost(
            navController = navController,
            startDestination = Screen.Vitals.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Vitals.route) {
                OverviewScreen(data = healthData)
            }
            composable(Screen.Insights.route) {
                InsightsScreen(
                    data = healthData,
                    onDeepSleepClick = { navController.navigate(Screen.SleepAnalysis.route) }
                )
            }
            composable(Screen.Causal.route) {
                CausalReportScreen(data = healthData)
            }
            composable(Screen.Data.route) {
                DataScreen(viewModel)
            }
            composable(Screen.System.route) {
                SystemScreen()
            }
            composable(Screen.SleepAnalysis.route) {
                SleepAnalysisScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}