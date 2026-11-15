package com.example.presentation.map

import com.example.domain.model.RoutePoint

/**
 * Estado inmutable de la pantalla de Mapa bajo el patrón UDF (Unidirectional Data Flow).
 *
 * @param isTracking Indica si el Foreground Service está grabando activamente.
 * @param currentPath Lista acumulada de puntos de coordenada en el trayecto en vivo.
 * @param liveDistanceMeters Distancia calculada en tiempo real (m).
 * @param showSaveDialog Controla la visibilidad del diálogo de finalización de ruta.
 */
data class MapState(
    val isTracking: Boolean = false,
    val currentPath: List<RoutePoint> = emptyList(),
    val liveDistanceMeters: Float = 0f,
    val showSaveDialog: Boolean = false
)
