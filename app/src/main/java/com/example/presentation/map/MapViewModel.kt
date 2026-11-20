package com.example.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Poi
import com.example.domain.repository.RouteRepository
import com.example.domain.usecase.AddPoiUseCase
import com.example.domain.usecase.DeletePoiUseCase
import com.example.domain.usecase.DeleteRouteUseCase
import com.example.domain.usecase.GetPoisByRouteIdUseCase
import com.example.domain.usecase.SaveRouteUseCase
import com.example.domain.util.LocationUtils
import com.example.service.TrackingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel @Inject constructor(
    private val saveRouteUseCase: SaveRouteUseCase,
    private val routeRepository: RouteRepository? = null,
    private val getPoisByRouteIdUseCase: GetPoisByRouteIdUseCase? = null,
    private val addPoiUseCase: AddPoiUseCase? = null,
    private val deletePoiUseCase: DeletePoiUseCase? = null,
    private val deleteRouteUseCase: DeleteRouteUseCase? = null
) : ViewModel() {

    private val _showSaveDialog = MutableStateFlow(false)

    // Observamos en tiempo real los POIs de la ruta activa mediante Flow de Room
    private val activePoisFlow: Flow<List<Poi>> = TrackingState.activeRouteId
        .flatMapLatest { routeId ->
            if (routeId != null && routeId > 0L && getPoisByRouteIdUseCase != null) {
                getPoisByRouteIdUseCase(routeId)
            } else {
                flowOf(emptyList())
            }
        }

    val state: StateFlow<MapState> = combine(
        TrackingState.isTracking,
        TrackingState.activeRouteId,
        TrackingState.routePoints,
        activePoisFlow,
        _showSaveDialog
    ) { isTracking, activeRouteId, points, pois, showDialog ->
        MapState(
            isTracking = isTracking,
            activeRouteId = activeRouteId,
            currentPath = points,
            activePois = pois,
            liveDistanceMeters = LocationUtils.calculateDistance(points),
            showSaveDialog = showDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MapState()
    )

    /**
     * Crea una ruta borrador en Room al iniciar la grabación para garantizar que cualquier POI
     * añadido durante el recorrido tenga un `activeRouteId` válido (integridad ForeignKey).
     */
    fun ensureActiveRouteSession() {
        if (TrackingState.activeRouteId.value != null) return
        val repo = routeRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (TrackingState.activeRouteId.value == null) {
                val draftId = repo.createDraftRoute("Recorrido en curso...")
                TrackingState.setActiveRouteId(draftId)
            }
        }
    }

    fun addPoiToActiveRoute(
        latitude: Double,
        longitude: Double,
        title: String,
        description: String,
        imageUri: String?
    ) {
        val addUseCase = addPoiUseCase ?: return
        val repo = routeRepository
        viewModelScope.launch(Dispatchers.IO) {
            var routeId = TrackingState.activeRouteId.value
            if (routeId == null && repo != null) {
                routeId = repo.createDraftRoute("Recorrido en curso...")
                TrackingState.setActiveRouteId(routeId)
            }
            if (routeId != null && routeId > 0L) {
                addUseCase(
                    Poi(
                        routeId = routeId,
                        latitude = latitude,
                        longitude = longitude,
                        title = title.trim(),
                        description = description.trim(),
                        imageUri = imageUri
                    )
                )
            }
        }
    }

    fun deletePoi(poiId: Long) {
        val deleteUseCase = deletePoiUseCase ?: return
        viewModelScope.launch(Dispatchers.IO) {
            deleteUseCase(poiId)
        }
    }

    fun onToggleSaveDialog(show: Boolean) {
        _showSaveDialog.value = show
    }

    fun saveCurrentRoute(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val points = TrackingState.routePoints.value
            val activeId = TrackingState.activeRouteId.value
            if (points.isNotEmpty() || (activeId != null && activeId > 0L)) {
                saveRouteUseCase(
                    name = name,
                    points = points,
                    existingRouteId = activeId
                )
                TrackingState.clearState()
                _showSaveDialog.value = false
            }
        }
    }

    fun discardCurrentRoute() {
        val activeId = TrackingState.activeRouteId.value
        TrackingState.clearState()
        _showSaveDialog.value = false
        if (activeId != null && activeId > 0L && deleteRouteUseCase != null) {
            viewModelScope.launch(Dispatchers.IO) {
                // onDelete = CASCADE elimina automáticamente los POIs de la ruta descartada
                deleteRouteUseCase(activeId)
            }
        }
    }
}
