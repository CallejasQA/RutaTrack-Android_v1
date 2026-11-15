package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.PoiEntity
import com.example.data.local.entity.RouteEntity
import com.example.data.local.entity.RoutePointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {

    // --- LECTURAS (Reactivo con Flow) ---
    
    @Query("SELECT * FROM routes ORDER BY timestamp DESC")
    fun getAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    fun getRouteById(routeId: Long): Flow<RouteEntity>

    @Query("SELECT * FROM route_points WHERE routeId = :routeId ORDER BY timestamp ASC")
    fun getRoutePoints(routeId: Long): Flow<List<RoutePointEntity>>

    @Query("SELECT * FROM pois WHERE routeId = :routeId ORDER BY id ASC")
    fun getRoutePois(routeId: Long): Flow<List<PoiEntity>>

    // --- ESCRITURAS ---
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity): Long // Devuelve el ID autogenerado

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutePoints(points: List<RoutePointEntity>) // Inserción masiva (Batch)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoi(poi: PoiEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPois(pois: List<PoiEntity>) // Inserción masiva de POIs

    /**
     * Inserta atómicamente una ruta importada junto con todos sus puntos y POIs.
     * Si ocurre cualquier error en las inserciones hijas, Room revierte la transacción
     * completa (Rollback) para evitar rutas vacías o corruptas.
     */
    @Transaction
    suspend fun insertRouteWithDetails(
        route: RouteEntity,
        points: List<RoutePointEntity>,
        pois: List<PoiEntity>
    ): Long {
        val generatedRouteId = insertRoute(route)
        if (points.isNotEmpty()) {
            insertRoutePoints(points.map { it.copy(routeId = generatedRouteId) })
        }
        if (pois.isNotEmpty()) {
            insertPois(pois.map { it.copy(routeId = generatedRouteId) })
        }
        return generatedRouteId
    }

    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteRoute(routeId: Long)
    // Nota: ¡No necesitamos borrar los puntos ni los POIs manualmente gracias al onDelete = CASCADE!
}
