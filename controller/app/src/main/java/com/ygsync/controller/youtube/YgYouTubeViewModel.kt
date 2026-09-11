package com.ygsync.controller.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class YgYouTubeUiState(
    val query: String = "",
    val results: List<YgYouTubeResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    val suggestions: List<YgYouTubeResult> = emptyList(),
    val querySuggestions: List<String> = emptyList()
)

class YgYouTubeViewModel(
    private val repository: YgYouTubeRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(YgYouTubeUiState())

    val uiState: StateFlow<YgYouTubeUiState> =
        _uiState.asStateFlow()

    /*
     * Consultas variadas para que, apenas se abre la app
     * (sin buscar nada todavía), ya haya opciones de
     * canciones distintas para tocar. Pensado para uso en
     * bar: siempre hay algo para elegir sin escribir.
     */
    private val defaultSuggestionQueries = listOf(
        "musica popular",
        "reggaeton mix",
        "salsa clasica",
        "rock en español"
    )

    private var querySuggestJob: Job? = null

    init {
        loadSuggestions()
    }

    private fun loadSuggestions() {

        viewModelScope.launch {

            val query =
                defaultSuggestionQueries.random()

            repository
                .search(query)
                .onSuccess { results ->

                    _uiState.value =
                        _uiState.value.copy(
                            suggestions = results
                        )
                }
                .onFailure {
                    /*
                     * Si falla, simplemente no hay
                     * sugerencias por defecto; no es un
                     * error que deba mostrarse.
                     */
                }
        }
    }

    fun setQuery(value: String) {

        _uiState.value =
            _uiState.value.copy(
                query = value,
                error = null
            )

        querySuggestJob?.cancel()

        val cleanValue = value.trim()

        if (cleanValue.length < 2) {

            _uiState.value =
                _uiState.value.copy(
                    querySuggestions = emptyList()
                )

            return
        }

        /*
         * Esperamos un poquito antes de pedir sugerencias,
         * así no disparamos una petición por cada letra que
         * se escribe.
         */
        querySuggestJob = viewModelScope.launch {

            delay(250)

            val suggestions =
                repository.suggestQueries(cleanValue)

            _uiState.value =
                _uiState.value.copy(
                    querySuggestions = suggestions
                )
        }
    }

    fun selectSuggestion(suggestion: String) {

        querySuggestJob?.cancel()

        _uiState.value =
            _uiState.value.copy(
                query = suggestion,
                querySuggestions = emptyList()
            )

        search()
    }

    fun search() {

        val query =
            _uiState.value.query.trim()

        if (query.isEmpty()) {
            return
        }

        querySuggestJob?.cancel()

        viewModelScope.launch {

            _uiState.value =
                _uiState.value.copy(
                    isLoading = true,
                    error = null,
                    hasSearched = true,
                    querySuggestions = emptyList()
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

        querySuggestJob?.cancel()

        _uiState.value =
            YgYouTubeUiState(
                suggestions = _uiState.value.suggestions
            )
    }
}
