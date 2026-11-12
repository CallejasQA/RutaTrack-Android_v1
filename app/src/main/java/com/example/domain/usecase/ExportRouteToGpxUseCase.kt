package com.example.domain.usecase

import com.example.domain.model.RouteWithDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * Caso de Uso responsable de serializar un [RouteWithDetails] a formato XML GPX 1.1 estándar
 * y escribirlo en un [OutputStream].
 *
 * Cumple estrictamente con el esquema TopoGrafix GPX 1.1, garantizando compatibilidad con
 * Strava, Garmin Connect, Wikiloc y Google Earth.
 */
class ExportRouteToGpxUseCase @Inject constructor() {

    suspend operator fun invoke(routeDetails: RouteWithDetails, outputStream: OutputStream): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }

                outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    writer.write("<gpx version=\"1.1\" creator=\"RutaTrack Android\"\n")
                    writer.write("     xmlns=\"http://www.topografix.com/GPX/1/1\"\n")
                    writer.write("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
                    writer.write("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")

                    // Metadatos de la ruta
                    writer.write("  <metadata>\n")
                    writer.write("    <name>${escapeXml(routeDetails.route.name)}</name>\n")
                    writer.write("    <time>${isoDateFormat.format(Date(routeDetails.route.timestamp))}</time>\n")
                    writer.write("  </metadata>\n")

                    // Puntos de Interés (Waypoints <wpt>)
                    routeDetails.pois.forEach { poi ->
                        writer.write("  <wpt lat=\"${poi.latitude}\" lon=\"${poi.longitude}\">\n")
                        writer.write("    <name>${escapeXml(poi.title)}</name>\n")
                        if (poi.description.isNotBlank()) {
                            writer.write("    <desc>${escapeXml(poi.description)}</desc>\n")
                        }
                        writer.write("  </wpt>\n")
                    }

                    // Trazado de ruta (<trk> y <trkseg>)
                    writer.write("  <trk>\n")
                    writer.write("    <name>${escapeXml(routeDetails.route.name)}</name>\n")
                    writer.write("    <trkseg>\n")
                    routeDetails.points.forEach { pt ->
                        writer.write("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
                        if (pt.altitude != 0.0) {
                            writer.write("        <ele>${pt.altitude}</ele>\n")
                        }
                        writer.write("        <time>${isoDateFormat.format(Date(pt.timestamp))}</time>\n")
                        writer.write("      </trkpt>\n")
                    }
                    writer.write("    </trkseg>\n")
                    writer.write("  </trk>\n")

                    writer.write("</gpx>\n")
                    writer.flush()
                }
            }
        }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
