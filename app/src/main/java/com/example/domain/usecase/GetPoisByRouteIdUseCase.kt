package com.example.domain.usecase

import com.example.domain.model.Poi
import com.example.domain.repository.RouteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPoisByRouteIdUseCase @Inject constructor(
    private val repository: RouteRepository
) {
    operator fun invoke(routeId: Long): Flow<List<Poi>> {
        return repository.getPoisByRouteId(routeId)
    }
}
