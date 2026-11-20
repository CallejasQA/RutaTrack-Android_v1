package com.example.service

import com.example.domain.model.RoutePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Canal de estado reactivo en RAM para la sesión de grabación GPS en curso.
 */
object TrackingState {

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _routePoints = MutableStateFlow<List<RoutePoint>>(emptyList())
    val routePoints: StateFlow<List<RoutePoint>> = _routePoints.asStateFlow()

    private val _startTimeMillis = MutableStateFlow(0L)
    val startTimeMillis: StateFlow<Long> = _startTimeMillis.asStateFlow()

    fun setIsTracking(tracking: Boolean) {
        _isTracking.value = tracking
        if (tracking && _startTimeMillis.value == 0L) {
            _startTimeMillis.value = System.currentTimeMillis()
        }
    }

    fun addPoint(point: RoutePoint) {
        _routePoints.update { currentList ->
            currentList + point
        }
    }

    fun restorePoints(points: List<RoutePoint>, startTime: Long) {
        if (points.isNotEmpty()) {
            _routePoints.value = points
            _startTimeMillis.value = startTime
        }
    }

    fun clearState() {
        _isTracking.value = false
        _routePoints.value = emptyList()
        _startTimeMillis.value = 0L
    }
}
