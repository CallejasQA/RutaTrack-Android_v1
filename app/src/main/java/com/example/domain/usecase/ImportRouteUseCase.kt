package com.example.domain.usecase

import com.example.domain.model.ImportedRouteSummary
import com.example.domain.repository.RouteRepository
import java.io.InputStream
import javax.inject.Inject

/**
 * Caso de Uso encargado de importar una ruta desde un flujo XML (GPX o KML)
 * hacia la base de datos local de forma atómica.
 */
class ImportRouteUseCase @Inject constructor(
    private val repository: RouteRepository
) {
    suspend operator fun invoke(
        inputStream: InputStream,
        fallbackName: String
    ): Result<ImportedRouteSummary> {
        return repository.importRoute(inputStream, fallbackName)
    }
}
