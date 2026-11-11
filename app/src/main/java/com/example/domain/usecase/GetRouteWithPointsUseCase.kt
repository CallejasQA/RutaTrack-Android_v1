package com.example.domain.usecase

import com.example.domain.model.RouteWithDetails
import com.example.domain.repository.RouteRepository
import kotlinx.coroutines.flow.Flow

class GetRouteWithPointsUseCase(
    private val repository: RouteRepository
) {
    operator fun invoke(routeId: Long): Flow<RouteWithDetails> {
        return repository.getRouteWithDetails(routeId)
    }
}
