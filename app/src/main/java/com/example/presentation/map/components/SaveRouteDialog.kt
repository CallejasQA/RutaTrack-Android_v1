package com.example.presentation.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun SaveRouteDialog(
    distanceMeters: Float,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDiscard: () -> Unit
) {
    var routeName by remember { mutableStateOf("") }
    val distanceKm = distanceMeters / 1000f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Finalizar Recorrido",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Distancia total acumulada: ${String.format("%.2f", distanceKm)} km",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = routeName,
                    onValueChange = { routeName = it },
                    label = { Text("Nombre de la ruta") },
                    placeholder = { Text("Ej. Paseo por el parque") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("route_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(if (routeName.isBlank()) "Ruta sin nombre" else routeName) },
                modifier = Modifier.testTag("confirm_save_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDiscard,
                modifier = Modifier.testTag("discard_route_button")
            ) {
                Text("Descartar")
            }
        }
    )
}
