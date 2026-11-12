package com.example.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.BaseApplication
import com.example.presentation.detail.RouteDetailViewModel
import com.example.presentation.history.HistoryViewModel
import com.example.presentation.map.MapViewModel

/**
 * Fábrica centralizada de ViewModels para Compose.
 *
 * Conecta los Casos de Uso del AppContainer con los ViewModels respectivos
 * mediante inyección por constructor limpia y tipada.
 */
object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = this.application()
            MapViewModel(saveRouteUseCase = app.container.saveRouteUseCase)
        }
        initializer {
            val app = this.application()
            HistoryViewModel(
                getAllRoutesUseCase = app.container.getAllRoutesUseCase,
                deleteRouteUseCase = app.container.deleteRouteUseCase
            )
        }
    }

    /**
     * Provee una fábrica parametrizada con [routeId] para [RouteDetailViewModel].
     */
    fun provideRouteDetailViewModel(routeId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = this.application()
            RouteDetailViewModel(
                routeId = routeId,
                getRouteWithPointsUseCase = app.container.getRouteWithPointsUseCase,
                addPoiUseCase = app.container.addPoiUseCase,
                exportRouteToGpxUseCase = app.container.exportRouteToGpxUseCase
            )
        }
    }
}

/**
 * Función de extensión para recuperar la instancia tipada de [BaseApplication].
 */
fun CreationExtras.application(): BaseApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BaseApplication)
