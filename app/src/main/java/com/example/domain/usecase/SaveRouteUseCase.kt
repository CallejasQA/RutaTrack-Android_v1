package com.example.domain.usecase

import com.example.domain.model.Route
import com.example.domain.model.RoutePoint
import com.example.domain.repository.RouteRepository
import com.example.domain.util.LocationUtils
import javax.inject.Inject

class SaveRouteUseCase @Inject constructor(
    private val repository: RouteRepository
) {
    suspend operator fun invoke(
        name: String,
        points: List<RoutePoint>,
        existingRouteId: Long? = null
    ): Long {
        if (points.isEmpty() && (existingRouteId == null || existingRouteId <= 0L)) {
            return -1L
        }

        val startTime = points.firstOrNull()?.timestamp ?: System.currentTimeMillis()
        val endTime = points.lastOrNull()?.timestamp ?: startTime
        val duration = (endTime - startTime).coerceAtLeast(0L)
        val distance = LocationUtils.calculateDistance(points)

        val route = Route(
            id = existingRouteId ?: 0L,
            name = name,
            timestamp = startTime,
            distanceMeters = distance,
            durationMillis = duration
        )

        return if (existingRouteId != null && existingRouteId > 0L) {
            repository.finalizeRoute(route, points)
            existingRouteId
        } else {
            repository.saveRoute(route, points)
        }
    }
}
