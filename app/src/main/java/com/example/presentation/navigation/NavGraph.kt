package com.example.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.presentation.detail.RouteDetailScreen
import com.example.presentation.history.HistoryScreen
import com.example.presentation.map.MapScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Map.createRoute()
    ) {
        composable(
            route = Screen.Map.route,
            arguments = listOf(
                navArgument("followRouteId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val rawRouteId = backStackEntry.arguments?.getLong("followRouteId") ?: -1L
            val followRouteId = rawRouteId.takeIf { it > 0L }

            MapScreen(
                followRouteId = followRouteId,
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                }
            )
        }

        composable(route = Screen.History.route) {
            HistoryScreen(navController = navController)
        }

        composable(
            route = Screen.RouteDetail.route,
            arguments = listOf(navArgument("routeId") { type = NavType.LongType })
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getLong("routeId") ?: 0L
            RouteDetailScreen(
                routeId = routeId,
                navController = navController
            )
        }
    }
}
