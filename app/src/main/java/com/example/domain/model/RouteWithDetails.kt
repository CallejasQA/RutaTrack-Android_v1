package com.example.domain.model

/**
 * Agregado que combina una [Route] junto con su lista ordenada de coordenadas [points]
 * y sus puntos de interés multimedia [pois].
 *
 * Utilizado por la UI de inspección y por los Casos de Uso para renderizar la polilínea completa
 * y los marcadores en el mapa de Osmdroid.
 */
data class RouteWithDetails(
    val route: Route,
    val points: List<RoutePoint>,
    val pois: List<Poi>
)
