package com.example.focusastra

import android.os.Bundle
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.example.focusastra.ui.FocusDashboard
import com.example.focusastra.ui.theme.FocusAstraTheme

class MainActivity : ComponentActivity() {
    private var openTodosRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_OPEN_TODOS, false)) openTodosRequest++
        enableEdgeToEdge()
        updateSystemBars()
        setContent { FocusAstraTheme { FocusDashboard(application as FocusApplication, openTodosRequest) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_TODOS, false)) openTodosRequest++
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateSystemBars()
    }

    private fun updateSystemBars() {
        val light = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    companion object { const val EXTRA_OPEN_TODOS = "com.example.focusastra.OPEN_TODOS" }
}
