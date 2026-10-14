package com.example.trackly.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.presentation.calendar.CalendarScreen
import com.example.trackly.presentation.habit.HabitDetailsScreen
import com.example.trackly.presentation.habit.HabitFormScreen
import com.example.trackly.presentation.habit.HabitHistoryScreen
import com.example.trackly.presentation.habit.ReorderScreen
import com.example.trackly.presentation.home.HomeScreen
import com.example.trackly.presentation.legal.LegalScreen
import com.example.trackly.presentation.onboarding.OnboardingScreen
import com.example.trackly.presentation.premium.PremiumScreen
import com.example.trackly.presentation.settings.LegalKind
import com.example.trackly.presentation.settings.SettingsScreen
import com.example.trackly.presentation.stats.StatsScreen
import com.example.trackly.presentation.stats.WeeklyOverviewScreen

private const val ANIMATION_MILLIS = 260

private val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(ANIMATION_MILLIS)) + slideInHorizontally(tween(ANIMATION_MILLIS)) { it / 10 }
}
private val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(ANIMATION_MILLIS / 2))
}
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(ANIMATION_MILLIS))
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(ANIMATION_MILLIS / 2)) + slideOutHorizontally(tween(ANIMATION_MILLIS)) { it / 10 }
}

@Composable
fun AppNavHost(
    startDestination: String,
    deepLinkHabitId: Long?,
    onDeepLinkConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TopLevelDestination.routes
    val currentOnConsumed by rememberUpdatedState(onDeepLinkConsumed)

    // Notification tap: Home is the base of the back stack, the habit opens on top of it.
    LaunchedEffect(deepLinkHabitId, backStackEntry != null) {
        if (deepLinkHabitId != null && backStackEntry != null) {
            if (currentRoute != Routes.ONBOARDING) {
                navController.navigate(Routes.habit(deepLinkHabitId)) { launchSingleTop = true }
            }
            currentOnConsumed()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateTopLevel(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                            modifier = Modifier.testTag("nav_${destination.route}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
            enterTransition = enter,
            exitTransition = exit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = { openCreate ->
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                        if (openCreate) navController.navigate(Routes.CREATE)
                    },
                )
            }
            composable(
                Routes.HOME,
                enterTransition = { fadeIn(tween(ANIMATION_MILLIS)) },
                exitTransition = { fadeOut(tween(ANIMATION_MILLIS / 2)) },
            ) {
                HomeScreen(
                    onOpenHabit = { navController.navigate(Routes.habit(it)) },
                    onAddHabit = { navController.navigate(Routes.CREATE) },
                    onUpgrade = { navController.navigate(Routes.premium(it)) },
                    onOpenWeekly = { navController.navigate(Routes.WEEKLY) },
                    onReorder = { navController.navigate(Routes.REORDER) },
                )
            }
            composable(Routes.CALENDAR, enterTransition = { fadeIn(tween(ANIMATION_MILLIS)) }) {
                CalendarScreen(
                    onAddHabit = { navController.navigate(Routes.CREATE) },
                    onOpenHabit = { navController.navigate(Routes.habit(it)) },
                    onUpgrade = { navController.navigate(Routes.premium(it)) },
                )
            }
            composable(Routes.STATS, enterTransition = { fadeIn(tween(ANIMATION_MILLIS)) }) {
                StatsScreen(
                    onAddHabit = { navController.navigate(Routes.CREATE) },
                    onOpenWeekly = { navController.navigate(Routes.WEEKLY) },
                    onUpgrade = { navController.navigate(Routes.premium(it)) },
                )
            }
            composable(Routes.SETTINGS, enterTransition = { fadeIn(tween(ANIMATION_MILLIS)) }) {
                SettingsScreen(
                    onOpenPremium = { navController.navigate(Routes.premium()) },
                    onOpenLegal = { navController.navigate(Routes.legal(it.name)) },
                    onUpgrade = { navController.navigate(Routes.premium(it)) },
                )
            }
            composable(Routes.CREATE) {
                HabitFormScreen(
                    habitId = 0L,
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onUpgrade = { navController.navigate(Routes.premium(it)) },
                )
            }
            composable(
                Routes.EDIT,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_ID) ?: 0L
                HabitFormScreen(
                    habitId = id,
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onUpgrade = { navController.navigate(Routes.premium(it)) },
                )
            }
            composable(
                Routes.HABIT,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_ID) ?: 0L
                HabitDetailsScreen(
                    habitId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.edit(it)) },
                    onHistory = { navController.navigate(Routes.history(it)) },
                    onUpgrade = { navController.navigate(Routes.premium(it)) },
                )
            }
            composable(
                Routes.HISTORY,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_ID) ?: 0L
                HabitHistoryScreen(
                    habitId = id,
                    onBack = { navController.popBackStack() },
                    onUpgrade = { navController.navigate(Routes.premium(it)) },
                )
            }
            composable(Routes.REORDER) { ReorderScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.WEEKLY) {
                WeeklyOverviewScreen(
                    onBack = { navController.popBackStack() },
                    onOpenHabit = { navController.navigate(Routes.habit(it)) },
                )
            }
            composable(
                Routes.PREMIUM,
                arguments = listOf(
                    navArgument(Routes.ARG_FEATURE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
                enterTransition = { slideInVertically(tween(ANIMATION_MILLIS + 60)) { it / 3 } + fadeIn(tween(ANIMATION_MILLIS)) },
                popExitTransition = { slideOutVertically(tween(ANIMATION_MILLIS)) { it / 3 } + fadeOut(tween(ANIMATION_MILLIS)) },
            ) { entry ->
                val feature = entry.arguments?.getString(Routes.ARG_FEATURE)
                    ?.let { name -> PremiumFeature.entries.firstOrNull { it.name == name } }
                PremiumScreen(
                    reason = feature,
                    onClose = { navController.popBackStack() },
                    onOpenLegal = { navController.navigate(Routes.legal(it.name)) },
                )
            }
            composable(
                Routes.LEGAL,
                arguments = listOf(navArgument(Routes.ARG_KIND) { type = NavType.StringType }),
            ) { entry ->
                val kind = entry.arguments?.getString(Routes.ARG_KIND)
                LegalScreen(
                    kind = if (kind == LegalKind.TERMS.name) LegalKind.TERMS else LegalKind.PRIVACY,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
