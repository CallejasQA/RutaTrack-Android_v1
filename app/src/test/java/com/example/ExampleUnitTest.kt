package com.example

import com.example.domain.model.RoutePoint
import com.example.domain.util.LocationUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun locationUtils_calculatesDistanceCorrectly() {
    // Coordenadas conocidas: Madrid a Barcelona (~505 km línea recta)
    val madrid = RoutePoint(latitude = 40.4168, longitude = -3.7038, timestamp = 1000L)
    val barcelona = RoutePoint(latitude = 41.3879, longitude = 2.1699, timestamp = 2000L)

    val distanceMeters = LocationUtils.calculateDistance(listOf(madrid, barcelona))
    val distanceKm = distanceMeters / 1000f

    // La distancia ortodrómica aproximada está entre 500 y 510 km
    assertTrue(distanceKm in 500f..510f)
  }
}
