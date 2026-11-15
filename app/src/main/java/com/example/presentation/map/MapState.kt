package com.example.presentation.map

import com.example.domain.model.Poi
import com.example.domain.model.Route
import com.example.domain.model.RoutePoint

/**
 * Estado específico del modo "Seguir Huella" (Track Following / Ghost Route).
 *
 * @param referenceRoute Metadatos de la ruta histórica o importada cargada como referencia.
 * @param referencePoints Coordenadas de la huella a seguir.
 * @param referencePois Puntos de Interés pertenecientes a la ruta de referencia.
 * @param distanceFromTrackMeters Distancia mínima en metros entre la ubicación actual y la huella.
 * @param isCameraCenteredOnUser Controla si la cámara sigue automáticamente el GPS del usuario.
 */
data class FollowRouteMode(
    val referenceRoute: Route? = null,
    val referencePoints: List<RoutePoint> = emptyList(),
    val referencePois: List<Poi> = emptyList(),
    val distanceFromTrackMeters: Float? = null,
    val isCameraCenteredOnUser: Boolean = true
) {
    val isActive: Boolean
        get() = referenceRoute != null && referencePoints.isNotEmpty()
}

/**
 * Estado inmutable de la pantalla de Mapa bajo el patrón UDF (Unidirectional Data Flow).
 *
 * @param isTracking Indica si el Foreground Service está grabando activamente.
 * @param currentPath Lista acumulada de puntos de coordenada en el trayecto en vivo.
 * @param liveDistanceMeters Distancia calculada en tiempo real (m).
 * @param showSaveDialog Controla la visibilidad del diálogo de finalización de ruta.
 * @param followRouteMode Estado del modo "Seguir Huella", independiente de la grabación activa.
 */
data class MapState(
    val isTracking: Boolean = false,
    val currentPath: List<RoutePoint> = emptyList(),
    val liveDistanceMeters: Float = 0f,
    val showSaveDialog: Boolean = false,
    val followRouteMode: FollowRouteMode = FollowRouteMode()
)
