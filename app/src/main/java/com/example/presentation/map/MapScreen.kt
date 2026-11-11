package com.example.presentation.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.di.AppViewModelProvider
import com.example.presentation.map.components.SaveRouteDialog
import com.example.service.TrackingService
import com.google.android.gms.location.LocationServices
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onNavigateToHistory: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var locationOverlayInstance by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    val polylineOverlay = remember {
        Polyline().apply {
            outlinePaint.color = Color(0xFF1E88E5).toArgb() // Azul Material de alta legibilidad
            outlinePaint.strokeWidth = 14f
        }
    }

    // Centrar en ubicación conocida al iniciar
    LaunchedEffect(mapViewInstance) {
        val mapView = mapViewInstance ?: return@LaunchedEffect
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && state.currentPath.isEmpty()) {
                    val userPoint = GeoPoint(loc.latitude, loc.longitude)
                    mapView.controller.setCenter(userPoint)
                    mapView.controller.setZoom(16.5)
                }
            }
        } catch (_: SecurityException) {
        }
    }

    // Gestor de permisos en Compose
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isFineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val isCoarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (isFineGranted || isCoarseGranted) {
            sendCommandToService(context, "ACTION_START_OR_RESUME_SERVICE")
            locationOverlayInstance?.enableMyLocation()
            locationOverlayInstance?.enableFollowLocation()
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Botón para ir al Historial de Rutas
                FloatingActionButton(
                    onClick = onNavigateToHistory,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .testTag("history_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Historial de rutas")
                }

                // Botón para centrar en la ubicación actual del usuario
                FloatingActionButton(
                    onClick = {
                        locationOverlayInstance?.myLocation?.let { geoPoint ->
                            mapViewInstance?.controller?.animateTo(geoPoint)
                        } ?: run {
                            if (state.currentPath.isNotEmpty()) {
                                val last = state.currentPath.last()
                                mapViewInstance?.controller?.animateTo(GeoPoint(last.latitude, last.longitude))
                            } else {
                                try {
                                    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                                    fusedClient.lastLocation.addOnSuccessListener { loc ->
                                        if (loc != null) {
                                            mapViewInstance?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                                        }
                                    }
                                } catch (_: SecurityException) {
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .testTag("center_location_button")
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Mi ubicación")
                }

                // Botón principal de Iniciar / Detener grabación
                if (state.isTracking) {
                    FloatingActionButton(
                        onClick = {
                            sendCommandToService(context, "ACTION_STOP_SERVICE")
                            viewModel.onToggleSaveDialog(true)
                        },
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .testTag("stop_tracking_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Detener y guardar")
                    }
                } else {
                    FloatingActionButton(
                        onClick = {
                            val permissionsToRequest = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(permissionsToRequest.toTypedArray())
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .testTag("start_tracking_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Iniciar grabación")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // --- EL PUENTE A OSMDROID (OpenStreetMap Mapnik 100% Offline) ---
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true

                        // Activar hilos de descarga y renderizado de tiles de Osmdroid
                        onResume()

                        controller.setZoom(15.5)
                        controller.setCenter(GeoPoint(40.4168, -3.7038)) // Centro por defecto

                        // Capa de ubicación en tiempo real
                        val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                            enableMyLocation()
                        }
                        overlays.add(myLocationOverlay)
                        overlays.add(polylineOverlay)

                        locationOverlayInstance = myLocationOverlay
                        mapViewInstance = this
                    }
                },
                update = { mapView ->
                    if (state.currentPath.isNotEmpty()) {
                        val geoPoints = state.currentPath.map { GeoPoint(it.latitude, it.longitude) }
                        polylineOverlay.setPoints(geoPoints)
                        mapView.controller.animateTo(geoPoints.last())
                    } else {
                        polylineOverlay.setPoints(emptyList())
                    }
                    mapView.invalidate()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("osmdroid_map_view")
            )

            // Panel superior con telemetría en vivo
            if (state.isTracking) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .testTag("telemetry_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "🔴 Grabando",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "${String.format("%.1f", state.liveDistanceMeters)} m",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "(${state.currentPath.size} pts)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Modal de finalización de recorrido
    if (state.showSaveDialog) {
        SaveRouteDialog(
            distanceMeters = state.liveDistanceMeters,
            onDismiss = { viewModel.onToggleSaveDialog(false) },
            onSave = { name -> viewModel.saveCurrentRoute(name) },
            onDiscard = { viewModel.discardCurrentRoute() }
        )
    }

    // Manejo de ciclo de vida del MapView de Osmdroid
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapViewInstance?.onResume()
                    locationOverlayInstance?.enableMyLocation()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    locationOverlayInstance?.disableMyLocation()
                    mapViewInstance?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            locationOverlayInstance?.disableMyLocation()
            mapViewInstance?.onPause()
            mapViewInstance?.onDetach()
        }
    }
}

// Función auxiliar para enviar comandos al Foreground Service
private fun sendCommandToService(context: Context, action: String) {
    val intent = Intent(context, TrackingService::class.java).apply {
        this.action = action
    }
    context.startService(intent)
}
