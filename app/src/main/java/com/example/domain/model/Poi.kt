package com.example.domain.model

/**
 * Representa un Punto de Interés (POI) multimedia creado por el usuario durante un recorrido.
 *
 * @param id Identificador único del POI.
 * @param routeId Identificador de la ruta a la que está asociado el POI.
 * @param title Título o nombre descriptivo del punto (ej. "Cascada de agua dulce").
 * @param description Detalles o notas adicionales sobre el lugar.
 * @param latitude Latitud en grados decimales.
 * @param longitude Longitud en grados decimales.
 * @param imageUri Ruta URI local ('content://...' o 'file://...') de la foto tomada o seleccionada.
 *                 No se guarda el archivo binario en la base de datos, únicamente la URI local.
 * @param timestamp Marca de tiempo en la que se creó el POI.
 */
data class Poi(
    val id: Long = 0,
    val routeId: Long,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val description: String,
    val imageUri: String? // Nullable por si el usuario decide no tomar una foto
)
