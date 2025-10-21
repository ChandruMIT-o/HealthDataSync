package com.samsung.health.mobile.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.samsung.health.mobile.presentation.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TheApp(viewModel)
        }
        processIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        intent?.getStringExtra("message")?.let { jsonString ->
            val measurementResults = HelpFunctions.decodeMessage(jsonString)
            viewModel.onNewDataReceived(measurementResults)
        }
    }
}

@Composable
fun TheApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    MainScreen(
        result = uiState.latestAveragedData,
        isSaving = uiState.isSaving
    )
}