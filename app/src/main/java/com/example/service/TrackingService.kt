package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.RoutePoint
import com.example.domain.util.LocationUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Foreground Service blindado para recorridos largos con pantalla apagada.
 * - Utiliza Partial WakeLock con timeout de seguridad (12 horas) liberado en onDestroy().
 * - Retorna START_STICKY y persiste puntos de respaldo en archivo local ante reinicios del SO.
 * - Actualiza en tiempo real la distancia y el tiempo transcurrido en la notificación.
 */
class TrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var notificationUpdateJob: Job? = null

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val draftFile by lazy { File(filesDir, DRAFT_FILE_NAME) }

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
        .setMinUpdateIntervalMillis(2000L)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            result.lastLocation?.let { location ->
                val point = RoutePoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    timestamp = System.currentTimeMillis()
                )
                TrackingState.addPoint(point)
                persistDraftPointAsync(point)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OR_RESUME -> {
                if (!TrackingState.isTracking.value) {
                    clearDraftFile()
                    startTrackingSession()
                }
            }
            ACTION_STOP -> {
                stopTrackingService()
            }
            null -> {
                // Reinicio automático por START_STICKY tras cierre de memoria del SO
                if (prefs.getBoolean(KEY_ACTIVE_TRACKING, false)) {
                    restoreDraftPointsIfNeeded()
                    startTrackingSession()
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTrackingSession() {
        prefs.edit().putBoolean(KEY_ACTIVE_TRACKING, true).apply()
        TrackingState.setIsTracking(true)

        // 1. Adquirimos Partial WakeLock para mantener el CPU despierto para lecturas GPS
        acquireWakeLock()

        // 2. Promovemos a Foreground Service inmediatamente con notificación rica en telemetría
        val initialNotification = buildTelemetryNotification(
            distanceMeters = LocationUtils.calculateDistance(TrackingState.routePoints.value),
            elapsedMillis = computeElapsedMillis(),
            pointsCount = TrackingState.routePoints.value.size
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        // 3. Iniciamos actualizaciones continuas de GPS
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            // El usuario revocó el permiso de ubicación en segundo plano/primer plano
        }

        // 4. Actualización periódica de distancia y tiempo en la notificación para reforzar prioridad
        startNotificationTicker()
    }

    private fun startNotificationTicker() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = serviceScope.launch {
            val notificationManager = getSystemService(NotificationManager::class.java)
            while (isActive && TrackingState.isTracking.value) {
                val points = TrackingState.routePoints.value
                val distance = LocationUtils.calculateDistance(points)
                val elapsed = computeElapsedMillis()
                val notification = buildTelemetryNotification(
                    distanceMeters = distance,
                    elapsedMillis = elapsed,
                    pointsCount = points.size
                )
                notificationManager?.notify(NOTIFICATION_ID, notification)
                delay(2000L)
            }
        }
    }

    private fun buildTelemetryNotification(
        distanceMeters: Float,
        elapsedMillis: Long,
        pointsCount: Int
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val formattedDistance = if (distanceMeters >= 1000f) {
            String.format(Locale.US, "%.2f km", distanceMeters / 1000f)
        } else {
            String.format(Locale.US, "%.0f m", distanceMeters)
        }
        val formattedTime = formatDuration(elapsedMillis)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.rutatrack_logo))
            .setContentTitle("RutaTrack • Grabando recorrido")
            .setContentText("$formattedDistance  •  $formattedTime  •  $pointsCount pts")
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun stopTrackingService() {
        prefs.edit().putBoolean(KEY_ACTIVE_TRACKING, false).apply()
        TrackingState.setIsTracking(false)
        notificationUpdateJob?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        releaseWakeLock()
        clearDraftFile()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        ).apply {
            setReferenceCounted(false)
            // Timeout de seguridad de 12 horas para evitar drenaje infinito ante fallos anómalos
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        } finally {
            wakeLock = null
        }
    }

    private fun persistDraftPointAsync(point: RoutePoint) {
        serviceScope.launch {
            try {
                draftFile.appendText(
                    "${point.latitude},${point.longitude},${point.altitude},${point.timestamp}\n"
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun restoreDraftPointsIfNeeded() {
        if (TrackingState.routePoints.value.isNotEmpty() || !draftFile.exists()) return
        try {
            val restored = draftFile.readLines().mapNotNull { line ->
                val parts = line.split(',')
                if (parts.size >= 4) {
                    val lat = parts[0].toDoubleOrNull()
                    val lon = parts[1].toDoubleOrNull()
                    val alt = parts[2].toDoubleOrNull() ?: 0.0
                    val ts = parts[3].toLongOrNull()
                    if (lat != null && lon != null && ts != null) {
                        RoutePoint(lat, lon, alt, ts)
                    } else null
                } else null
            }
            if (restored.isNotEmpty()) {
                TrackingState.restorePoints(restored, restored.first().timestamp)
            }
        } catch (_: Exception) {
        }
    }

    private fun clearDraftFile() {
        try {
            if (draftFile.exists()) draftFile.delete()
        } catch (_: Exception) {
        }
    }

    private fun computeElapsedMillis(): Long {
        val start = TrackingState.startTimeMillis.value
        return if (start > 0L) (System.currentTimeMillis() - start).coerceAtLeast(0L) else 0L
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        notificationUpdateJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_OR_RESUME = "ACTION_START_OR_RESUME_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_SERVICE"

        private const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        private const val NOTIFICATION_ID = 1
        private const val WAKELOCK_TAG = "RutaTrack::GpsTrackingWakeLock"
        private const val WAKELOCK_TIMEOUT_MS = 12L * 60L * 60L * 1000L // 12 horas
        private const val PREFS_NAME = "tracking_service_prefs"
        private const val KEY_ACTIVE_TRACKING = "active_tracking"
        private const val DRAFT_FILE_NAME = "active_route_draft.csv"
    }
}
