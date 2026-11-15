package com.example.presentation.navigation

sealed class Screen(val route: String) {
    object Map : Screen("map_screen?followRouteId={followRouteId}") {
        fun createRoute(followRouteId: Long? = null): String {
            return if (followRouteId != null && followRouteId > 0L) {
                "map_screen?followRouteId=$followRouteId"
            } else {
                "map_screen"
            }
        }
    }

    object History : Screen("history_screen")

    object RouteDetail : Screen("route_detail_screen/{routeId}") {
        fun createRoute(routeId: Long) = "route_detail_screen/$routeId"
    }
}
