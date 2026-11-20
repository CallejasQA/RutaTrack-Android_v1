package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.importer.RouteFileImporter
import com.example.data.local.dao.PoiDao
import com.example.data.local.dao.RouteDao
import com.example.data.local.database.AppDatabase
import com.example.data.repository.RouteRepositoryImpl
import com.example.domain.repository.RouteRepository
import com.example.domain.usecase.AddPoiUseCase
import com.example.domain.usecase.DeletePoiUseCase
import com.example.domain.usecase.DeleteRouteUseCase
import com.example.domain.usecase.ExportRouteToGpxUseCase
import com.example.domain.usecase.GetAllRoutesUseCase
import com.example.domain.usecase.GetPoisByRouteIdUseCase
import com.example.domain.usecase.GetRouteWithPointsUseCase
import com.example.domain.usecase.ImportRouteUseCase
import com.example.domain.usecase.SaveRouteUseCase

interface AppContainer {
    val routeRepository: RouteRepository
    val saveRouteUseCase: SaveRouteUseCase
    val getAllRoutesUseCase: GetAllRoutesUseCase
    val getRouteWithPointsUseCase: GetRouteWithPointsUseCase
    val addPoiUseCase: AddPoiUseCase
    val getPoisByRouteIdUseCase: GetPoisByRouteIdUseCase
    val deletePoiUseCase: DeletePoiUseCase
    val deleteRouteUseCase: DeleteRouteUseCase
    val exportRouteToGpxUseCase: ExportRouteToGpxUseCase
    val importRouteUseCase: ImportRouteUseCase
}

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

    private val poiDao: PoiDao by lazy {
        database.poiDao()
    }

    private val routeFileImporter: RouteFileImporter by lazy {
        RouteFileImporter()
    }

    override val routeRepository: RouteRepository by lazy {
        RouteRepositoryImpl(routeDao, poiDao, routeFileImporter)
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

    override val getPoisByRouteIdUseCase: GetPoisByRouteIdUseCase by lazy {
        GetPoisByRouteIdUseCase(routeRepository)
    }

    override val deletePoiUseCase: DeletePoiUseCase by lazy {
        DeletePoiUseCase(routeRepository)
    }

    override val deleteRouteUseCase: DeleteRouteUseCase by lazy {
        DeleteRouteUseCase(routeRepository)
    }

    override val exportRouteToGpxUseCase: ExportRouteToGpxUseCase by lazy {
        ExportRouteToGpxUseCase()
    }

    override val importRouteUseCase: ImportRouteUseCase by lazy {
        ImportRouteUseCase(routeRepository)
    }
}
