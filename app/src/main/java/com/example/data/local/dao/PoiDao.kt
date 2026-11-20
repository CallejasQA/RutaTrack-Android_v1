package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.PoiEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiDao {

    @Query("SELECT * FROM pois WHERE routeId = :routeId ORDER BY id ASC")
    fun getPoisByRouteId(routeId: Long): Flow<List<PoiEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoi(poi: PoiEntity): Long

    @Query("DELETE FROM pois WHERE id = :poiId")
    suspend fun deletePoiById(poiId: Long)
}
