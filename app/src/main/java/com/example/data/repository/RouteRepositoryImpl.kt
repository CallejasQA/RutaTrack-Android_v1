package com.example.data.repository

import com.example.data.local.dao.RouteDao
import com.example.data.local.mapper.toDomain
import com.example.data.local.mapper.toEntity
import com.example.domain.model.Poi
import com.example.domain.model.Route
import com.example.domain.model.RoutePoint
import com.example.domain.model.RouteWithDetails
import com.example.domain.repository.RouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RouteRepositoryImpl(
    private val dao: RouteDao
) : RouteRepository {

    override fun getAllRoutes(): Flow<List<Route>> {
        // Transformamos el Flow de Room (Entity) a un Flow de Dominio
        return dao.getAllRoutes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRouteWithDetails(routeId: Long): Flow<RouteWithDetails> {
        // La magia reactiva: Combinamos los 3 flujos de la base de datos
        return combine(
            dao.getRouteById(routeId),
            dao.getRoutePoints(routeId),
            dao.getRoutePois(routeId)
        ) { routeEntity, pointsEntities, poisEntities ->
            RouteWithDetails(
                route = routeEntity.toDomain(),
                points = pointsEntities.map { it.toDomain() },
                pois = poisEntities.map { it.toDomain() }
            )
        }
    }

    override suspend fun saveRoute(route: Route, points: List<RoutePoint>): Long {
        // 1. Insertamos la ruta y obtenemos el ID autogenerado por SQLite
        val insertedRouteId = dao.insertRoute(route.toEntity())
        
        // 2. Mapeamos los puntos al modelo Entity inyectándoles el nuevo RouteId
        val pointEntities = points.map { it.toEntity(routeId = insertedRouteId) }
        
        // 3. Inserción masiva optimizada
        dao.insertRoutePoints(pointEntities)
        
        return insertedRouteId // Devolvemos el ID por si la UI quiere navegar a la pantalla de detalles
    }

    override suspend fun addPoi(poi: Poi): Long {
        return dao.insertPoi(poi.toEntity())
    }

    override suspend fun deleteRoute(routeId: Long) {
        dao.deleteRoute(routeId)
        // Recordatorio: Los puntos y POIs se borran solos gracias al CASCADE en Room.
    }
}
