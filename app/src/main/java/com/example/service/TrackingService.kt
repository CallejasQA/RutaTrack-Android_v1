package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.RoutePoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class TrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val NOTIFICATION_CHANNEL_ID = "tracking_channel"
    private val NOTIFICATION_ID = 1

    // Definimos qué tan seguido queremos que el chip GPS nos dé coordenadas
    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L) // Cada 3 segundos
        .setMinUpdateIntervalMillis(2000L)
        .build()

    // El callback que se dispara cada vez que el GPS consigue una nueva coordenada
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            result.lastLocation?.let { location ->
                // Creamos nuestro modelo de Dominio
                val point = RoutePoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    timestamp = System.currentTimeMillis()
                )
                // Lo emitimos al canal de memoria (StateFlow)
                TrackingState.addPoint(point)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    // Este método es el que Android llama cuando hacemos startService(intent)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            "ACTION_START_OR_RESUME_SERVICE" -> {
                if (!TrackingState.isTracking.value) {
                    startForegroundService()
                }
            }
            "ACTION_STOP_SERVICE" -> {
                stopTrackingService()
            }
        }
        return START_STICKY // Si el sistema mata el servicio por falta crítica de memoria, que intente revivirlo
    }

    private fun startForegroundService() {
        TrackingState.setIsTracking(true)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Creamos la notificación que indica al usuario que el GPS está grabando
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setAutoCancel(false)
            .setOngoing(true) // No se puede deslizar para borrar
            .setSmallIcon(R.mipmap.ic_launcher) // Tu ícono
            .setContentTitle("Grabando ruta")
            .setContentText("Tu recorrido se está registrando en segundo plano.")
            .setContentIntent(pendingIntent)
            .build()

        // Requisito estricto de Android 14+ para declarar el tipo de servicio en el código
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // ¡Encendemos el chip GPS!
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // El usuario revocó el permiso de GPS repentinamente
        }
    }

    private fun stopTrackingService() {
        TrackingState.setIsTracking(false)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf() // Matamos el servicio amablemente
    }

    // Requisito de Android 8.0+ para poder mostrar notificaciones
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Tracking Service",
                NotificationManager.IMPORTANCE_LOW // Low para que no suene ni vibre cada 3 segundos
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // No usamos bindings en esta arquitectura
    }
}
