package com.example.presentation.detail.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun AddPoiDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, imageUri: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current

    // Selector de fotos nativo sin permisos (Zero-permission Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            // Regla crítica: persistir el permiso de lectura de URI para futuras sesiones
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            imageUri = it
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Nuevo Punto de Interés (POI)",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título *") },
                    placeholder = { Text("Ej. Mirador, Cruce río") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("poi_title_input"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción / Nota (opcional)") },
                    placeholder = { Text("Ej. Excelente vista para descansar") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("poi_desc_input"),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("pick_photo_button")
                ) {
                    Text(if (imageUri == null) "Adjuntar foto" else "✓ Foto adjuntada (cambiar)")
                }

                imageUri?.let { uri ->
                    Spacer(modifier = Modifier.height(10.dp))
                    AsyncImage(
                        model = uri,
                        contentDescription = "Vista previa del POI",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, description, imageUri?.toString()) },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_poi_confirm_button")
            ) {
                Text("Guardar POI")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cancel_poi_button")
            ) {
                Text("Cancelar")
            }
        }
    )
}
