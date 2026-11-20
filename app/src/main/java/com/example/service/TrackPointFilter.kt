package com.example.service

import android.location.Location

/**
 * Filtro inteligente de movimiento (Anti-GPS Drift) para evitar acumular puntos
 * estacionarios y zigzags causados por rebote de señal satelital cuando el usuario
 * se detiene a descansar.
 */
class TrackPointFilter(
    private val maxAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_METERS,
    private val minMoveDistanceMeters: Float = DEFAULT_MIN_MOVE_DISTANCE_METERS,
    private val lowSpeedThresholdMetersPerSec: Float = DEFAULT_LOW_SPEED_MPS,
    private val lowSpeedMinDistanceMeters: Float = DEFAULT_LOW_SPEED_MIN_DISTANCE_METERS
) {

    private var lastSavedLocation: Location? = null

    /**
     * Evalúa si una lectura GPS entrante representa desplazamiento real y calidad suficiente.
     * Solo cuando retorna `true` se actualiza [lastSavedLocation] y se debe persistir el punto.
     *
     * No existe heartbeat por tiempo: si el usuario permanece estacionario durante 10 o 30 minutos,
     * ningún punto nuevo será aceptado hasta que supere el umbral de desplazamiento real.
     */
    @Synchronized
    fun shouldAccept(newLocation: Location): Boolean {
        // 1. Filtro de Calidad de Señal (Accuracy)
        if (!newLocation.hasAccuracy() || newLocation.accuracy > maxAccuracyMeters) {
            return false
        }

        val previous = lastSavedLocation
        // 2. Primer Punto de la Ruta: siempre se guarda para fijar el inicio exacto
        if (previous == null) {
            lastSavedLocation = Location(newLocation)
            return true
        }

        // 3. Regla Anti-Deriva Estacionaria (comparación contra el último punto GUARDADO)
        val distanceMeters = previous.distanceTo(newLocation)

        // Descarta cualquier movimiento menor a 5.0 metros
        if (distanceMeters < minMoveDistanceMeters) {
            return false
        }

        // Si reporta velocidad muy baja (< 0.4 m/s ≈ 1.4 km/h), exige un umbral mayor (10.0 m)
        // para filtrar rebotes de señal en reposo sin crear falsos desplazamientos
        val isStationaryOrNearZeroSpeed =
            newLocation.hasSpeed() && newLocation.speed < lowSpeedThresholdMetersPerSec
        if (isStationaryOrNearZeroSpeed && distanceMeters < lowSpeedMinDistanceMeters) {
            return false
        }

        // 4. Desplazamiento real verificado -> actualizamos referencia del último punto guardado
        lastSavedLocation = Location(newLocation)
        return true
    }

    /**
     * Reinicia el estado interno del filtro al iniciar o finalizar un recorrido.
     */
    @Synchronized
    fun reset() {
        lastSavedLocation = null
    }

    /**
     * Restaura el último punto conocido en caso de recuperación tras reinicio START_STICKY.
     */
    @Synchronized
    fun seedLastSavedPoint(latitude: Double, longitude: Double) {
        lastSavedLocation = Location("restored_draft").apply {
            this.latitude = latitude
            this.longitude = longitude
            this.accuracy = 1f
        }
    }

    companion object {
        const val DEFAULT_MAX_ACCURACY_METERS = 25f
        const val DEFAULT_MIN_MOVE_DISTANCE_METERS = 5.0f
        const val DEFAULT_LOW_SPEED_MPS = 0.4f // ~1.44 km/h
        const val DEFAULT_LOW_SPEED_MIN_DISTANCE_METERS = 10.0f
    }
}
