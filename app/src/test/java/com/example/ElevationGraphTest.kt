package com.example

import com.example.domain.model.RoutePoint
import com.example.presentation.detail.components.calculateElevationBounds
import org.junit.Assert.assertEquals
import org.junit.Test

class ElevationGraphTest {

    @Test
    fun testCalculateElevationBoundsWithVariedAltitudes() {
        val points = listOf(
            RoutePoint(latitude = 40.0, longitude = -3.0, altitude = 500.0, timestamp = 1000L),
            RoutePoint(latitude = 40.1, longitude = -3.1, altitude = 750.0, timestamp = 2000L),
            RoutePoint(latitude = 40.2, longitude = -3.2, altitude = 620.0, timestamp = 3000L),
            RoutePoint(latitude = 40.3, longitude = -3.3, altitude = 810.0, timestamp = 4000L)
        )

        val bounds = calculateElevationBounds(points)
        assertEquals(500.0, bounds.minAltitude, 0.001)
        assertEquals(810.0, bounds.maxAltitude, 0.001)
        assertEquals(310.0, bounds.altitudeDelta, 0.001)
    }

    @Test
    fun testCalculateElevationBoundsEmptyList() {
        val bounds = calculateElevationBounds(emptyList())
        assertEquals(0.0, bounds.minAltitude, 0.001)
        assertEquals(0.0, bounds.maxAltitude, 0.001)
        assertEquals(0.0, bounds.altitudeDelta, 0.001)
    }

    @Test
    fun testCalculateElevationBoundsFlatRoute() {
        val points = listOf(
            RoutePoint(latitude = 40.0, longitude = -3.0, altitude = 600.0, timestamp = 1000L),
            RoutePoint(latitude = 40.1, longitude = -3.1, altitude = 600.0, timestamp = 2000L)
        )

        val bounds = calculateElevationBounds(points)
        assertEquals(600.0, bounds.minAltitude, 0.001)
        assertEquals(600.0, bounds.maxAltitude, 0.001)
        assertEquals(0.0, bounds.altitudeDelta, 0.001)
    }
}
