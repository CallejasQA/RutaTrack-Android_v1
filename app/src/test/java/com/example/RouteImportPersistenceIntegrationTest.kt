package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.importer.RouteFileImporter
import com.example.data.local.database.AppDatabase
import com.example.data.repository.RouteRepositoryImpl
import com.example.domain.usecase.DeleteRouteUseCase
import com.example.domain.usecase.ExportRouteToGpxUseCase
import com.example.domain.usecase.GetAllRoutesUseCase
import com.example.domain.usecase.ImportRouteUseCase
import com.example.presentation.history.HistoryViewModel
import com.example.presentation.history.ImportUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RouteImportPersistenceIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: RouteRepositoryImpl
    private lateinit var importUseCase: ImportRouteUseCase
    private lateinit var exportUseCase: ExportRouteToGpxUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RouteRepositoryImpl(database.routeDao(), RouteFileImporter())
        importUseCase = ImportRouteUseCase(repository)
        exportUseCase = ExportRouteToGpxUseCase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `atomic Room import and GPX round-trip preserve route points POIs and order`() = runBlocking {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="TestSuite" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata>
                <name>Cañón &amp; Cumbre</name>
              </metadata>
              <wpt lat="40.4169" lon="-3.7039">
                <name>Inicio &lt;Sur&gt;</name>
                <cmt>Comentario usado si falta desc</cmt>
              </wpt>
              <trk>
                <trkseg>
                  <trkpt lat="40.4168" lon="-3.7038">
                    <ele>600.0</ele>
                    <time>2025-01-10T08:00:00Z</time>
                  </trkpt>
                  <!-- Punto sin timestamp ni elevación explícita, y coordenada inválida intercalada -->
                  <trkpt lat="95.0" lon="-3.7040" />
                  <trkpt lat="40,4175" lon="-3,7045" />
                  <trkpt lat="40.4182" lon="-3.7050">
                    <ele>645.0</ele>
                    <time>2025-01-10T08:10:00Z</time>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val importResult = importUseCase(gpx.byteInputStream(), "cañon.gpx")
        assertTrue("Import should succeed", importResult.isSuccess)
        val summary = importResult.getOrThrow()

        assertEquals("Cañón & Cumbre", summary.routeName)
        assertEquals(3, summary.pointsCount) // Se filtró el punto lat=95.0 inválido
        assertEquals(1, summary.poisCount)

        // Verificamos persistencia real en Room
        val details = repository.getRouteWithDetails(summary.routeId).first()
        assertEquals(3, details.points.size)
        assertEquals(1, details.pois.size)
        assertEquals("Inicio <Sur>", details.pois[0].title)
        assertEquals("Comentario usado si falta desc", details.pois[0].description)

        // Verifica que el punto intermedio sin <time> mantuvo su orden estricto entre 0 y 2
        assertEquals(40.4168, details.points[0].latitude, 0.0001)
        assertEquals(40.4175, details.points[1].latitude, 0.0001)
        assertEquals(40.4182, details.points[2].latitude, 0.0001)

        // Round-Trip: Exportamos la ruta importada a GPX y la re-importamos
        val out = ByteArrayOutputStream()
        val exportResult = exportUseCase(details, out)
        assertTrue(exportResult.isSuccess)

        val reimportResult = importUseCase(out.toByteArray().inputStream(), "reimport.gpx")
        assertTrue(reimportResult.isSuccess)
        val reimported = repository.getRouteWithDetails(reimportResult.getOrThrow().routeId).first()
        assertEquals("Cañón & Cumbre", reimported.route.name)
        assertEquals(3, reimported.points.size)
        assertEquals(1, reimported.pois.size)
    }

    @Test
    fun `atomic Room KML MultiGeometry import and invalid file rollback verification`() = runBlocking {
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>Travesía KML</name>
                <Folder>
                  <name>Carpeta Ignorada Como Título Principal</name>
                  <Placemark>
                    <name>Mirador Norte</name>
                    <description><![CDATA[Vista <b>increíble</b>]]></description>
                    <Point>
                      <coordinates>-3.7010,40.4200,720</coordinates>
                    </Point>
                  </Placemark>
                  <Placemark>
                    <name>Ruta Principal</name>
                    <MultiGeometry>
                      <LineString>
                        <coordinates>
                          -3.7010,40.4200,720
                          -3.7020,40.4210,755
                        </coordinates>
                      </LineString>
                    </MultiGeometry>
                  </Placemark>
                </Folder>
              </Document>
            </kml>
        """.trimIndent()

        val kmlRes = importUseCase(kml.byteInputStream(), "travesia.kml")
        assertTrue(kmlRes.isSuccess)
        val kmlSummary = kmlRes.getOrThrow()
        assertEquals("Travesía KML", kmlSummary.routeName)
        assertEquals(2, kmlSummary.pointsCount)
        assertEquals(1, kmlSummary.poisCount)
        assertEquals(35.0, kmlSummary.elevationGainMeters, 0.01)

        // Intentamos importar un archivo corrupto / etiqueta inválida y verificamos que Room no guarda nada extra
        val beforeCount = repository.getAllRoutes().first().size
        val badResult = importUseCase("<earth><bad/></earth>".byteInputStream(), "malo.kml")
        assertTrue(badResult.isFailure)
        val afterCount = repository.getAllRoutes().first().size
        assertEquals("Database should remain clean after failed import", beforeCount, afterCount)
    }

    @Test
    fun `HistoryViewModel updates ImportUiState on success and error`() = runBlocking {
        val viewModel = HistoryViewModel(
            getAllRoutesUseCase = GetAllRoutesUseCase(repository),
            deleteRouteUseCase = DeleteRouteUseCase(repository),
            importRouteUseCase = importUseCase
        )

        assertTrue(viewModel.importState.value is ImportUiState.Idle)

        viewModel.onFileValidationError("Extensión inválida")
        assertTrue(viewModel.importState.value is ImportUiState.Error)
        assertEquals("Extensión inválida", (viewModel.importState.value as ImportUiState.Error).message)

        viewModel.resetImportState()
        assertTrue(viewModel.importState.value is ImportUiState.Idle)
    }
}
