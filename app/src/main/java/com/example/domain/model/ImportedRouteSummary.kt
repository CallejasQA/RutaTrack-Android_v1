package com.example.domain.model

/**
 * Resumen de metadatos resultante tras importar exitosamente una ruta desde GPX o KML.
 */
data class ImportedRouteSummary(
    val routeId: Long,
    val routeName: String,
    val distanceMeters: Float,
    val elevationGainMeters: Double,
    val pointsCount: Int,
    val poisCount: Int
)
