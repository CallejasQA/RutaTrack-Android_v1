package com.example.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.usecase.SaveRouteUseCase
import com.example.domain.util.LocationUtils
import com.example.service.TrackingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(
    private val saveRouteUseCase: SaveRouteUseCase
) : ViewModel() {

    // Estado local para mostrar u ocultar el diálogo de guardar ruta
    private val _showSaveDialog = MutableStateFlow(false)

    // Fusionamos los flujos del Singleton (Servicio) con el estado del diálogo
    val state: StateFlow<MapState> = combine(
        TrackingState.isTracking,
        TrackingState.routePoints,
        _showSaveDialog
    ) { isTracking, points, showDialog ->
        MapState(
            isTracking = isTracking,
            currentPath = points,
            liveDistanceMeters = LocationUtils.calculateDistance(points),
            showSaveDialog = showDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000), // Mantiene el estado activo al rotar pantalla
        initialValue = MapState()
    )

    // --- EVENTOS (UDF Actions) ---

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
}
