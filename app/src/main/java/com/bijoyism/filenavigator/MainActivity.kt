package com.bijoyism.filenavigator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bijoyism.filenavigator.data.AppDataRepository
import com.bijoyism.filenavigator.data.AppSettings
import com.bijoyism.filenavigator.ui.FileBrowserViewModel
import com.bijoyism.filenavigator.ui.FileNavigatorApp
import com.bijoyism.filenavigator.ui.theme.FileNavigatorTheme

class MainActivity : ComponentActivity() {
    private var startRoute by mutableStateOf("home")

    private val fileBrowserViewModel: FileBrowserViewModel by viewModels {
        FileBrowserViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startRoute = intent.getStringExtra(EXTRA_START_ROUTE) ?: "home"
        setContent {
            val appData = AppDataRepository(applicationContext)
            val settings by appData.settings.collectAsState(initial = AppSettings())
            FileNavigatorTheme(darkTheme = settings.darkTheme) {
                FileNavigatorApp(fileBrowserViewModel, appData, settings, startRoute)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startRoute = intent.getStringExtra(EXTRA_START_ROUTE) ?: "home"
    }

    companion object {
        const val EXTRA_START_ROUTE = "start_route"
    }
}
