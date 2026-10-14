package com.example.focusastra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.focusastra.ui.FocusDashboard
import com.example.focusastra.ui.theme.FocusAstraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FocusAstraTheme { FocusDashboard(application as FocusApplication) } }
    }
}
