package com.example.presentation.detail.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.RoutePoint
import kotlin.math.roundToInt

/**
 * Datos calculados para la normalización del perfil altimétrico.
 */
data class ElevationBounds(
    val minAltitude: Double,
    val maxAltitude: Double,
    val altitudeDelta: Double
)

/**
 * Paso 1: Función matemática auxiliar que analiza los puntos del recorrido y
 * extrae los límites de altitud necesarios para escalar las coordenadas en el Canvas.
 */
fun calculateElevationBounds(points: List<RoutePoint>): ElevationBounds {
    if (points.isEmpty()) {
        return ElevationBounds(minAltitude = 0.0, maxAltitude = 0.0, altitudeDelta = 0.0)
    }

    var min = Double.MAX_VALUE
    var max = Double.MIN_VALUE

    for (pt in points) {
        if (pt.altitude < min) min = pt.altitude
        if (pt.altitude > max) max = pt.altitude
    }

    if (min == Double.MAX_VALUE || max == Double.MIN_VALUE) {
        min = 0.0
        max = 0.0
    }

    return ElevationBounds(
        minAltitude = min,
        maxAltitude = max,
        altitudeDelta = (max - min).coerceAtLeast(0.0)
    )
}

/**
 * Paso 2: Composable que renderiza el perfil altimétrico de forma ultra-eficiente
 * utilizando únicamente el Canvas nativo de Jetpack Compose y un único Path vectorial continuo.
 *
 * Matemáticas de Traslación y Escala:
 * - Eje X (Progreso): Mapea linealmente el índice del punto `i` de `[0 .. N-1]` al rango de píxeles `[0 .. width]`.
 *   Fórmula: x = (i / (N - 1)) * size.width
 * - Eje Y (Altitud invertida): Dado que el origen (0,0) del Canvas está en la esquina superior izquierda,
 *   las altitudes mayores deben tener un valor Y menor (más cercano a 0).
 *   Fórmula: y = size.height - paddingBottom - (normalizedElevation * availableHeight)
 */
@Composable
fun ElevationGraph(
    points: List<RoutePoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gradientStartColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
    gradientEndColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
) {
    val bounds = remember(points) { calculateElevationBounds(points) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("elevation_graph_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Cabecera con estadísticas clave de elevación
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Perfil de Elevación",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Min: ${bounds.minAltitude.roundToInt()}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "Max: ${bounds.maxAltitude.roundToInt()}m",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Δ: ${bounds.altitudeDelta.roundToInt()}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            if (points.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay suficientes datos de altitud disponibles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                // Lienzo Canvas de alto rendimiento
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .padding(top = 10.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                        .testTag("elevation_canvas")
                ) {
                    val width = size.width
                    val height = size.height

                    val paddingTop = 12.dp.toPx()
                    val paddingBottom = 12.dp.toPx()
                    val availableHeight = (height - paddingTop - paddingBottom).coerceAtLeast(1f)

                    val isFlat = bounds.altitudeDelta < 1.0

                    // Generamos dos Paths vectoriales continuos:
                    // 1. fillPath: Trazado cerrado para el degradado bajo la curva
                    // 2. strokePath: Línea superior nítida
                    val strokePath = Path()
                    val fillPath = Path()

                    val stepX = if (points.size > 1) width / (points.size - 1) else width

                    points.forEachIndexed { index, point ->
                        val currentX = index * stepX
                        val currentY = if (isFlat) {
                            height / 2f
                        } else {
                            val normalized = ((point.altitude - bounds.minAltitude) / bounds.altitudeDelta).toFloat()
                            height - paddingBottom - (normalized * availableHeight)
                        }

                        if (index == 0) {
                            strokePath.moveTo(currentX, currentY)
                            fillPath.moveTo(currentX, height) // Comienza en la base inferior
                            fillPath.lineTo(currentX, currentY)
                        } else {
                            strokePath.lineTo(currentX, currentY)
                            fillPath.lineTo(currentX, currentY)
                        }

                        if (index == points.lastIndex) {
                            fillPath.lineTo(currentX, height) // Baja a la base inferior
                            fillPath.close() // Cierra el polígono
                        }
                    }

                    // 1. Dibujamos el área translúcida con degradado vertical
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(gradientStartColor, gradientEndColor),
                            startY = paddingTop,
                            endY = height
                        )
                    )

                    // 2. Dibujamos la línea continua superior
                    drawPath(
                        path = strokePath,
                        color = lineColor,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}
