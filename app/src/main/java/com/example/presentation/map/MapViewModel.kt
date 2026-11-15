package com.example.presentation.map

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.RoutePoint
import com.example.domain.model.RouteWithDetails
import com.example.domain.usecase.GetRouteWithPointsUseCase
import com.example.domain.usecase.SaveRouteUseCase
import com.example.domain.util.LocationUtils
import com.example.service.TrackingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

class MapViewModel @Inject constructor(
    private val saveRouteUseCase: SaveRouteUseCase,
    private val getRouteWithPointsUseCase: GetRouteWithPointsUseCase
) : ViewModel() {

    // Estado local para mostrar u ocultar el diálogo de guardar ruta
    private val _showSaveDialog = MutableStateFlow(false)

    // Estado de ruta de referencia para el modo "Seguir Huella"
    private val _referenceDetails = MutableStateFlow<RouteWithDetails?>(null)
    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    private val _isCameraCenteredOnUser = MutableStateFlow(true)
    private var followRouteJob: Job? = null

    // Fusionamos los flujos del Foreground Service con el estado de seguimiento de huella y UI
    val state: StateFlow<MapState> = combine(
        TrackingState.isTracking,
        TrackingState.routePoints,
        _showSaveDialog,
        _referenceDetails,
        _userLocation,
        _isCameraCenteredOnUser
    ) { values ->
        val isTracking = values[0] as Boolean
        @Suppress("UNCHECKED_CAST")
        val points = values[1] as List<RoutePoint>
        val showDialog = values[2] as Boolean
        val reference = values[3] as RouteWithDetails?
        @Suppress("UNCHECKED_CAST")
        val userLoc = values[4] as Pair<Double, Double>?
        val isCentered = values[5] as Boolean

        val followMode = if (reference != null) {
            val minDistance = userLoc?.let { (lat, lon) ->
                calculateMinDistanceToTrackMeters(lat, lon, reference.points)
            }
            FollowRouteMode(
                referenceRoute = reference.route,
                referencePoints = reference.points,
                referencePois = reference.pois,
                distanceFromTrackMeters = minDistance,
                isCameraCenteredOnUser = isCentered
            )
        } else {
            FollowRouteMode(isCameraCenteredOnUser = isCentered)
        }

        MapState(
            isTracking = isTracking,
            currentPath = points,
            liveDistanceMeters = LocationUtils.calculateDistance(points),
            showSaveDialog = showDialog,
            followRouteMode = followMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MapState()
    )

    // --- EVENTOS DE GRABACIÓN (UDF Actions) ---

    fun onToggleSaveDialog(show: Boolean) {
        _showSaveDialog.value = show
    }

    fun saveCurrentRoute(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val points = TrackingState.routePoints.value
            if (points.isNotEmpty()) {
                saveRouteUseCase(name = name, points = points)
                TrackingState.clearState()
                _showSaveDialog.value = false
            }
        }
    }

    fun discardCurrentRoute() {
        TrackingState.clearState()
        _showSaveDialog.value = false
    }

    // --- EVENTOS DE MODO "SEGUIR HUELLA" ---

    fun loadReferenceRoute(routeId: Long) {
        if (routeId <= 0L) return
        if (_referenceDetails.value?.route?.id == routeId) return

        followRouteJob?.cancel()
        followRouteJob = viewModelScope.launch {
            getRouteWithPointsUseCase(routeId)
                .catch {
                    _referenceDetails.value = null
                }
                .collect { details ->
                    _referenceDetails.value = details
                }
        }
    }

    fun updateUserLocation(latitude: Double, longitude: Double) {
        _userLocation.value = latitude to longitude
    }

    fun setCameraCenteredOnUser(centered: Boolean) {
        _isCameraCenteredOnUser.value = centered
    }

    fun toggleCameraCenteredOnUser() {
        _isCameraCenteredOnUser.value = !_isCameraCenteredOnUser.value
    }

    fun exitFollowRouteMode() {
        followRouteJob?.cancel()
        followRouteJob = null
        _referenceDetails.value = null
    }

    companion object {
        /**
         * Calcula de forma 100% offline la distancia mínima (en metros) desde la ubicación
         * del usuario hasta el punto más cercano de la huella usando [Location.distanceBetween].
         */
        fun calculateMinDistanceToTrackMeters(
            userLat: Double,
            userLon: Double,
            referencePoints: List<RoutePoint>
        ): Float? {
            if (referencePoints.isEmpty()) return null
            val result = FloatArray(1)
            var minDistance = Float.MAX_VALUE

            for (point in referencePoints) {
                Location.distanceBetween(
                    userLat,
                    userLon,
                    point.latitude,
                    point.longitude,
                    result
                )
                if (result[0] < minDistance) {
                    minDistance = result[0]
                }
            }
            return if (minDistance == Float.MAX_VALUE) null else minDistance
        }
    }
}
