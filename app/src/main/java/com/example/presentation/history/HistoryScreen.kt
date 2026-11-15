package com.example.presentation.history

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.di.AppViewModelProvider
import com.example.presentation.history.components.RouteCard
import com.example.presentation.navigation.Screen
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val routes by viewModel.routes.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Selector de documentos nativo (SAF).
    // REGLA CRÍTICA: Usamos "*/*" porque Android frecuentemente no mapea el MIME de .gpx/.kml
    // y los deshabilitaría si se aplicara un filtro MIME estricto.
    val importRouteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = resolveFileName(context, uri)
            val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)

            // Validamos extensión si está presente; el contenido XML se valida estrictamente en RouteFileImporter
            if (extension.isNotEmpty() && extension != "gpx" && extension != "kml") {
                viewModel.onFileValidationError(
                    "Archivo '$fileName' no válido. Selecciona un archivo con extensión .gpx o .kml."
                )
                return@rememberLauncherForActivityResult
            }

            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    viewModel.importRoute(inputStream, fileName)
                } else {
                    viewModel.onFileValidationError("No se pudo abrir el flujo de lectura para '$fileName'.")
                }
            } catch (e: Exception) {
                viewModel.onFileValidationError(
                    "Error al acceder al archivo: ${e.localizedMessage ?: "permiso denegado"}"
                )
            }
        }
    }

    // Reacción a los estados de importación (Éxito o Error)
    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportUiState.Success -> {
                val summary = state.summary
                val kmFormatted = String.format(Locale.US, "%.2f", summary.distanceMeters / 1000f)
                val gainMeters = summary.elevationGainMeters.roundToInt()
                val msg = "Importada '${summary.routeName}' ($kmFormatted km, +${gainMeters}m, ${summary.pointsCount} pts, ${summary.poisCount} POIs)"

                val actionResult = snackbarHostState.showSnackbar(
                    message = msg,
                    actionLabel = "Ver ruta",
                    duration = SnackbarDuration.Long
                )
                viewModel.resetImportState()
                if (actionResult == SnackbarResult.ActionPerformed) {
                    navController.navigate(Screen.RouteDetail.createRoute(summary.routeId))
                }
            }
            is ImportUiState.Error -> {
                // El error se muestra en AlertDialog para mayor claridad
            }
            else -> Unit
        }
    }

    BackHandler {
        navController.popBackStack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Mis Recorridos") },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { importRouteLauncher.launch(arrayOf("*/*")) },
                        enabled = importState !is ImportUiState.Loading,
                        modifier = Modifier.testTag("import_route_top_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Importar GPX o KML")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (importState !is ImportUiState.Loading) {
                        importRouteLauncher.launch(arrayOf("*/*"))
                    }
                },
                icon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                text = { Text("Importar GPX / KML") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("import_route_fab")
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (routes.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(48.dp)
                            .padding(bottom = 12.dp)
                    )
                    Text(
                        text = "Aún no tienes rutas guardadas.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Graba un recorrido o importa un archivo GPX/KML.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { importRouteLauncher.launch(arrayOf("*/*")) },
                        enabled = importState !is ImportUiState.Loading,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("import_route_empty_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importar Ruta (.gpx / .kml)")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("routes_lazy_column")
                ) {
                    items(
                        items = routes,
                        key = { it.id }
                    ) { route ->
                        RouteCard(
                            route = route,
                            onClick = {
                                navController.navigate(Screen.RouteDetail.createRoute(route.id))
                            },
                            onDeleteClick = {
                                viewModel.deleteRoute(route.id)
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }

            // Indicador modal de progreso durante el parseo e inserción atómica
            if (importState is ImportUiState.Loading) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .testTag("import_loading_card")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Importando ruta...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    // Diálogo descriptivo si la validación o parseo XML falla
    val errorState = importState as? ImportUiState.Error
    if (errorState != null) {
        AlertDialog(
            onDismissRequest = { viewModel.resetImportState() },
            title = { Text("No se pudo importar el archivo") },
            text = {
                Text(
                    text = errorState.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.resetImportState() },
                    modifier = Modifier.fillMaxWidth(0.4f)
                ) {
                    Text("Entendido")
                }
            }
        )
    }
}

/**
 * Obtiene el nombre real del archivo seleccionado mediante el ContentResolver.
 */
private fun resolveFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
    if (result.isNullOrBlank()) {
        result = uri.path?.substringAfterLast('/')
    }
    return result?.takeIf { it.isNotBlank() } ?: "Ruta_Importada"
}
