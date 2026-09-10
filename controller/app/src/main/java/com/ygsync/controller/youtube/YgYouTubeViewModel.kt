package com.ygsync.controller.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class YgYouTubeUiState(
    val query: String = "",
    val results: List<YgYouTubeResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false
)

class YgYouTubeViewModel(
    private val repository: YgYouTubeRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(YgYouTubeUiState())

    val uiState: StateFlow<YgYouTubeUiState> =
        _uiState.asStateFlow()

    fun setQuery(value: String) {
        _uiState.value =
            _uiState.value.copy(
                query = value,
                error = null
            )
    }

    fun search() {

        val query =
            _uiState.value.query.trim()

        if (query.isEmpty()) {
            return
        }

        viewModelScope.launch {

            _uiState.value =
                _uiState.value.copy(
                    isLoading = true,
                    error = null,
                    hasSearched = true
                )

            repository
                .search(query)
                .onSuccess { results ->

                    _uiState.value =
                        _uiState.value.copy(
                            results = results,
                            isLoading = false,
                            error = null
                        )
                }
                .onFailure { throwable ->

                    _uiState.value =
                        _uiState.value.copy(
                            results = emptyList(),
                            isLoading = false,
                            error =
                                throwable.message
                                    ?: "No se pudo realizar la búsqueda."
                        )
                }
        }
    }

    fun clearSearch() {

        _uiState.value =
            YgYouTubeUiState()
    }
}
