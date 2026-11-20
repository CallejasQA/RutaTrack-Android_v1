package com.example.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.BaseApplication
import com.example.presentation.detail.RouteDetailViewModel
import com.example.presentation.history.HistoryViewModel
import com.example.presentation.map.MapViewModel

object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = this.application()
            MapViewModel(
                saveRouteUseCase = app.container.saveRouteUseCase,
                routeRepository = app.container.routeRepository,
                getPoisByRouteIdUseCase = app.container.getPoisByRouteIdUseCase,
                addPoiUseCase = app.container.addPoiUseCase,
                deletePoiUseCase = app.container.deletePoiUseCase,
                deleteRouteUseCase = app.container.deleteRouteUseCase
            )
        }
        initializer {
            val app = this.application()
            HistoryViewModel(
                getAllRoutesUseCase = app.container.getAllRoutesUseCase,
                deleteRouteUseCase = app.container.deleteRouteUseCase,
                importRouteUseCase = app.container.importRouteUseCase
            )
        }
    }

    fun provideRouteDetailViewModel(routeId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = this.application()
            RouteDetailViewModel(
                routeId = routeId,
                getRouteWithPointsUseCase = app.container.getRouteWithPointsUseCase,
                addPoiUseCase = app.container.addPoiUseCase,
                deletePoiUseCase = app.container.deletePoiUseCase,
                exportRouteToGpxUseCase = app.container.exportRouteToGpxUseCase
            )
        }
    }
}

fun CreationExtras.application(): BaseApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BaseApplication)
