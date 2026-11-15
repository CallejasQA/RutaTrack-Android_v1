package com.example.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.ImportedRouteSummary
import com.example.domain.model.Route
import com.example.domain.usecase.DeleteRouteUseCase
import com.example.domain.usecase.GetAllRoutesUseCase
import com.example.domain.usecase.ImportRouteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

/**
 * Estados de la interfaz para el flujo de importación de archivos GPX/KML.
 */
sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Loading : ImportUiState
    data class Success(val summary: ImportedRouteSummary) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

class HistoryViewModel @Inject constructor(
    getAllRoutesUseCase: GetAllRoutesUseCase,
    private val deleteRouteUseCase: DeleteRouteUseCase,
    private val importRouteUseCase: ImportRouteUseCase
) : ViewModel() {

    // Observamos la base de datos en tiempo real.
    // Si la lista cambia en Room, este StateFlow emitirá la nueva lista automáticamente.
    val routes: StateFlow<List<Route>> = getAllRoutesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    fun deleteRoute(routeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            deleteRouteUseCase(routeId)
        }
    }

    /**
     * Procesa e importa de manera atómica un archivo GPX o KML desde su [InputStream].
     * Cierra el flujo automáticamente al finalizar.
     */
    fun importRoute(inputStream: InputStream, fileName: String) {
        _importState.value = ImportUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                inputStream.use { stream ->
                    val result = importRouteUseCase(stream, fileName)
                    result.onSuccess { summary ->
                        _importState.value = ImportUiState.Success(summary)
                    }.onFailure { error ->
                        _importState.value = ImportUiState.Error(
                            error.message ?: "No se pudo importar la ruta. Verifica que el archivo GPX/KML sea válido."
                        )
                    }
                }
            } catch (e: Exception) {
                _importState.value = ImportUiState.Error(
                    e.localizedMessage ?: "Error inesperado leyendo el archivo."
                )
            }
        }
    }

    fun onFileValidationError(message: String) {
        _importState.value = ImportUiState.Error(message)
    }

    fun resetImportState() {
        _importState.value = ImportUiState.Idle
    }
}
