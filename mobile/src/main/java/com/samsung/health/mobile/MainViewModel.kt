package com.samsung.health.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val storageManager: StorageManager,
    private val repository: HealthDataRepository // <--- INJECT REPO
) : ViewModel() {

    val fileSize = storageManager.fileSize.map { size ->
        "%.2f MB".format(size / (1024.0 * 1024.0))
    }.stateIn(viewModelScope, SharingStarted.Lazily, "0.00 MB")

    val lastUpdate = storageManager.lastUpdate.map { ts ->
        if (ts == 0L) "Waiting for data..."
        else "Last Packet: " + java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(ts))
    }.stateIn(viewModelScope, SharingStarted.Lazily, "Waiting...")

    // Check connection: considered connected if data received in last 70 seconds
    val isConnected = storageManager.lastUpdate.map { ts ->
        (System.currentTimeMillis() - ts) < 70_000
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus = _exportStatus.asStateFlow()

    // CHANGED: Observe the Repository instead of SimulatedDataProvider directly
    val healthData: StateFlow<HealthSnapshot> = repository.healthData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HealthSnapshot())

    fun exportData() {
        viewModelScope.launch {
            _exportStatus.value = "Exporting..."
            _exportStatus.value = storageManager.exportToDownloads()
            delay(3000)
            _exportStatus.value = null
        }
    }

    fun clearData() = storageManager.clearData()
}