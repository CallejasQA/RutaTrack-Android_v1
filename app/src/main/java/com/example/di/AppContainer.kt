package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.local.dao.RouteDao
import com.example.data.local.database.AppDatabase
import com.example.data.repository.RouteRepositoryImpl
import com.example.domain.repository.RouteRepository
import com.example.domain.usecase.AddPoiUseCase
import com.example.domain.usecase.DeleteRouteUseCase
import com.example.domain.usecase.GetAllRoutesUseCase
import com.example.domain.usecase.GetRouteWithPointsUseCase
import com.example.domain.usecase.SaveRouteUseCase

/**
 * Contenedor de Inyección de Dependencias (DI) a nivel de aplicación.
 *
 * Provee instancias únicas (Singletons) para la base de datos, el repositorio
 * y los Casos de Uso del Dominio.
 */
interface AppContainer {
    val routeRepository: RouteRepository
    val saveRouteUseCase: SaveRouteUseCase
    val getAllRoutesUseCase: GetAllRoutesUseCase
    val getRouteWithPointsUseCase: GetRouteWithPointsUseCase
    val addPoiUseCase: AddPoiUseCase
    val deleteRouteUseCase: DeleteRouteUseCase
}

/**
 * Implementación concreta del contenedor de DI.
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "offline_map_tracker_db"
        ).build()
    }

    private val routeDao: RouteDao by lazy {
        database.routeDao()
    }

    override val routeRepository: RouteRepository by lazy {
        RouteRepositoryImpl(routeDao)
    }

    override val saveRouteUseCase: SaveRouteUseCase by lazy {
        SaveRouteUseCase(routeRepository)
    }

    override val getAllRoutesUseCase: GetAllRoutesUseCase by lazy {
        GetAllRoutesUseCase(routeRepository)
    }

    override val getRouteWithPointsUseCase: GetRouteWithPointsUseCase by lazy {
        GetRouteWithPointsUseCase(routeRepository)
    }

    override val addPoiUseCase: AddPoiUseCase by lazy {
        AddPoiUseCase(routeRepository)
    }

    override val deleteRouteUseCase: DeleteRouteUseCase by lazy {
        DeleteRouteUseCase(routeRepository)
    }
}
