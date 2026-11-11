package com.example.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.local.dao.RouteDao
import com.example.data.local.entity.PoiEntity
import com.example.data.local.entity.RouteEntity
import com.example.data.local.entity.RoutePointEntity

@Database(
    entities = [RouteEntity::class, RoutePointEntity::class, PoiEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun routeDao(): RouteDao
    
    // Dejamos que el módulo de DI se encargue de instanciar y proveer esto como Singleton
}
