package com.example.data.importer

import android.util.Xml
import com.example.domain.model.Poi
import com.example.domain.model.Route
import com.example.domain.model.RoutePoint
import com.example.domain.model.RouteWithDetails
import com.example.domain.util.LocationUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Resultado interno del parser nativo antes de persistir en Room.
 */
data class ParsedRouteData(
    val routeWithDetails: RouteWithDetails,
    val elevationGainMeters: Double
)

/**
 * Parser XML nativo de alto rendimiento para importar archivos GPX (1.1) y KML (2.2)
 * utilizando exclusivamente [android.util.Xml.newPullParser].
 */
class RouteFileImporter {

    fun parse(inputStream: InputStream, fallbackFileName: String): Result<ParsedRouteData> {
        return runCatching {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(inputStream, null)
            }

            // Avanzamos hasta la primera etiqueta raíz válida
            var eventType = parser.eventType
            while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
                eventType = parser.next()
            }

            if (eventType == XmlPullParser.END_DOCUMENT) {
                throw IllegalArgumentException("El archivo está vacío o no contiene XML válido.")
            }

            val rootTag = parser.name?.lowercase(Locale.US) ?: ""
            val cleanFallbackName = cleanFileName(fallbackFileName)

            when (rootTag) {
                "gpx" -> parseGpx(parser, cleanFallbackName)
                "kml" -> parseKml(parser, cleanFallbackName)
                else -> throw IllegalArgumentException(
                    "Formato no soportado: etiqueta raíz <$rootTag> inválida. Se esperaba <gpx> o <kml>."
                )
            }
        }.recoverCatching { throwable ->
            val friendlyMessage = when (throwable) {
                is XmlPullParserException ->
                    "El archivo XML está dañado o truncado: ${throwable.localizedMessage ?: "error de sintaxis"}"
                is IOException ->
                    "No se pudo leer el contenido del archivo."
                is IllegalArgumentException ->
                    throwable.message ?: "Archivo de ruta inválido."
                else ->
                    "Error inesperado al importar la ruta: ${throwable.localizedMessage ?: "desconocido"}"
            }
            throw IllegalArgumentException(friendlyMessage, throwable)
        }
    }

    private fun parseGpx(parser: XmlPullParser, fallbackName: String): ParsedRouteData {
        var metadataName: String? = null
        var trackName: String? = null
        val rawPoints = mutableListOf<RoutePoint>()
        val pois = mutableListOf<Poi>()

        var inMetadata = false
        var inTrk = false
        var inTrkseg = false
        var inTrkpt = false
        var inWpt = false

        // Variables temporales para <trkpt>
        var ptLat: Double? = null
        var ptLon: Double? = null
        var ptEle = 0.0
        var ptTime: Long? = null

        // Variables temporales para <wpt>
        var wptLat: Double? = null
        var wptLon: Double? = null
        var wptTitle = ""
        var wptDesc = ""

        val baseTime = System.currentTimeMillis()
        var lastTimestamp: Long? = null
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.lowercase(Locale.US)

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "metadata" -> inMetadata = true
                        "trk" -> inTrk = true
                        "trkseg" -> if (inTrk) inTrkseg = true
                        "trkpt" -> if (inTrkseg) {
                            inTrkpt = true
                            ptLat = parseNormalizedDouble(parser.getAttributeValue(null, "lat"))
                            ptLon = parseNormalizedDouble(parser.getAttributeValue(null, "lon"))
                            ptEle = 0.0
                            ptTime = null
                        }
                        "wpt" -> {
                            inWpt = true
                            wptLat = parseNormalizedDouble(parser.getAttributeValue(null, "lat"))
                            wptLon = parseNormalizedDouble(parser.getAttributeValue(null, "lon"))
                            wptTitle = ""
                            wptDesc = ""
                        }
                        "name" -> {
                            val text = safeNextText(parser)
                            when {
                                inWpt -> wptTitle = text
                                inMetadata && metadataName.isNullOrBlank() -> metadataName = text
                                inTrk && !inTrkseg && trackName.isNullOrBlank() -> trackName = text
                            }
                        }
                        "desc" -> if (inWpt) {
                            wptDesc = safeNextText(parser)
                        }
                        "cmt" -> if (inWpt && wptDesc.isBlank()) {
                            wptDesc = safeNextText(parser)
                        }
                        "ele" -> if (inTrkpt) {
                            ptEle = parseNormalizedDouble(safeNextText(parser)) ?: 0.0
                        }
                        "time" -> if (inTrkpt) {
                            ptTime = parseIso8601Timestamp(safeNextText(parser))
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (tagName) {
                        "metadata" -> inMetadata = false
                        "trk" -> inTrk = false
                        "trkseg" -> inTrkseg = false
                        "trkpt" -> {
                            if (inTrkpt && isValidCoordinate(ptLat, ptLon)) {
                                // Garantizamos marcas de tiempo crecientes respecto al punto anterior
                                // para preservar el orden exacto en consultas ORDER BY timestamp ASC
                                val safeTimestamp = ptTime ?: ((lastTimestamp ?: baseTime) + 1000L)
                                lastTimestamp = safeTimestamp
                                rawPoints.add(
                                    RoutePoint(
                                        latitude = ptLat!!,
                                        longitude = ptLon!!,
                                        altitude = ptEle,
                                        timestamp = safeTimestamp
                                    )
                                )
                            }
                            inTrkpt = false
                        }
                        "wpt" -> {
                            if (inWpt && isValidCoordinate(wptLat, wptLon)) {
                                pois.add(
                                    Poi(
                                        routeId = 0L,
                                        latitude = wptLat!!,
                                        longitude = wptLon!!,
                                        title = wptTitle.ifBlank { "Waypoint ${pois.size + 1}" },
                                        description = wptDesc,
                                        imageUri = null
                                    )
                                )
                            }
                            inWpt = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val finalName = metadataName?.takeIf { it.isNotBlank() }
            ?: trackName?.takeIf { it.isNotBlank() }
            ?: fallbackName

        return buildParsedRouteData(finalName, rawPoints, pois)
    }

    private fun parseKml(parser: XmlPullParser, fallbackName: String): ParsedRouteData {
        var documentName: String? = null
        var firstTrackPlacemarkName: String? = null
        val rawPoints = mutableListOf<RoutePoint>()
        val pois = mutableListOf<Poi>()

        var inDocument = false
        var inFolder = false
        var inPlacemark = false
        var inLineString = false
        var inPoint = false

        var placemarkName = ""
        var placemarkDesc = ""

        val baseTime = System.currentTimeMillis()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.lowercase(Locale.US)

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "document" -> inDocument = true
                        "folder" -> inFolder = true
                        "placemark" -> {
                            inPlacemark = true
                            placemarkName = ""
                            placemarkDesc = ""
                        }
                        "linestring" -> if (inPlacemark) inLineString = true
                        "point" -> if (inPlacemark) inPoint = true
                        "name" -> {
                            val text = safeNextText(parser)
                            when {
                                inPlacemark -> placemarkName = text
                                inDocument && !inFolder && documentName.isNullOrBlank() -> documentName = text
                            }
                        }
                        "description" -> if (inPlacemark) {
                            placemarkDesc = safeNextText(parser)
                        }
                        "coordinates" -> {
                            val coordsText = safeNextText(parser)
                            when {
                                inLineString -> {
                                    if (firstTrackPlacemarkName.isNullOrBlank() && placemarkName.isNotBlank()) {
                                        firstTrackPlacemarkName = placemarkName
                                    }
                                    val parsedCoords = parseKmlCoordinatesList(coordsText)
                                    parsedCoords.forEach { (lat, lon, alt) ->
                                        rawPoints.add(
                                            RoutePoint(
                                                latitude = lat,
                                                longitude = lon,
                                                altitude = alt,
                                                timestamp = baseTime + rawPoints.size * 1000L
                                            )
                                        )
                                    }
                                }
                                inPoint -> {
                                    val parsedPoint = parseKmlCoordinatesList(coordsText).firstOrNull()
                                    if (parsedPoint != null) {
                                        val (lat, lon, _) = parsedPoint
                                        pois.add(
                                            Poi(
                                                routeId = 0L,
                                                latitude = lat,
                                                longitude = lon,
                                                title = placemarkName.ifBlank { "Punto ${pois.size + 1}" },
                                                description = placemarkDesc,
                                                imageUri = null
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (tagName) {
                        "document" -> inDocument = false
                        "folder" -> inFolder = false
                        "placemark" -> inPlacemark = false
                        "linestring" -> inLineString = false
                        "point" -> inPoint = false
                    }
                }
            }
            eventType = parser.next()
        }

        val finalName = documentName?.takeIf { it.isNotBlank() }
            ?: firstTrackPlacemarkName?.takeIf { it.isNotBlank() }
            ?: fallbackName

        return buildParsedRouteData(finalName, rawPoints, pois)
    }

    /**
     * Parsea una cadena de coordenadas KML en formato "longitud,latitud[,altitud] ...".
     * Invierte correctamente (lon, lat) al orden estándar interno (lat, lon).
     */
    private fun parseKmlCoordinatesList(rawText: String): List<Triple<Double, Double, Double>> {
        if (rawText.isBlank()) return emptyList()
        val results = mutableListOf<Triple<Double, Double, Double>>()

        val tuples = rawText.trim().split(Regex("\\s+"))
        for (tuple in tuples) {
            if (tuple.isBlank()) continue
            val parts = tuple.split(',')
            if (parts.size >= 2) {
                val lon = parseNormalizedDouble(parts[0])
                val lat = parseNormalizedDouble(parts[1])
                val alt = if (parts.size >= 3) parseNormalizedDouble(parts[2]) ?: 0.0 else 0.0
                if (isValidCoordinate(lat, lon)) {
                    results.add(Triple(lat!!, lon!!, alt))
                }
            }
        }
        return results
    }

    private fun buildParsedRouteData(
        routeName: String,
        points: List<RoutePoint>,
        pois: List<Poi>
    ): ParsedRouteData {
        if (points.isEmpty() && pois.isEmpty()) {
            throw IllegalArgumentException("El archivo no contiene coordenadas válidas de recorrido ni puntos de interés.")
        }

        // Si solo hay POIs (ej. un KML solo con marcadores), generamos puntos de enlace o permitimos guardar con distancia 0
        val effectivePoints = if (points.isEmpty() && pois.isNotEmpty()) {
            val baseTime = System.currentTimeMillis()
            pois.mapIndexed { idx, poi ->
                RoutePoint(
                    latitude = poi.latitude,
                    longitude = poi.longitude,
                    altitude = 0.0,
                    timestamp = baseTime + idx * 1000L
                )
            }
        } else {
            points
        }

        val distanceMeters = LocationUtils.calculateDistance(effectivePoints)
        val elevationGain = calculatePositiveElevationGain(effectivePoints)
        val startTime = effectivePoints.first().timestamp
        val endTime = effectivePoints.last().timestamp
        val durationMillis = (endTime - startTime).coerceAtLeast(0L)

        val route = Route(
            id = 0L,
            name = routeName,
            timestamp = startTime,
            distanceMeters = distanceMeters,
            durationMillis = durationMillis
        )

        return ParsedRouteData(
            routeWithDetails = RouteWithDetails(
                route = route,
                points = effectivePoints,
                pois = pois
            ),
            elevationGainMeters = elevationGain
        )
    }

    private fun calculatePositiveElevationGain(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0
        var gain = 0.0
        for (i in 1 until points.size) {
            val diff = points[i].altitude - points[i - 1].altitude
            if (diff > 0.0) {
                gain += diff
            }
        }
        return gain
    }

    private fun parseNormalizedDouble(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().replace(',', '.').toDoubleOrNull()
    }

    private fun isValidCoordinate(lat: Double?, lon: Double?): Boolean {
        if (lat == null || lon == null) return false
        if (lat.isNaN() || lon.isNaN()) return false
        return lat in -90.0..90.0 && lon in -180.0..180.0
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return try {
            parser.nextText().trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseIso8601Timestamp(raw: String): Long? {
        if (raw.isBlank()) return null
        val cleaned = raw.trim()
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(cleaned)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Intentamos con el siguiente formato ISO-8601
            }
        }
        return null
    }

    private fun cleanFileName(fileName: String): String {
        val withoutExt = fileName.substringBeforeLast('.')
        return withoutExt.trim().ifBlank { "Ruta Importada" }
    }
}
