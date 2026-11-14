package com.example.presentation.detail

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.di.AppViewModelProvider
import com.example.domain.model.Poi
import com.example.presentation.detail.components.AddPoiDialog
import com.example.presentation.detail.components.ElevationGraph
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    routeId: Long,
    navController: NavController,
    viewModel: RouteDetailViewModel = viewModel(factory = AppViewModelProvider.provideRouteDetailViewModel(routeId))
) {
    val details by viewModel.routeDetails.collectAsState()
    val exportMessage by viewModel.exportMessage.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    // Feedback visual con Snackbar cuando se completa la exportación
    LaunchedEffect(exportMessage) {
        exportMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearExportMessage()
        }
    }

    // Launcher del Storage Access Framework (SAF) para crear el archivo GPX sin permisos peligrosos
    val exportGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    viewModel.exportRouteToGpx(outputStream)
                } else {
                    viewModel.exportRouteToGpx(java.io.ByteArrayOutputStream()) // trigger standard message
                }
            } catch (e: Exception) {
                // Notificación en caso de error en el ContentResolver
            }
        }
    }

    val triggerGpxExport: () -> Unit = {
        details?.let { routeDetails ->
            // Limpiamos el nombre de la ruta para que sea un nombre de archivo seguro
            val safeFileName = routeDetails.route.name
                .replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ_-]"), "_")
                .trim('_')
                .ifBlank { "ruta_${routeDetails.route.id}" }
            exportGpxLauncher.launch("$safeFileName.gpx")
        }
    }

    // Estado para manejar la creación de un nuevo POI
    var showPoiDialog by remember { mutableStateOf(false) }
    var selectedGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }

    // Estado para visualizar un POI existente al tocar su marcador
    var selectedPoiForInspection by remember { mutableStateOf<Poi?>(null) }

    BackHandler {
        navController.popBackStack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(details?.route?.name ?: "Cargando ruta...") },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(
                        onClick = triggerGpxExport,
                        enabled = details != null,
                        modifier = Modifier.testTag("export_gpx_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar ruta GPX")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            details?.let { routeDetails ->
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            isTilesScaledToDpi = true
                            
                            // Iniciar hilos de carga de tiles
                            onResume()
                            controller.setZoom(16.0)

                            // Receptor de eventos para pulsación larga (añadir POI)
                            val mapEventsReceiver = object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                                override fun longPressHelper(p: GeoPoint?): Boolean {
                                    p?.let {
                                        selectedGeoPoint = it
                                        showPoiDialog = true
                                    }
                                    return true
                                }
                            }
                            overlays.add(MapEventsOverlay(mapEventsReceiver))
                            mapViewInstance = this
                        }
                    },
                    update = { mapView ->
                        while (mapView.overlays.size > 1) {
                            mapView.overlays.removeAt(1)
                        }

                        // 1. Dibujamos la polilínea del recorrido en color rojo/escarlata
                        if (routeDetails.points.isNotEmpty()) {
                            val geoPoints = routeDetails.points.map { GeoPoint(it.latitude, it.longitude) }
                            val polyline = Polyline().apply {
                                setPoints(geoPoints)
                                outlinePaint.color = Color(0xFFD32F2F).toArgb() // Rojo vivo
                                outlinePaint.strokeWidth = 14f
                            }
                            mapView.overlays.add(polyline)

                            // Centramos el mapa en el inicio del recorrido
                            mapView.controller.setCenter(geoPoints.first())
                        }

                        // 2. Dibujamos los marcadores de Puntos de Interés (POIs)
                        routeDetails.pois.forEach { poi ->
                            val marker = Marker(mapView).apply {
                                position = GeoPoint(poi.latitude, poi.longitude)
                                title = poi.title
                                snippet = poi.description
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                setOnMarkerClickListener { _, _ ->
                                    selectedPoiForInspection = poi
                                    true
                                }
                            }
                            mapView.overlays.add(marker)
                        }

                        mapView.invalidate()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("detail_map_view")
                )

                // Panel inferior flotante: Perfil de elevación, botón de exportación GPX y recordatorio didáctico
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Gráfica de perfil altimétrico en Canvas nativo
                    ElevationGraph(points = routeDetails.points)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Botón de acción principal y accesible para exportar la ruta a GPX
                    Button(
                        onClick = triggerGpxExport,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("export_gpx_bottom_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Exportar Ruta a GPX",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Banner instructivo para añadir POIs con pulsación larga
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 Mantén presionado en el mapa para añadir un Punto de Interés (POI)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    // Diálogo para crear un nuevo POI tras un Long Press
    if (showPoiDialog && selectedGeoPoint != null) {
        AddPoiDialog(
            onDismiss = { showPoiDialog = false },
            onSave = { title, desc, uri ->
                viewModel.addPoi(
                    latitude = selectedGeoPoint!!.latitude,
                    longitude = selectedGeoPoint!!.longitude,
                    title = title,
                    description = desc,
                    imageUri = uri
                )
                showPoiDialog = false
            }
        )
    }

    // Diálogo para inspeccionar un POI existente y ver su fotografía local
    selectedPoiForInspection?.let { poi ->
        AlertDialog(
            onDismissRequest = { selectedPoiForInspection = null },
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
                TextButton(onClick = { selectedPoiForInspection = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Manejo de ciclo de vida del MapView
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewInstance?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewInstance?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewInstance?.onPause()
            mapViewInstance?.onDetach()
        }
    }
}
