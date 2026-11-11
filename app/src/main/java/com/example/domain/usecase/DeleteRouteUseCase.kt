package com.example.domain.usecase

import com.example.domain.repository.RouteRepository

class DeleteRouteUseCase(
    private val repository: RouteRepository
) {
    suspend operator fun invoke(routeId: Long) {
        repository.deleteRoute(routeId)
    }
}
