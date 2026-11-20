package com.example.presentation.map

import com.example.domain.model.Poi
import com.example.domain.model.RoutePoint

/**
 * Estado inmutable de la pantalla de Mapa bajo el patrón UDF (Unidirectional Data Flow).
 */
data class MapState(
    val isTracking: Boolean = false,
    val activeRouteId: Long? = null,
    val currentPath: List<RoutePoint> = emptyList(),
    val activePois: List<Poi> = emptyList(),
    val liveDistanceMeters: Float = 0f,
    val showSaveDialog: Boolean = false
)
