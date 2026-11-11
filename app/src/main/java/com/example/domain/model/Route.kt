package com.example.domain.model

/**
 * Representa una ruta grabada por el usuario.
 *
 * Modelo puro de dominio, desacoplado de la base de datos (Room) y de la UI (Compose).
 *
 * @param id Identificador único de la ruta.
 * @param name Nombre personalizado asignado por el usuario (ej. "Caminata al Mirador").
 * @param startTime Marca de tiempo (epoch millis) cuando inició la grabación.
 * @param endTime Marca de tiempo (epoch millis) cuando se detuvo la grabación.
 * @param totalDistanceMeters Distancia total acumulada en metros.
 * @param durationMillis Duración neta del recorrido en milisegundos.
 * @param averageSpeedKmh Velocidad promedio calculada en km/h.
 */
data class Route(
    val id: Long = 0, // 0 indica que aún no se ha guardado en la base de datos
    val name: String,
    val timestamp: Long, // Cuándo se inició el recorrido
    val distanceMeters: Float,
    val durationMillis: Long
)
