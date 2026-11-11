package com.example.data.local.mapper

import com.example.data.local.entity.PoiEntity
import com.example.data.local.entity.RouteEntity
import com.example.data.local.entity.RoutePointEntity
import com.example.domain.model.Poi
import com.example.domain.model.Route
import com.example.domain.model.RoutePoint

// --- Mappers para Route ---
fun RouteEntity.toDomain(): Route = Route(
    id = id,
    name = name,
    timestamp = timestamp,
    distanceMeters = distanceMeters,
    durationMillis = durationMillis
)

fun Route.toEntity(): RouteEntity = RouteEntity(
    id = id, // Si es 0, Room autogenerará el ID al insertar
    name = name,
    timestamp = timestamp,
    distanceMeters = distanceMeters,
    durationMillis = durationMillis
)

// --- Mappers para RoutePoint ---
fun RoutePointEntity.toDomain(): RoutePoint = RoutePoint(
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    timestamp = timestamp
)

// Inyectamos el routeId porque el Dominio no lo maneja, pero la DB lo requiere
fun RoutePoint.toEntity(routeId: Long): RoutePointEntity = RoutePointEntity(
    routeId = routeId,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    timestamp = timestamp
)

// --- Mappers para Poi ---
fun PoiEntity.toDomain(): Poi = Poi(
    id = id,
    routeId = routeId,
    latitude = latitude,
    longitude = longitude,
    title = title,
    description = description,
    imageUri = imageUri
)

fun Poi.toEntity(): PoiEntity = PoiEntity(
    id = id,
    routeId = routeId,
    latitude = latitude,
    longitude = longitude,
    title = title,
    description = description,
    imageUri = imageUri
)
