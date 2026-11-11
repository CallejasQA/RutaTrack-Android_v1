package com.example.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Route
import com.example.domain.usecase.DeleteRouteUseCase
import com.example.domain.usecase.GetAllRoutesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    getAllRoutesUseCase: GetAllRoutesUseCase,
    private val deleteRouteUseCase: DeleteRouteUseCase
) : ViewModel() {

    // Observamos la base de datos en tiempo real.
    // Si la lista cambia en Room, este StateFlow emitirá la nueva lista automáticamente.
    val routes: StateFlow<List<Route>> = getAllRoutesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteRoute(routeId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            deleteRouteUseCase(routeId)
        }
    }
}
