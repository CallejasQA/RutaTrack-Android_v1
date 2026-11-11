package com.example.domain.usecase

import com.example.domain.model.Route
import com.example.domain.repository.RouteRepository
import kotlinx.coroutines.flow.Flow

class GetAllRoutesUseCase(
    private val repository: RouteRepository
) {
    operator fun invoke(): Flow<List<Route>> {
        return repository.getAllRoutes()
    }
}
