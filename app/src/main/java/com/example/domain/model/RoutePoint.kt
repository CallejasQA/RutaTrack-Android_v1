package com.example.domain.model

/**
 * Representa un punto individual de coordenada geográfica capturado por el GPS durante el tracking.
 *
 * @param id Identificador único del punto.
 * @param routeId Identificador de la ruta a la que pertenece este punto.
 * @param latitude Latitud en grados decimales (WGS84).
 * @param longitude Longitud en grados decimales (WGS84).
 * @param altitude Altitud en metros sobre el nivel del mar (0.0 si no está disponible).
 * @param timestamp Marca de tiempo en milisegundos en la que se tomó la muestra.
 * @param speed Velocidad instantánea registrada por el sensor GPS en m/s.
 */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val timestamp: Long
)
