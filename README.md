# 🛰️ RutaTrack - Offline GPS Tracker para Android

RutaTrack es una aplicación nativa para Android desarrollada con **Kotlin** y **Jetpack Compose**, diseñada bajo los estándares de la industria con **Clean Architecture** y patrón de presentación **MVVM**. Permite grabar rutas geográficas en tiempo real mediante un servicio en primer plano (*Foreground Service*), consultar el historial de recorridos y añadir Puntos de Interés (POIs) con fotografías multimedia locales.

Todo el funcionamiento es **100% offline y sin costos**, sin depender de Google Maps SDK ni servicios de facturación en la nube.

---

## 📸 Características Principales

- **Mapas 100% Offline (Osmdroid):** Renderizado de cartografía OpenStreetMap (Mapnik) con almacenamiento en caché local seguro en el dispositivo.
- **Tracking GPS en Tiempo Real:** Grabación continua de coordenadas y trazado de polilíneas dinámicas mediante `FusedLocationProviderClient`.
- **Foreground Service Resistente:** Seguimiento ininterrumpido en segundo plano incluso con la pantalla bloqueada o la aplicación minimizada (compatible con Android 14+ `FOREGROUND_SERVICE_LOCATION`).
- **Persistencia Local con Room (SQLite):** Guardado de rutas finalizadas con métricas de distancia, duración y marcas de tiempo.
- **Puntos de Interés (POIs) Multimedia:** Posibilidad de fijar marcadores en el mapa con título, descripción y fotos seleccionadas mediante el Photo Picker nativo (`PickVisualMedia`) con permisos persistentes de URI (`takePersistableUriPermission`).
- **Historial de Rutas:** Consulta de trayectos anteriores con eliminación en cascada de puntos y POIs asociados.

---

## 🏛️ Arquitectura y Tecnologías

El proyecto sigue una separación estricta en tres capas:

```
app/src/main/java/com/example/
├── data/              # Implementación de repositorios, DAOs, Entidades Room y Base de Datos
├── domain/            # Modelos puros, interfaces de repositorio y Casos de Uso (Use Cases)
├── service/           # Foreground Service (TrackingService) y flujo de estado en RAM (TrackingState)
├── di/                # Inyección de Dependencias Manual (AppContainer y ViewModelProvider)
└── presentation/      # Pantallas en Jetpack Compose, ViewModels y Estado Inmutable (UDF)
    ├── map/           # Vista principal del mapa, polilínea en vivo y controles
    ├── history/       # Listado de recorridos guardados y métricas
    └── detail/        # Vista detallada de ruta con POIs interactivos
```

### Stack Técnico:
- **Lenguaje:** Kotlin 2.0+
- **UI:** Jetpack Compose (100% Declarativo con Material Design 3)
- **Persistencia:** Room Database 2.7+ (SQLite con KSP)
- **Ubicación:** Google Play Services Location (`FusedLocationProviderClient`)
- **Mapas:** Osmdroid 6.1+
- **Carga de Imágenes:** Coil Compose
- **Navegación:** Jetpack Navigation Compose
- **Asincronía & Flujos:** Kotlin Coroutines y `StateFlow` / `Flow` (UDF - Unidirectional Data Flow)
- **Testing:** JUnit 4 & Robolectric

---

## 🚀 Instalación y Compilación

1. Clonar el repositorio:
   ```bash
   git clone https://github.com/CallejasQA/RutaTrack-Android_v1.git
   ```
2. Abrir el proyecto en **Android Studio** (versión Ladybug / Hedgehog o superior).
3. Sincronizar Gradle (`Sync Project with Gradle Files`).
4. Ejecutar en un dispositivo físico o emulador con Android 8.0+ (API 26+).

---

## 📄 Licencia

Este proyecto está bajo la licencia MIT. Siéntete libre de utilizarlo con fines educativos o para tu portafolio personal.
