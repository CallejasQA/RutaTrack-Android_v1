package com.example.service

import com.example.domain.model.RoutePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Objeto Singleton estático. Vivirá mientras la app esté viva en memoria.
object TrackingState {
    
    // ¿Estamos grabando actualmente?
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    // La lista de coordenadas capturadas en la sesión actual
    private val _routePoints = MutableStateFlow<List<RoutePoint>>(emptyList())
    val routePoints: StateFlow<List<RoutePoint>> = _routePoints.asStateFlow()

    fun setIsTracking(tracking: Boolean) {
        _isTracking.value = tracking
    }

    fun addPoint(point: RoutePoint) {
        // Añadimos el nuevo punto al final de la lista de forma inmutable
        _routePoints.update { currentList ->
            currentList + point
        }
    }

    // Se llamará cuando el usuario decida guardar la ruta y empezar una nueva
    fun clearState() {
        _isTracking.value = false
        _routePoints.value = emptyList()
    }
}
