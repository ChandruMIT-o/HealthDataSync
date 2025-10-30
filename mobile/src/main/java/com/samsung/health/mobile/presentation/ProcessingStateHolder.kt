package com.samsung.health.mobile.presentation

import com.samsung.health.data.TrackedData
import kotlinx.coroutines.flow.MutableSharedFlow // --- NEW IMPORT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow // --- NEW IMPORT
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class UiState(
    val latestAveragedData: TrackedData? = null,
    val isSaving: Boolean = false
)

@Singleton
class ProcessingStateHolder @Inject constructor() {

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    // ▼▼▼ NEW "INBOX" FOR DATA ▼▼▼
    private val _dataInbox = MutableSharedFlow<String>()
    val dataInbox = _dataInbox.asSharedFlow()

    /**
     * Called by the DataListenerService to post new raw JSON data.
     * This is a suspend function, so the caller must use a coroutine.
     */
    suspend fun postData(jsonString: String) {
        _dataInbox.emit(jsonString)
    }
    // ▲▲▲ END NEW "INBOX" ▲▲▲

    fun setLatestAveragedData(data: TrackedData) {
        _state.update { it.copy(latestAveragedData = data) }
    }

    fun setSavingState(isSaving: Boolean) {
        _state.update { it.copy(isSaving = isSaving) }
    }
}