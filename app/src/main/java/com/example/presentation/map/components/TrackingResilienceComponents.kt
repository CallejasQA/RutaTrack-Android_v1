package com.example.presentation.map.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Comprueba si el usuario ha concedido permiso de ubicación permanente ("Permitir todo el tiempo").
 * En Android < 10 (API < 29), el permiso en primer plano cubre la ejecución en segundo plano.
 */
fun hasBackgroundLocationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Verifica si la aplicación está exenta de la optimización de batería del sistema (Doze / ahorro fabricante).
 */
fun isBatteryOptimizationIgnored(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Abre el diálogo nativo del sistema para solicitar exención de optimización de batería.
 */
@SuppressLint("BatteryLife")
fun requestIgnoreBatteryOptimizations(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(fallbackIntent)
        }
    }
}

/**
 * Diálogo explicativo (Google Play Prominent Disclosure) antes de pedir ACCESS_BACKGROUND_LOCATION.
 * Explica claramente por qué RutaTrack requiere "Permitir todo el tiempo" para rutas largas.
 */
@Composable
fun BackgroundLocationRationaleDialog(
    onGrantClick: () -> Unit,
    onContinueForegroundOnly: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onContinueForegroundOnly,
        icon = {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Ubicación continua en segundo plano",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "Para grabar recorridos largos durante varias horas con la pantalla apagada o la app en el bolsillo sin cortes de señal GPS, selecciona \"Permitir todo el tiempo\" en la siguiente pantalla de permisos del sistema.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onGrantClick,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("grant_background_location_button")
            ) {
                Text("Configurar \"Todo el tiempo\"")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onContinueForegroundOnly,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("skip_background_location_button")
            ) {
                Text("Solo mientras uso")
            }
        }
    )
}

/**
 * Aviso discreto sobre el mapa cuando el ahorro de batería está restringiendo la app.
 */
@Composable
fun BatteryOptimizationBanner(
    onOptimizeClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("battery_optimization_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Evita cortes de GPS en rutas largas",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onOptimizeClick,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("battery_optimize_action_button")
            ) {
                Text("Permitir")
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("battery_banner_dismiss_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar aviso de batería",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
