package com.example.domain.usecase

import com.example.domain.repository.RouteRepository
import javax.inject.Inject

class DeletePoiUseCase @Inject constructor(
    private val repository: RouteRepository
) {
    suspend operator fun invoke(poiId: Long) {
        repository.deletePoiById(poiId)
    }
}
