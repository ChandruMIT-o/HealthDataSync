package com.samsung.health.mobile.presentation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

// ▼▼▼ FIX: The UiState data class has been moved to ProcessingStateHolder.kt ▼▼▼

@HiltViewModel
class MainViewModel @Inject constructor(
    // ▼▼▼ FIX: Inject the state holder instead of the database ▼▼▼
    stateHolder: ProcessingStateHolder
) : ViewModel() {

    // ▼▼▼ FIX: Simply expose the state from the holder ▼▼▼
    val uiState: StateFlow<UiState> = stateHolder.state
    // ▲▲▲ END FIX ▲▲▲

    // ▼▼▼ FIX: All other logic is removed. ▼▼▼
    // The ViewModel is no longer responsible for buffering, processing, or saving.
    // The ProcessingService handles all of this in the background.
    // ▲▲▲ END FIX ▲▲▲
}