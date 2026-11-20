package com.example.presentation.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.Poi

/**
 * Diálogo de inspección de un POI (usado tanto en MapScreen como en RouteDetailScreen).
 * Muestra título, descripción, foto local con Coil y botón para eliminar previa confirmación.
 */
@Composable
fun PoiInspectionDialog(
    poi: Poi,
    onDismiss: () -> Unit,
    onDeleteConfirmed: (Long) -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = poi.title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                if (poi.description.isNotBlank()) {
                    Text(
                        text = poi.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (!poi.imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = poi.imageUri,
                        contentDescription = poi.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Text(
                        text = "Sin fotografía adjunta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cerrar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showConfirmDelete = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("delete_poi_dialog_button")
            ) {
                Text("Eliminar POI")
            }
        }
    )

    if (showConfirmDelete) {
        ConfirmDeletePoiDialog(
            poiTitle = poi.title,
            onDismiss = { showConfirmDelete = false },
            onConfirm = {
                showConfirmDelete = false
                onDeleteConfirmed(poi.id)
                onDismiss()
            }
        )
    }
}

@Composable
fun ConfirmDeletePoiDialog(
    poiTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar POI") },
        text = {
            Text("¿Deseas eliminar \"$poiTitle\" de este recorrido? Esta acción no se puede deshacer.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_delete_poi_button")
            ) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Tarjeta colapsable con la lista de POIs guardados para RouteDetailScreen.
 * Muestra miniatura (AsyncImage), título y botón de papelera con confirmación.
 */
@Composable
fun RoutePoisListCard(
    pois: List<Poi>,
    onPoiClick: (Poi) -> Unit,
    onDeletePoi: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pois.isEmpty()) return

    var expanded by remember { mutableStateOf(true) }
    var poiPendingDeletion by remember { mutableStateOf<Poi?>(null) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("route_pois_list_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Puntos de Interés (${pois.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Ocultar POIs" else "Mostrar POIs",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 136.dp)
                        .testTag("route_pois_lazy_column")
                ) {
                    items(items = pois, key = { it.id }) { poi ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPoiClick(poi) }
                                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            if (!poi.imageUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = poi.imageUri,
                                    contentDescription = poi.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = poi.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (poi.description.isNotBlank()) {
                                    Text(
                                        text = poi.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = { poiPendingDeletion = poi },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("delete_poi_row_${poi.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eliminar POI ${poi.title}",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    poiPendingDeletion?.let { poi ->
        ConfirmDeletePoiDialog(
            poiTitle = poi.title,
            onDismiss = { poiPendingDeletion = null },
            onConfirm = {
                poiPendingDeletion = null
                onDeletePoi(poi.id)
            }
        )
    }
}
