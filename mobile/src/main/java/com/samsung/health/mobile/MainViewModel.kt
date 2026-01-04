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
    private val storageManager: StorageManager
) : ViewModel() {

    val fileSize = storageManager.fileSize.map { size ->
        val mb = size / (1024.0 * 1024.0)
        "%.2f MB".format(mb)
    }.stateIn(viewModelScope, SharingStarted.Lazily, "0.00 MB")

    val lastUpdate = storageManager.lastUpdate.map { ts ->
        if (ts == 0L) "Waiting for data..."
        else "Last Packet: " + java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(ts))
    }.stateIn(viewModelScope, SharingStarted.Lazily, "Waiting...")

    // Connection Logic: If we received data < 1 min ago, we are "Connected"
    val isConnected = storageManager.lastUpdate.map { ts ->
        (System.currentTimeMillis() - ts) < 60_000
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus = _exportStatus.asStateFlow()

    fun exportData() {
        viewModelScope.launch {
            _exportStatus.value = "Exporting..."
            _exportStatus.value = storageManager.exportToDownloads()
            delay(3000)
            _exportStatus.value = null
        }
    }

    fun clearData() {
        storageManager.clearData()
    }
}