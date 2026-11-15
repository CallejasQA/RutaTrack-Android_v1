package com.example.presentation.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.di.AppViewModelProvider
import com.example.domain.model.Poi
import com.example.presentation.map.components.FollowRouteHudCard
import com.example.presentation.map.components.SaveRouteDialog
import com.example.service.TrackingService
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen(
    followRouteId: Long? = null,
    viewModel: MapViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onNavigateToHistory: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var locationOverlayInstance by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var renderedFollowRouteKey by remember { mutableStateOf<String?>(null) }
    var centeredFollowRouteId by remember { mutableStateOf<Long?>(null) }
    var selectedReferencePoi by remember { mutableStateOf<Poi?>(null) }

    var hasLocationPermission by remember {
        mutableStateOf(hasAnyLocationPermission(context))
    }

    // Overlays persistentes con jerarquía Z estricta:
    // 0: Huella de referencia (Naranja #FF6D00, grosor 12f)
    // 1: Polilínea de grabación en vivo (Azul #1E88E5, grosor 14f)
    // 2: Carpeta de marcadores de referencia (Inicio, Fin y POIs)
    // 3: MyLocationNewOverlay (añadido al final para quedar siempre por encima de las rutas)
    val referencePolylineOverlay = remember {
        Polyline().apply {
            outlinePaint.color = Color(0xFFFF6D00).toArgb()
            outlinePaint.strokeWidth = 12f
        }
    }
    val livePolylineOverlay = remember {
        Polyline().apply {
            outlinePaint.color = Color(0xFF1E88E5).toArgb()
            outlinePaint.strokeWidth = 14f
        }
    }
    val referenceMarkersFolder = remember { FolderOverlay() }

    // Cargamos la ruta de referencia si venimos desde Historial / Detalle con followRouteId
    LaunchedEffect(followRouteId) {
        if (followRouteId != null && followRouteId > 0L) {
            viewModel.loadReferenceRoute(followRouteId)
        }
    }

    // Permiso exclusivo para habilitar GPS en pantalla en modo "Seguir Huella" (sin iniciar servicio)
    val followPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            locationOverlayInstance?.enableMyLocation()
            if (state.followRouteMode.isCameraCenteredOnUser) {
                locationOverlayInstance?.enableFollowLocation()
            }
        }
    }

    // Si activamos Seguir Huella y aún no tenemos permiso GPS, lo solicitamos suavemente
    LaunchedEffect(state.followRouteMode.isActive) {
        if (state.followRouteMode.isActive && !hasAnyLocationPermission(context)) {
            followPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Gestor de permisos para iniciar el Foreground Service de grabación
    val trackingPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isFineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val isCoarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (isFineGranted || isCoarseGranted) {
            hasLocationPermission = true
            sendCommandToService(context, "ACTION_START_OR_RESUME_SERVICE")
            locationOverlayInstance?.enableMyLocation()
            if (state.followRouteMode.isCameraCenteredOnUser) {
                locationOverlayInstance?.enableFollowLocation()
            }
        }
    }

    // Sincronizamos el centrado automático de cámara con MyLocationNewOverlay
    LaunchedEffect(state.followRouteMode.isCameraCenteredOnUser, locationOverlayInstance) {
        val overlay = locationOverlayInstance ?: return@LaunchedEffect
        if (state.followRouteMode.isCameraCenteredOnUser) {
            overlay.enableFollowLocation()
            overlay.myLocation?.let { geoPoint ->
                mapViewInstance?.controller?.animateTo(geoPoint)
            }
        } else {
            overlay.disableFollowLocation()
        }
    }

    // Centramos una vez al inicio de la huella de referencia recién cargada
    LaunchedEffect(state.followRouteMode.referenceRoute?.id, state.followRouteMode.referencePoints.size, mapViewInstance) {
        val mapView = mapViewInstance ?: return@LaunchedEffect
        val refRoute = state.followRouteMode.referenceRoute
        val refPoints = state.followRouteMode.referencePoints
        if (refRoute != null && refPoints.isNotEmpty() && centeredFollowRouteId != refRoute.id) {
            centeredFollowRouteId = refRoute.id
            val startGeo = GeoPoint(refPoints.first().latitude, refPoints.first().longitude)
            // Si aún no hay fijación GPS o cámara no está anclada al usuario, enfocamos el inicio de la ruta
            if (locationOverlayInstance?.myLocation == null) {
                mapView.controller.setZoom(16.0)
                mapView.controller.animateTo(startGeo)
            }
        } else if (refRoute == null) {
            centeredFollowRouteId = null
        }
    }

    // Listener GPS 100% Offline (LocationManager.GPS_PROVIDER) independiente del Foreground Service
    DisposableEffect(lifecycleOwner, hasLocationPermission) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                viewModel.updateUserLocation(location.latitude, location.longitude)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        fun startOfflineGpsUpdates() {
            if (!hasAnyLocationPermission(context)) return
            try {
                val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (lastGps != null) {
                    viewModel.updateUserLocation(lastGps.latitude, lastGps.longitude)
                }
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1500L,
                        2f,
                        gpsListener
                    )
                }
            } catch (_: SecurityException) {
            }
        }

        fun stopOfflineGpsUpdates() {
            try {
                locationManager.removeUpdates(gpsListener)
            } catch (_: SecurityException) {
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            startOfflineGpsUpdates()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> startOfflineGpsUpdates()
                Lifecycle.Event.ON_PAUSE -> stopOfflineGpsUpdates()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopOfflineGpsUpdates()
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

                // Botón flotante para alternar el centrado automático de cámara (enableFollowLocation / disableFollowLocation)
                val isCentered = state.followRouteMode.isCameraCenteredOnUser
                FloatingActionButton(
                    onClick = {
                        viewModel.toggleCameraCenteredOnUser()
                        if (!isCentered) {
                            locationOverlayInstance?.enableFollowLocation()
                            locationOverlayInstance?.myLocation?.let { geoPoint ->
                                mapViewInstance?.controller?.animateTo(geoPoint)
                            }
                        } else {
                            locationOverlayInstance?.disableFollowLocation()
                        }
                    },
                    containerColor = if (isCentered) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (isCentered) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .testTag("center_location_button")
                ) {
                    Icon(
                        imageVector = if (isCentered) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                        contentDescription = if (isCentered) {
                            "Centrado automático activado"
                        } else {
                            "Activar centrado automático"
                        }
                    )
                }

                // Botón principal de Iniciar / Detener grabación (independiente de Seguir Huella)
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
                            trackingPermissionLauncher.launch(permissionsToRequest.toTypedArray())
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
                        onResume()

                        controller.setZoom(15.5)
                        controller.setCenter(GeoPoint(40.4168, -3.7038))

                        // Proveedor GPS 100% offline usando LocationManager.GPS_PROVIDER
                        val gpsProvider = GpsMyLocationProvider(ctx).apply {
                            clearLocationSources()
                            addLocationSource(LocationManager.GPS_PROVIDER)
                            locationUpdateMinTime = 1500L
                            locationUpdateMinDistance = 2f
                        }

                        val myLocationOverlay = MyLocationNewOverlay(gpsProvider, this).apply {
                            enableMyLocation()
                            if (state.followRouteMode.isCameraCenteredOnUser) {
                                enableFollowLocation()
                            }
                        }

                        // Orden Z estricto:
                        // 1) Polilínea de referencia (Naranja)
                        // 2) Polilínea de grabación en vivo (Azul)
                        // 3) Carpeta de marcadores (Inicio, Fin, POIs)
                        // 4) MyLocationNewOverlay SIEMPRE AL FINAL para dibujarse por encima de las rutas
                        overlays.add(referencePolylineOverlay)
                        overlays.add(livePolylineOverlay)
                        overlays.add(referenceMarkersFolder)
                        overlays.add(myLocationOverlay)

                        locationOverlayInstance = myLocationOverlay
                        mapViewInstance = this
                    }
                },
                update = { mapView ->
                    // A. Actualizar Huella de Referencia (Modo "Seguir Huella")
                    val followMode = state.followRouteMode
                    val newKey = if (followMode.isActive) {
                        "${followMode.referenceRoute?.id}_${followMode.referencePoints.size}_${followMode.referencePois.size}"
                    } else {
                        null
                    }

                    if (newKey != renderedFollowRouteKey) {
                        renderedFollowRouteKey = newKey
                        referenceMarkersFolder.items.clear()

                        if (followMode.isActive) {
                            val refGeoPoints = followMode.referencePoints.map {
                                GeoPoint(it.latitude, it.longitude)
                            }
                            referencePolylineOverlay.setPoints(refGeoPoints)

                            // Marcador de Inicio
                            val startMarker = Marker(mapView).apply {
                                position = refGeoPoints.first()
                                title = "Inicio: ${followMode.referenceRoute?.name.orEmpty()}"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            referenceMarkersFolder.add(startMarker)

                            // Marcador de Fin (si hay más de 1 punto)
                            if (refGeoPoints.size > 1) {
                                val endMarker = Marker(mapView).apply {
                                    position = refGeoPoints.last()
                                    title = "Fin de ruta"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                referenceMarkersFolder.add(endMarker)
                            }

                            // Marcadores de POIs de la huella
                            followMode.referencePois.forEach { poi ->
                                val poiMarker = Marker(mapView).apply {
                                    position = GeoPoint(poi.latitude, poi.longitude)
                                    title = poi.title
                                    snippet = poi.description
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    setOnMarkerClickListener { _, _ ->
                                        selectedReferencePoi = poi
                                        true
                                    }
                                }
                                referenceMarkersFolder.add(poiMarker)
                            }
                        } else {
                            referencePolylineOverlay.setPoints(emptyList())
                        }
                    }

                    // B. Actualizar Trazado en Vivo (Grabación activa)
                    if (state.currentPath.isNotEmpty()) {
                        val liveGeoPoints = state.currentPath.map { GeoPoint(it.latitude, it.longitude) }
                        livePolylineOverlay.setPoints(liveGeoPoints)
                        if (state.followRouteMode.isCameraCenteredOnUser) {
                            mapView.controller.animateTo(liveGeoPoints.last())
                        }
                    } else {
                        livePolylineOverlay.setPoints(emptyList())
                    }

                    mapView.invalidate()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("osmdroid_map_view")
            )

            // HUD superior apilado: Modo "Seguir Huella" y/o Telemetría de Grabación
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                if (state.followRouteMode.isActive) {
                    FollowRouteHudCard(
                        followMode = state.followRouteMode,
                        onExitClick = { viewModel.exitFollowRouteMode() }
                    )
                }

                if (state.isTracking) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier.testTag("telemetry_banner")
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
    }

    // Modal para inspeccionar un POI de la huella de referencia
    selectedReferencePoi?.let { poi ->
        AlertDialog(
            onDismissRequest = { selectedReferencePoi = null },
            title = { Text(text = poi.title, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    if (poi.description.isNotBlank()) {
                        Text(text = poi.description, style = MaterialTheme.typography.bodyMedium)
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
                TextButton(onClick = { selectedReferencePoi = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Modal de finalización de recorrido grabado
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
                    if (hasAnyLocationPermission(context)) {
                        locationOverlayInstance?.enableMyLocation()
                    }
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

private fun hasAnyLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

// Función auxiliar para enviar comandos al Foreground Service
private fun sendCommandToService(context: Context, action: String) {
    val intent = Intent(context, TrackingService::class.java).apply {
        this.action = action
    }
    context.startService(intent)
}
