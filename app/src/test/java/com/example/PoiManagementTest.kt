package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.importer.RouteFileImporter
import com.example.data.local.database.AppDatabase
import com.example.data.repository.RouteRepositoryImpl
import com.example.domain.model.Poi
import com.example.domain.model.Route
import com.example.domain.model.RoutePoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PoiManagementTest {

    @Test
    fun `draft route finalization preserves active POIs and deletePoiById removes POI cleanly`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val repo = RouteRepositoryImpl(db.routeDao(), db.poiDao(), RouteFileImporter())

            // 1. Creamos ruta borrador activa y agregamos 2 POIs durante el recorrido
            val activeRouteId = repo.createDraftRoute("Recorrido en curso...")
            val poi1Id = repo.addPoi(
                Poi(
                    routeId = activeRouteId,
                    latitude = 40.4168,
                    longitude = -3.7038,
                    title = "Mirador",
                    description = "Vista al valle",
                    imageUri = "content://media/external/images/media/42"
                )
            )
            val poi2Id = repo.addPoi(
                Poi(
                    routeId = activeRouteId,
                    latitude = 40.4175,
                    longitude = -3.7042,
                    title = "Cruce río",
                    description = "",
                    imageUri = null
                )
            )

            // 2. Finalizamos la ruta con puntos (debe usar @Update sin disparar ON DELETE CASCADE)
            repo.finalizeRoute(
                route = Route(
                    id = activeRouteId,
                    name = "Ruta Sierra Finalizada",
                    timestamp = 1_700_000_000_000L,
                    distanceMeters = 1200f,
                    durationMillis = 600_000L
                ),
                points = listOf(
                    RoutePoint(40.4168, -3.7038, 650.0, 1_700_000_000_000L),
                    RoutePoint(40.4180, -3.7045, 670.0, 1_700_000_600_000L)
                )
            )

            val detailsAfterSave = repo.getRouteWithDetails(activeRouteId).first()
            assertEquals("Ruta Sierra Finalizada", detailsAfterSave.route.name)
            assertEquals(2, detailsAfterSave.points.size)
            assertEquals(2, detailsAfterSave.pois.size)

            // 3. Eliminamos un POI por ID y verificamos actualización reactiva en Flow
            repo.deletePoiById(poi1Id)
            val remainingPois = repo.getPoisByRouteId(activeRouteId).first()
            assertEquals(1, remainingPois.size)
            assertEquals(poi2Id, remainingPois.first().id)

            // 4. Al eliminar la ruta, CASCADE elimina los POIs restantes
            repo.deleteRoute(activeRouteId)
            val poisAfterRouteDelete = repo.getPoisByRouteId(activeRouteId).first()
            assertTrue(poisAfterRouteDelete.isEmpty())
        } finally {
            db.close()
        }
    }
}
