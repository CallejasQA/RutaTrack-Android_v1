package com.example.domain.repository

import com.example.domain.model.ImportedRouteSummary
import com.example.domain.model.Poi
import com.example.domain.model.Route
import com.example.domain.model.RoutePoint
import com.example.domain.model.RouteWithDetails
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

interface RouteRepository {
    // Lectura reactiva (Flow)
    fun getAllRoutes(): Flow<List<Route>>
    fun getRouteWithDetails(routeId: Long): Flow<RouteWithDetails>
    fun getPoisByRouteId(routeId: Long): Flow<List<Poi>>

    // Escritura (Suspend functions porque bloquean, deben ir en corrutinas)
    suspend fun createDraftRoute(name: String): Long
    suspend fun finalizeRoute(route: Route, points: List<RoutePoint>)
    suspend fun saveRoute(route: Route, points: List<RoutePoint>): Long
    suspend fun addPoi(poi: Poi): Long
    suspend fun deletePoiById(poiId: Long)
    suspend fun deleteRoute(routeId: Long)

    // Importación atómica desde GPX / KML
    suspend fun importRoute(inputStream: InputStream, fallbackName: String): Result<ImportedRouteSummary>
}
