package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.importer.RouteFileImporter
import com.example.data.local.database.AppDatabase
import com.example.data.repository.RouteRepositoryImpl
import com.example.domain.model.Poi
import com.example.domain.model.Route
import com.example.domain.model.RoutePoint
import com.example.domain.usecase.GetRouteWithPointsUseCase
import com.example.domain.usecase.SaveRouteUseCase
import com.example.presentation.map.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FollowRouteModeTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var repository: RouteRepositoryImpl
    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RouteRepositoryImpl(database.routeDao(), RouteFileImporter())
        viewModel = MapViewModel(
            saveRouteUseCase = SaveRouteUseCase(repository),
            getRouteWithPointsUseCase = GetRouteWithPointsUseCase(repository)
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `calculateMinDistanceToTrackMeters computes offline deviation accurately`() {
        val track = listOf(
            RoutePoint(latitude = 40.4168, longitude = -3.7038, timestamp = 1000L),
            RoutePoint(latitude = 40.4178, longitude = -3.7038, timestamp = 2000L)
        )

        // Exactamente sobre el primer punto -> ~0 metros
        val onTrack = MapViewModel.calculateMinDistanceToTrackMeters(40.4168, -3.7038, track)
        assertNotNull(onTrack)
        assertTrue(onTrack!! < 1f)

        // Desviado ~0.001 grados de longitud (~85 m en latitud 40°) -> > 30 metros
        val offTrack = MapViewModel.calculateMinDistanceToTrackMeters(40.4168, -3.7028, track)
        assertNotNull(offTrack)
        assertTrue("Expected deviation > 30m, got $offTrack", offTrack!! > 30f)

        // Lista vacía -> null
        assertNull(MapViewModel.calculateMinDistanceToTrackMeters(40.4168, -3.7038, emptyList()))
    }

    @Test
    fun `follow route mode operates independently of recording and clears on exit`() = runBlocking {
        val routeId = repository.saveRoute(
            route = Route(
                name = "Sendero Norte",
                timestamp = 1000L,
                distanceMeters = 120f,
                durationMillis = 60_000L
            ),
            points = listOf(
                RoutePoint(latitude = 40.4168, longitude = -3.7038, timestamp = 1000L),
                RoutePoint(latitude = 40.4178, longitude = -3.7038, timestamp = 61_000L)
            )
        )
        repository.addPoi(
            Poi(
                routeId = routeId,
                latitude = 40.4170,
                longitude = -3.7038,
                title = "Mirador",
                description = "Vista",
                imageUri = null
            )
        )

        val collectorJob = launch(testDispatcher) { viewModel.state.collect() }

        viewModel.loadReferenceRoute(routeId)
        viewModel.updateUserLocation(40.4168, -3.7038)

        withTimeout(3000) {
            while (!viewModel.state.value.followRouteMode.isActive ||
                viewModel.state.value.followRouteMode.distanceFromTrackMeters == null
            ) {
                kotlinx.coroutines.delay(50)
            }
        }

        val activeState = viewModel.state.value
        assertFalse("Tracking service need not be active", activeState.isTracking)
        assertTrue(activeState.followRouteMode.isActive)
        assertEquals("Sendero Norte", activeState.followRouteMode.referenceRoute?.name)
        assertEquals(2, activeState.followRouteMode.referencePoints.size)
        assertEquals(1, activeState.followRouteMode.referencePois.size)
        assertTrue(activeState.followRouteMode.distanceFromTrackMeters!! <= 25f)

        // Alternar centrado de cámara
        assertTrue(activeState.followRouteMode.isCameraCenteredOnUser)
        viewModel.toggleCameraCenteredOnUser()
        assertFalse(viewModel.state.value.followRouteMode.isCameraCenteredOnUser)

        // Salir del modo Seguir Huella
        viewModel.exitFollowRouteMode()
        assertFalse(viewModel.state.value.followRouteMode.isActive)
        assertNull(viewModel.state.value.followRouteMode.referenceRoute)

        collectorJob.cancel()
    }
}
