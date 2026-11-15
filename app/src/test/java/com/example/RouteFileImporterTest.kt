package com.example

import com.example.data.importer.RouteFileImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RouteFileImporterTest {

    private val importer = RouteFileImporter()

    @Test
    fun `parse valid GPX 1_1 with metadata wpt trkpt and decimal commas`() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata>
                <name>Sendero Sierra</name>
              </metadata>
              <wpt lat="40,4169" lon="-3,7039">
                <name>Mirador</name>
                <desc>Vista panorámica</desc>
              </wpt>
              <trk>
                <name>Track Ignorado</name>
                <trkseg>
                  <trkpt lat="40.4168" lon="-3.7038">
                    <ele>650,5</ele>
                    <time>2025-05-01T10:00:00Z</time>
                  </trkpt>
                  <trkpt lat="40.4178" lon="-3.7038">
                    <ele>680.5</ele>
                    <time>2025-05-01T10:05:00Z</time>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val result = importer.parse(gpx.byteInputStream(), "fallback.gpx")
        assertTrue("GPX parsing should succeed", result.isSuccess)

        val parsed = result.getOrThrow()
        val details = parsed.routeWithDetails

        assertEquals("Sendero Sierra", details.route.name)
        assertEquals(2, details.points.size)
        assertEquals(1, details.pois.size)

        // Verifica altitud normalizada y desnivel positivo (+30.0m)
        assertEquals(650.5, details.points[0].altitude, 0.01)
        assertEquals(680.5, details.points[1].altitude, 0.01)
        assertEquals(30.0, parsed.elevationGainMeters, 0.01)

        // Verifica duración (5 minutos = 300,000 ms) y distancia > 0
        assertEquals(300_000L, details.route.durationMillis)
        assertTrue(details.route.distanceMeters > 100f)

        // Verifica POI
        assertEquals("Mirador", details.pois[0].title)
        assertEquals("Vista panorámica", details.pois[0].description)
        assertEquals(40.4169, details.pois[0].latitude, 0.0001)
        assertEquals(-3.7039, details.pois[0].longitude, 0.0001)
    }

    @Test
    fun `parse valid KML 2_2 reverses lon lat alt order correctly`() {
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>Ruta KML Pirineos</name>
                <Placemark>
                  <name>Refugio</name>
                  <description>Refugio de montaña</description>
                  <Point>
                    <coordinates>-0.5000,42.7000,1800</coordinates>
                  </Point>
                </Placemark>
                <Placemark>
                  <name>Trazado</name>
                  <LineString>
                    <coordinates>
                      -0.5000,42.7000,1800
                      -0.5010,42.7010,1850
                    </coordinates>
                  </LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val result = importer.parse(kml.byteInputStream(), "ruta.kml")
        assertTrue("KML parsing should succeed", result.isSuccess)

        val parsed = result.getOrThrow()
        val details = parsed.routeWithDetails

        assertEquals("Ruta KML Pirineos", details.route.name)
        assertEquals(2, details.points.size)
        assertEquals(1, details.pois.size)

        // Verifica inversión (lon, lat, alt) -> (lat, lon, alt)
        assertEquals(42.7000, details.points[0].latitude, 0.0001)
        assertEquals(-0.5000, details.points[0].longitude, 0.0001)
        assertEquals(1800.0, details.points[0].altitude, 0.01)
        assertEquals(50.0, parsed.elevationGainMeters, 0.01)

        assertEquals("Refugio", details.pois[0].title)
        assertEquals(42.7000, details.pois[0].latitude, 0.0001)
        assertEquals(-0.5000, details.pois[0].longitude, 0.0001)
    }

    @Test
    fun `parse rejects invalid root tag html or earth`() {
        val html = "<html><body><h1>Not a route</h1></body></html>"
        val earth = "<earth><point>1,2</point></earth>"

        val resHtml = importer.parse(html.byteInputStream(), "bad.html")
        val resEarth = importer.parse(earth.byteInputStream(), "bad.xml")

        assertTrue(resHtml.isFailure)
        assertTrue(resEarth.isFailure)
        assertTrue(resEarth.exceptionOrNull()?.message?.contains("earth") == true)
    }

    @Test
    fun `parse rejects empty and truncated xml gracefully`() {
        val emptyResult = importer.parse("".byteInputStream(), "empty.gpx")
        val truncatedGpx = "<gpx version=\"1.1\"><trk><trkseg><trkpt lat=\"40.0\" lon=\"-3.0\">"
        val truncatedResult = importer.parse(truncatedGpx.byteInputStream(), "broken.gpx")

        assertTrue(emptyResult.isFailure)
        assertTrue(truncatedResult.isFailure)
    }
}
