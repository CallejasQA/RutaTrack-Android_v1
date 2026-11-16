package com.example.presentation.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.location.LocationManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.di.AppViewModelProvider
import com.example.presentation.map.components.SaveRouteDialog
import com.example.service.TrackingService
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
    var hasFineLocationPermission by remember {
        mutableStateOf(hasFineLocationPermission(context))
    }
    var hasLocationPermission by remember {
        mutableStateOf(hasAnyLocationPermission(context))
    }
    var hasCenteredOnInitialLocation by remember { mutableStateOf(false) }

    val livePolylineOverlay = remember {
        Polyline().apply {
            outlinePaint.color = Color(0xFF1E88E5).toArgb()
            outlinePaint.strokeWidth = 14f
        }
    }

    // 1. Launcher inicial de permisos de ubicación para activar MyLocationNewOverlay en dispositivo físico
    val mapLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasFineLocationPermission = fineGranted
        hasLocationPermission = fineGranted || coarseGranted

        if (fineGranted || coarseGranted) {
            locationOverlayInstance?.apply {
                enableMyLocation()
                enableFollowLocation()
            }
            mapViewInstance?.let { map ->
                centerOnLastKnownLocation(context, map)
                hasCenteredOnInitialLocation = true
                map.invalidate()
            }
        }
    }

    // Solicitamos permisos al abrir el mapa si aún no están concedidos
    LaunchedEffect(Unit) {
        if (!hasAnyLocationPermission(context)) {
            mapLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Reactivamos explícitamente MyLocationNewOverlay cuando el permiso o las instancias estén listas
    LaunchedEffect(
        hasFineLocationPermission,
        hasLocationPermission,
        locationOverlayInstance,
        mapViewInstance
    ) {
        val overlay = locationOverlayInstance ?: return@LaunchedEffect
        val mapView = mapViewInstance ?: return@LaunchedEffect
        if (hasLocationPermission) {
            overlay.enableMyLocation()
            overlay.enableFollowLocation()
            if (!hasCenteredOnInitialLocation) {
                if (centerOnLastKnownLocation(context, mapView)) {
                    hasCenteredOnInitialLocation = true
                }
            }
            mapView.invalidate()
        }
    }

    // Gestor de permisos al pulsar Iniciar grabación (ubicación + notificaciones Android 13+)
    val trackingPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isFineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val isCoarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasFineLocationPermission = isFineGranted || hasFineLocationPermission(context)
        hasLocationPermission = isFineGranted || isCoarseGranted

        if (isFineGranted || isCoarseGranted) {
            sendCommandToService(context, "ACTION_START_OR_RESUME_SERVICE")
            locationOverlayInstance?.apply {
                enableMyLocation()
                enableFollowLocation()
            }
            mapViewInstance?.let { map ->
                if (!hasCenteredOnInitialLocation) {
                    if (centerOnLastKnownLocation(context, map)) {
                        hasCenteredOnInitialLocation = true
                    }
                }
                map.invalidate()
            }
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
            // --- EL PUENTE A OSMDROID (OpenStreetMap Mapnik 100% Offline, Norte arriba 0f) ---
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        onResume()

                        controller.setZoom(16.0)
                        val initialGeo = getBestLastKnownLocation(ctx)?.let {
                            GeoPoint(it.latitude, it.longitude)
                        } ?: GeoPoint(40.4168, -3.7038)
                        controller.setCenter(initialGeo)

                        // 2. Proveedor híbrido (GPS + Red local/Wi-Fi) para respuesta inmediata en interiores y exteriores
                        val gpsProvider = GpsMyLocationProvider(ctx).apply {
                            clearLocationSources()
                            addLocationSource(LocationManager.GPS_PROVIDER)
                            addLocationSource(LocationManager.NETWORK_PROVIDER)
                            locationUpdateMinTime = 1500L
                            locationUpdateMinDistance = 2f
                        }

                        // 4. Puntero azul llamativo de alto contraste + círculo de precisión y centrado al primer fix
                        val myLocationOverlay = MyLocationNewOverlay(gpsProvider, this).apply {
                            val blueDot = createBlueLocationMarkerBitmap(ctx)
                            val blueArrow = createBlueDirectionArrowBitmap(ctx)
                            setDirectionArrow(blueDot, blueArrow)
                            setPersonAnchor(0.5f, 0.5f)
                            setDirectionAnchor(0.5f, 0.5f)
                            setDrawAccuracyEnabled(true)
                            runOnFirstFix {
                                val fix = myLocation
                                if (fix != null) {
                                    post {
                                        controller.animateTo(fix)
                                        invalidate()
                                    }
                                }
                            }
                            if (hasAnyLocationPermission(ctx)) {
                                enableMyLocation()
                                enableFollowLocation()
                            }
                        }

                        // 3. Jerarquía de capas protegida: MyLocationNewOverlay siempre al final
                        overlays.add(livePolylineOverlay)
                        overlays.add(myLocationOverlay)

                        locationOverlayInstance = myLocationOverlay
                        mapViewInstance = this
                    }
                },
                update = { mapView ->
                    // Protección de capas: nunca se usa overlays.clear() y garantizamos
                    // que myLocationOverlay permanezca al final (por encima de rutas y marcadores)
                    if (!mapView.overlays.contains(livePolylineOverlay)) {
                        mapView.overlays.add(0, livePolylineOverlay)
                    }

                    if (state.currentPath.isNotEmpty()) {
                        val liveGeoPoints = state.currentPath.map { GeoPoint(it.latitude, it.longitude) }
                        livePolylineOverlay.setPoints(liveGeoPoints)
                        mapView.controller.animateTo(liveGeoPoints.last())
                    } else {
                        livePolylineOverlay.setPoints(emptyList())
                    }

                    locationOverlayInstance?.let { myLocOverlay ->
                        if (!mapView.overlays.contains(myLocOverlay)) {
                            mapView.overlays.add(myLocOverlay)
                        } else if (mapView.overlays.lastOrNull() !== myLocOverlay) {
                            mapView.overlays.remove(myLocOverlay)
                            mapView.overlays.add(myLocOverlay)
                        }
                    }

                    mapView.invalidate()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("osmdroid_map_view")
            )

            // Banner de telemetría cuando se está grabando un recorrido
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

    // Modal de finalización de recorrido grabado
    if (state.showSaveDialog) {
        SaveRouteDialog(
            distanceMeters = state.liveDistanceMeters,
            onDismiss = { viewModel.onToggleSaveDialog(false) },
            onSave = { name -> viewModel.saveCurrentRoute(name) },
            onDiscard = { viewModel.discardCurrentRoute() }
        )
    }

    // 1. Vinculación estricta de MapView y MyLocationNewOverlay al ciclo de vida de Compose
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val fineNow = hasFineLocationPermission(context)
                    val anyNow = hasAnyLocationPermission(context)
                    hasFineLocationPermission = fineNow
                    hasLocationPermission = anyNow

                    mapViewInstance?.onResume()
                    if (anyNow) {
                        locationOverlayInstance?.enableMyLocation()
                        mapViewInstance?.invalidate()
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

private fun hasFineLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasAnyLocationPermission(context: Context): Boolean {
    val fine = hasFineLocationPermission(context)
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

/**
 * Consulta la última ubicación conocida en caché del dispositivo (GPS o Red local)
 * para centrar el mapa al instante mientras llega el primer fix en vivo.
 */
private fun getBestLastKnownLocation(context: Context): Location? {
    if (!hasAnyLocationPermission(context)) return null
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null

    val candidates = mutableListOf<Location>()
    try {
        if (hasFineLocationPermission(context)) {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                candidates.add(it)
            }
        }
        locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let {
            candidates.add(it)
        }
    } catch (_: SecurityException) {
        return null
    }
    return candidates.maxByOrNull { it.time }
}

private fun centerOnLastKnownLocation(context: Context, mapView: MapView): Boolean {
    val lastKnown = getBestLastKnownLocation(context) ?: return false
    mapView.controller.setZoom(16.0)
    mapView.controller.setCenter(GeoPoint(lastKnown.latitude, lastKnown.longitude))
    return true
}

// Función auxiliar para enviar comandos al Foreground Service
private fun sendCommandToService(context: Context, action: String) {
    val intent = Intent(context, TrackingService::class.java).apply {
        this.action = action
    }
    context.startService(intent)
}

/**
 * Genera un puntero circular azul eléctrico con halo translúcido y borde blanco
 * para destacar sobre cualquier fondo claro u oscuro de OpenStreetMap.
 */
private fun createBlueLocationMarkerBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
    val sizePx = (32f * density).toInt().coerceAtLeast(32)
    val center = sizePx / 2f

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Halo exterior azul suave
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4D1E88E5.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center * 0.92f, haloPaint)

    // Borde blanco nítido para máximo contraste
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center * 0.58f, borderPaint)

    // Núcleo azul eléctrico llamativo
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0066FF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, center * 0.42f, corePaint)

    return bitmap
}

/**
 * Genera una flecha direccional azul eléctrico con contorno blanco para navegación en movimiento.
 */
private fun createBlueDirectionArrowBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
    val sizePx = (36f * density).toInt().coerceAtLeast(36)
    val w = sizePx.toFloat()
    val h = sizePx.toFloat()

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val arrowPath = Path().apply {
        moveTo(w * 0.50f, h * 0.10f) // Punta superior
        lineTo(w * 0.85f, h * 0.86f) // Esquina inferior derecha
        lineTo(w * 0.50f, h * 0.68f) // Muesca central inferior
        lineTo(w * 0.15f, h * 0.86f) // Esquina inferior izquierda
        close()
    }

    // Contorno blanco grueso para contraste
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawPath(arrowPath, strokePaint)

    // Relleno azul eléctrico llamativo
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0066FF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawPath(arrowPath, fillPaint)

    return bitmap
}
