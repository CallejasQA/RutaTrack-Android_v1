package com.example.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Poi
import com.example.domain.model.RouteWithDetails
import com.example.domain.usecase.AddPoiUseCase
import com.example.domain.usecase.ExportRouteToGpxUseCase
import com.example.domain.usecase.GetRouteWithPointsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.OutputStream
import javax.inject.Inject

class RouteDetailViewModel @Inject constructor(
    private val routeId: Long,
    getRouteWithPointsUseCase: GetRouteWithPointsUseCase,
    private val addPoiUseCase: AddPoiUseCase,
    private val exportRouteToGpxUseCase: ExportRouteToGpxUseCase
) : ViewModel() {

    // Obtenemos la ruta, sus puntos y sus POIs en tiempo real
    val routeDetails: StateFlow<RouteWithDetails?> = getRouteWithPointsUseCase(routeId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Estado para notificar a la UI del resultado de la exportación (Snackbar / Toast)
    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    fun addPoi(latitude: Double, longitude: Double, title: String, description: String, imageUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val poi = Poi(
                routeId = routeId,
                latitude = latitude,
                longitude = longitude,
                title = title,
                description = description,
                imageUri = imageUri
            )
            addPoiUseCase(poi)
        }
    }

    /**
     * Orquesta la exportación de la ruta a formato GPX escribiendo en el flujo del archivo SAF.
     */
    fun exportRouteToGpx(outputStream: OutputStream) {
        val currentRoute = routeDetails.value
        if (currentRoute == null) {
            _exportMessage.value = "Error: Datos de la ruta aún no cargados"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val result = exportRouteToGpxUseCase(currentRoute, outputStream)
            _exportMessage.value = if (result.isSuccess) {
                "Ruta '${currentRoute.route.name}' exportada con éxito como GPX"
            } else {
                "Error al exportar GPX: ${result.exceptionOrNull()?.localizedMessage ?: "Error desconocido"}"
            }
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }
}
