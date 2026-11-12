package com.example

import com.example.domain.model.Poi
import com.example.domain.model.Route
import com.example.domain.model.RoutePoint
import com.example.domain.model.RouteWithDetails
import com.example.domain.usecase.ExportRouteToGpxUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ExportRouteToGpxUseCaseTest {

    private val useCase = ExportRouteToGpxUseCase()

    @Test
    fun testExportToGpxGeneratesValidXml() = runBlocking {
        val route = Route(
            id = 1L,
            name = "Ruta <Aventura & Montaña>",
            timestamp = 1700000000000L,
            distanceMeters = 1500f,
            durationMillis = 600000L
        )
        val points = listOf(
            RoutePoint(latitude = 40.4168, longitude = -3.7038, altitude = 650.0, timestamp = 1700000000000L),
            RoutePoint(latitude = 40.4170, longitude = -3.7040, altitude = 655.0, timestamp = 1700000030000L)
        )
        val pois = listOf(
            Poi(
                id = 1L,
                routeId = 1L,
                latitude = 40.4169,
                longitude = -3.7039,
                title = "Fuente de Agua",
                description = "Agua potable & fresca",
                imageUri = null
            )
        )
        val routeWithDetails = RouteWithDetails(route = route, points = points, pois = pois)

        val outputStream = ByteArrayOutputStream()
        val result = useCase(routeWithDetails, outputStream)

        assertTrue("UseCase execution should succeed", result.isSuccess)

        val gpxXml = outputStream.toString("UTF-8")

        // Verificaciones del esquema XML GPX 1.1
        assertTrue(gpxXml.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(gpxXml.contains("<gpx version=\"1.1\""))
        assertTrue(gpxXml.contains("<name>Ruta &lt;Aventura &amp; Montaña&gt;</name>"))
        assertTrue(gpxXml.contains("<wpt lat=\"40.4169\" lon=\"-3.7039\">"))
        assertTrue(gpxXml.contains("<desc>Agua potable &amp; fresca</desc>"))
        assertTrue(gpxXml.contains("<trk>"))
        assertTrue(gpxXml.contains("<trkpt lat=\"40.4168\" lon=\"-3.7038\">"))
        assertTrue(gpxXml.contains("<ele>650.0</ele>"))
        assertTrue(gpxXml.contains("</trkseg>"))
        assertTrue(gpxXml.contains("</trk>"))
        assertTrue(gpxXml.contains("</gpx>"))
    }
}
