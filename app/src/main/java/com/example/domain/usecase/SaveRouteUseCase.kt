package com.example.domain.usecase

import com.example.domain.model.Route
import com.example.domain.model.RoutePoint
import com.example.domain.repository.RouteRepository
import com.example.domain.util.LocationUtils

class SaveRouteUseCase(
    private val repository: RouteRepository
) {
    suspend operator fun invoke(name: String, points: List<RoutePoint>): Long {
        if (points.isEmpty()) return -1L

        val startTime = points.first().timestamp
        val endTime = points.last().timestamp
        val duration = endTime - startTime
        val distance = LocationUtils.calculateDistance(points)

        val route = Route(
            name = name,
            timestamp = startTime,
            distanceMeters = distance,
            durationMillis = duration
        )

        return repository.saveRoute(route, points)
    }
}
