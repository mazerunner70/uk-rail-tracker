package com.ukrailtracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.ukrailtracker.app.ui.navigation.AppNavHost
import com.ukrailtracker.app.ui.theme.NeonBackground
import com.ukrailtracker.app.ui.theme.UkRailTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val barColor = NeonBackground.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(barColor),
            navigationBarStyle = SystemBarStyle.dark(barColor),
        )
        setContent {
            UkRailTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NeonBackground,
                ) {
                    AppNavHost()
                }
            }
        }
    }
}
