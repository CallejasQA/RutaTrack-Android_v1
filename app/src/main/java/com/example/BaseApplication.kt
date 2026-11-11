package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer
import org.osmdroid.config.Configuration
import java.io.File

class BaseApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Configuración de almacenamiento interno seguro para caché de Osmdroid (Scoped Storage safe)
        val osmdroidBasePath = File(cacheDir, "osmdroid")
        val osmdroidTileCache = File(osmdroidBasePath, "tiles")
        if (!osmdroidTileCache.exists()) {
            osmdroidTileCache.mkdirs()
        }

        val config = Configuration.getInstance()
        config.load(this, getSharedPreferences("osmdroid_prefs", MODE_PRIVATE))
        config.osmdroidBasePath = osmdroidBasePath
        config.osmdroidTileCache = osmdroidTileCache
        // Política de OpenStreetMap: Obligatorio User-Agent específico para no recibir HTTP 403 Forbidden
        config.userAgentValue = "$packageName/1.0 (Android; RutaTrack)"

        // 2. Inicialización del contenedor de Inyección de Dependencias (DI)
        container = DefaultAppContainer(this)
    }
}
