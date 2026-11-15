package com.example

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.importer.RouteFileImporter
import com.example.data.local.database.AppDatabase
import com.example.data.repository.RouteRepositoryImpl
import com.example.domain.usecase.SaveRouteUseCase
import com.example.presentation.map.MapScreen
import com.example.presentation.map.MapViewModel
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapLocationOverlayTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setupPermissionsAndCachedLocation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val locationManager = app.getSystemService(LocationManager::class.java)
        val shadowLocationManager = Shadows.shadowOf(locationManager)
        val cachedLocation = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = 40.4200
            longitude = -3.7100
            time = System.currentTimeMillis()
        }
        shadowLocationManager.setLastKnownLocation(LocationManager.NETWORK_PROVIDER, cachedLocation)
    }

    @Test
    fun `MapScreen configures hybrid GPS+Network provider, accuracy circle, and keeps MyLocationNewOverlay on top`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RouteRepositoryImpl(db.routeDao(), RouteFileImporter())
        val viewModel = MapViewModel(saveRouteUseCase = SaveRouteUseCase(repo))

        try {
            composeTestRule.setContent {
                MyApplicationTheme {
                    MapScreen(viewModel = viewModel)
                }
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("osmdroid_map_view").assertIsDisplayed()

            val mapView = findMapViewRecursive(composeTestRule.activity.window.decorView)
            assertNotNull("MapView should exist in activity view hierarchy", mapView)

            val overlays = mapView!!.overlays
            assertTrue("Expected Polyline and MyLocationNewOverlay", overlays.size >= 2)
            assertTrue("First overlay should be Polyline", overlays.first() is Polyline)
            assertTrue(
                "Last overlay must always be MyLocationNewOverlay",
                overlays.last() is MyLocationNewOverlay
            )

            val myLocationOverlay = overlays.last() as MyLocationNewOverlay
            assertTrue(
                "Accuracy circle must be enabled for high-density screens",
                myLocationOverlay.isDrawAccuracyEnabled
            )
            assertTrue(
                "MyLocationNewOverlay should be enabled when permission is granted",
                myLocationOverlay.isMyLocationEnabled
            )

            val provider = myLocationOverlay.myLocationProvider as GpsMyLocationProvider
            assertTrue(
                "Hybrid provider must include GPS_PROVIDER",
                provider.locationSources.contains(LocationManager.GPS_PROVIDER)
            )
            assertTrue(
                "Hybrid provider must include NETWORK_PROVIDER",
                provider.locationSources.contains(LocationManager.NETWORK_PROVIDER)
            )

            assertEquals(40.4200, mapView.mapCenter.latitude, 0.001)
            assertEquals(-3.7100, mapView.mapCenter.longitude, 0.001)
        } finally {
            db.close()
        }
    }

    private fun findMapViewRecursive(view: View?): MapView? {
        if (view == null) return null
        if (view is MapView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findMapViewRecursive(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }
}
