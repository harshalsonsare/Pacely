package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.TrackScreen
import com.example.ui.screens.WorkoutDetailScreen
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.ElectricOrange
import com.example.ui.theme.ElectricOrangeContainer
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PacelyTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.StatsViewModel
import com.example.ui.viewmodel.TrackingViewModel
import kotlinx.coroutines.launch

object PacelyRoutes {
    const val TRACK = "track"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val WORKOUT_DETAIL = "workout_detail/{workoutId}"

    fun workoutDetail(id: Long): String = "workout_detail/$id"
}

private data class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopDestination(
        route = PacelyRoutes.TRACK,
        label = "Track",
        selectedIcon = Icons.Rounded.PlayCircle,
        unselectedIcon = Icons.Outlined.PlayCircle,
        testTag = "nav_track"
    ),
    TopDestination(
        route = PacelyRoutes.HISTORY,
        label = "History",
        selectedIcon = Icons.Rounded.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_history"
    ),
    TopDestination(
        route = PacelyRoutes.STATS,
        label = "Stats",
        selectedIcon = Icons.Rounded.EmojiEvents,
        unselectedIcon = Icons.Outlined.EmojiEvents,
        testTag = "nav_stats"
    ),
    TopDestination(
        route = PacelyRoutes.SETTINGS,
        label = "Settings",
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(ObsidianBg.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(CarbonSurface.toArgb())
        )
        setContent {
            PacelyTheme {
                PacelyAppRoot()
            }
        }
    }
}

@Composable
fun PacelyAppRoot() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val trackingViewModel: TrackingViewModel = viewModel(factory = TrackingViewModel.Factory(context))
    val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(context))
    val statsViewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory(context))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(context))

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showTopLevelNav = currentRoute in topDestinations.map { it.route }

    val showMessage: (String) -> Unit = { msg ->
        coroutineScope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navigateTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.statusBars,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail && showTopLevelNav) {
                    NavigationBar(
                        containerColor = CarbonSurface,
                        contentColor = TextPrimary,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricOrange,
                                    selectedTextColor = ElectricOrange,
                                    indicatorColor = ElectricOrangeContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail && showTopLevelNav) {
                    NavigationRail(
                        containerColor = CarbonSurface,
                        contentColor = TextPrimary
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricOrange,
                                    selectedTextColor = ElectricOrange,
                                    indicatorColor = ElectricOrangeContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = PacelyRoutes.TRACK,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(PacelyRoutes.TRACK) {
                        TrackScreen(
                            viewModel = trackingViewModel,
                            onWorkoutSaved = { savedId ->
                                showMessage("Workout saved offline.")
                                navController.navigate(PacelyRoutes.workoutDetail(savedId))
                            }
                        )
                    }
                    composable(PacelyRoutes.HISTORY) {
                        HistoryScreen(
                            viewModel = historyViewModel,
                            onWorkoutClick = { workoutId ->
                                navController.navigate(PacelyRoutes.workoutDetail(workoutId))
                            },
                            onStartTrackClick = { navigateTopLevel(PacelyRoutes.TRACK) },
                            onShowMessage = showMessage
                        )
                    }
                    composable(PacelyRoutes.STATS) {
                        StatsScreen(
                            viewModel = statsViewModel,
                            onWorkoutClick = { workoutId ->
                                navController.navigate(PacelyRoutes.workoutDetail(workoutId))
                            }
                        )
                    }
                    composable(PacelyRoutes.SETTINGS) {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onShowMessage = showMessage
                        )
                    }
                    composable(
                        route = PacelyRoutes.WORKOUT_DETAIL,
                        arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: -1L
                        WorkoutDetailScreen(
                            workoutId = workoutId,
                            viewModel = historyViewModel,
                            onBack = { navController.popBackStack() },
                            onShowMessage = showMessage
                        )
                    }
                }
            }
        }
    }
}
