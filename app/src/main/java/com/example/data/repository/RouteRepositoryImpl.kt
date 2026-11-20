package com.example.data.repository

import com.example.data.importer.RouteFileImporter
import com.example.data.local.dao.PoiDao
import com.example.data.local.dao.RouteDao
import com.example.data.local.entity.RouteEntity
import com.example.data.local.mapper.toDomain
import com.example.data.local.mapper.toEntity
import com.example.domain.model.ImportedRouteSummary
import com.example.domain.model.Poi
import com.example.domain.model.Route
import com.example.domain.model.RoutePoint
import com.example.domain.model.RouteWithDetails
import com.example.domain.repository.RouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.InputStream
import javax.inject.Inject

class RouteRepositoryImpl @Inject constructor(
    private val dao: RouteDao,
    private val poiDao: PoiDao,
    private val importer: RouteFileImporter = RouteFileImporter()
) : RouteRepository {

    override fun getAllRoutes(): Flow<List<Route>> {
        return dao.getAllRoutes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRouteWithDetails(routeId: Long): Flow<RouteWithDetails> {
        return combine(
            dao.getRouteById(routeId),
            dao.getRoutePoints(routeId),
            poiDao.getPoisByRouteId(routeId)
        ) { routeEntity, pointsEntities, poisEntities ->
            RouteWithDetails(
                route = routeEntity.toDomain(),
                points = pointsEntities.map { it.toDomain() },
                pois = poisEntities.map { it.toDomain() }
            )
        }
    }

    override fun getPoisByRouteId(routeId: Long): Flow<List<Poi>> {
        return poiDao.getPoisByRouteId(routeId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun createDraftRoute(name: String): Long {
        val draft = RouteEntity(
            name = name,
            timestamp = System.currentTimeMillis(),
            distanceMeters = 0f,
            durationMillis = 0L
        )
        return dao.insertRoute(draft)
    }

    override suspend fun finalizeRoute(route: Route, points: List<RoutePoint>) {
        val pointEntities = points.map { it.toEntity(routeId = route.id) }
        dao.finalizeRouteWithPoints(route.toEntity(), pointEntities)
    }

    override suspend fun saveRoute(route: Route, points: List<RoutePoint>): Long {
        val insertedRouteId = dao.insertRoute(route.toEntity())
        val pointEntities = points.map { it.toEntity(routeId = insertedRouteId) }
        dao.insertRoutePoints(pointEntities)
        return insertedRouteId
    }

    override suspend fun addPoi(poi: Poi): Long {
        return poiDao.insertPoi(poi.toEntity())
    }

    override suspend fun deletePoiById(poiId: Long) {
        poiDao.deletePoiById(poiId)
    }

    override suspend fun deleteRoute(routeId: Long) {
        dao.deleteRoute(routeId)
    }

    override suspend fun importRoute(
        inputStream: InputStream,
        fallbackName: String
    ): Result<ImportedRouteSummary> {
        return importer.parse(inputStream, fallbackName).mapCatching { parsed ->
            val details = parsed.routeWithDetails
            val routeEntity = details.route.toEntity()
            val pointEntities = details.points.map { it.toEntity(routeId = 0L) }
            val poiEntities = details.pois.map { it.toEntity() }

            val generatedRouteId = dao.insertRouteWithDetails(
                route = routeEntity,
                points = pointEntities,
                pois = poiEntities
            )

            ImportedRouteSummary(
                routeId = generatedRouteId,
                routeName = details.route.name,
                distanceMeters = details.route.distanceMeters,
                elevationGainMeters = parsed.elevationGainMeters,
                pointsCount = details.points.size,
                poisCount = details.pois.size
            )
        }
    }
}
