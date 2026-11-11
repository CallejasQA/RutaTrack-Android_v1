package com.example.domain.usecase

import com.example.domain.model.Poi
import com.example.domain.repository.RouteRepository

class AddPoiUseCase(
    private val repository: RouteRepository
) {
    suspend operator fun invoke(poi: Poi): Long {
        return repository.addPoi(poi)
    }
}
