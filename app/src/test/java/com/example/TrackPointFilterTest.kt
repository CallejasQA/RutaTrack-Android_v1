package com.example

import android.location.Location
import com.example.service.TrackPointFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackPointFilterTest {

    private fun makeLocation(
        lat: Double,
        lon: Double,
        accuracy: Float? = 10f,
        speed: Float? = null,
        timeMs: Long = System.currentTimeMillis()
    ): Location {
        return Location("gps").apply {
            latitude = lat
            longitude = lon
            time = timeMs
            if (accuracy != null) {
                this.accuracy = accuracy
            }
            if (speed != null) {
                this.speed = speed
            }
        }
    }

    @Test
    fun `rejects readings without accuracy or accuracy worse than 25m`() {
        val filter = TrackPointFilter()

        val noAccuracy = makeLocation(40.4168, -3.7038, accuracy = null)
        val poorAccuracy = makeLocation(40.4168, -3.7038, accuracy = 25.5f)
        val goodAccuracy = makeLocation(40.4168, -3.7038, accuracy = 25.0f)

        assertFalse("Should reject reading without accuracy", filter.shouldAccept(noAccuracy))
        assertFalse("Should reject reading with accuracy > 25m", filter.shouldAccept(poorAccuracy))
        assertTrue("Should accept first valid point with accuracy <= 25m", filter.shouldAccept(goodAccuracy))
    }

    @Test
    fun `always accepts first valid point and rejects stationary drift under 5m even after 30 minutes`() {
        val filter = TrackPointFilter()
        val baseTime = 1_700_000_000_000L

        // Primer punto válido
        val start = makeLocation(40.416800, -3.703800, accuracy = 8f, timeMs = baseTime)
        assertTrue(filter.shouldAccept(start))

        // Desplazamiento ~2.2m después de 30 minutos detenido -> debe descartarse (sin heartbeat por tiempo)
        val thirtyMinLater = baseTime + 30L * 60L * 1000L
        val drift2m = makeLocation(40.416820, -3.703800, accuracy = 8f, timeMs = thirtyMinLater)
        assertFalse("Should reject < 5m drift even after 30 minutes stationary", filter.shouldAccept(drift2m))
    }

    @Test
    fun `requires 10m threshold when speed is under 0_4 m_s to filter satellite bounce`() {
        val filter = TrackPointFilter()

        // Primer punto
        val p0 = makeLocation(40.416800, -3.703800, accuracy = 8f, speed = 0.0f)
        assertTrue(filter.shouldAccept(p0))

        // ~7m de desplazamiento pero velocidad 0.2 m/s (< 0.4 m/s) -> descartado porque exige >= 10m
        val drift7mLowSpeed = makeLocation(40.416863, -3.703800, accuracy = 8f, speed = 0.2f)
        assertFalse("Should reject 7m move when speed < 0.4 m/s", filter.shouldAccept(drift7mLowSpeed))

        // ~12m de desplazamiento con velocidad baja -> aceptado (>= 10m)
        val move12mLowSpeed = makeLocation(40.416910, -3.703800, accuracy = 8f, speed = 0.2f)
        assertTrue("Should accept >= 10m move even with low speed", filter.shouldAccept(move12mLowSpeed))
    }

    @Test
    fun `accepts 6m movement when speed is normal walking pace or speed unavailable`() {
        val filter = TrackPointFilter()

        assertTrue(filter.shouldAccept(makeLocation(40.416800, -3.703800, accuracy = 8f)))

        // ~6.6m con velocidad caminando (1.2 m/s >= 0.4 m/s) -> aceptado (>= 5m)
        val walk6m = makeLocation(40.416860, -3.703800, accuracy = 8f, speed = 1.2f)
        assertTrue("Should accept >= 5m move when speed >= 0.4 m/s", filter.shouldAccept(walk6m))

        // ~6.6m sin dato de velocidad -> aceptado (>= 5m)
        val move6mNoSpeed = makeLocation(40.416920, -3.703800, accuracy = 8f, speed = null)
        assertTrue("Should accept >= 5m move when hasSpeed() is false", filter.shouldAccept(move6mNoSpeed))
    }

    @Test
    fun `reset clears last saved point so next valid reading starts fresh`() {
        val filter = TrackPointFilter()
        val p0 = makeLocation(40.416800, -3.703800, accuracy = 8f)
        assertTrue(filter.shouldAccept(p0))

        // Mismo sitio antes de reset -> rechazado
        assertFalse(filter.shouldAccept(p0))

        filter.reset()
        // Mismo sitio tras reset -> aceptado como primer punto de la nueva ruta
        assertTrue(filter.shouldAccept(p0))
    }
}
