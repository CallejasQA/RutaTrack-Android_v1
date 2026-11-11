package com.example.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Poi
import com.example.domain.model.RouteWithDetails
import com.example.domain.usecase.AddPoiUseCase
import com.example.domain.usecase.GetRouteWithPointsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RouteDetailViewModel(
    private val routeId: Long,
    getRouteWithPointsUseCase: GetRouteWithPointsUseCase,
    private val addPoiUseCase: AddPoiUseCase
) : ViewModel() {

    // Obtenemos la ruta, sus puntos y sus POIs en tiempo real
    val routeDetails: StateFlow<RouteWithDetails?> = getRouteWithPointsUseCase(routeId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

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
}
