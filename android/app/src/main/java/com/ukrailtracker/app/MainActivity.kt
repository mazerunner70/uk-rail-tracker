package com.ukrailtracker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ukrailtracker.app.location.geofence.GeofenceIntents
import com.ukrailtracker.app.ui.navigation.AppNavHost
import com.ukrailtracker.app.ui.navigation.HomeLaunchAction
import com.ukrailtracker.app.ui.theme.NeonBackground
import com.ukrailtracker.app.ui.theme.UkRailTrackerTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val homeLaunchAction = MutableStateFlow<HomeLaunchAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeLaunchIntent(intent)
        val barColor = NeonBackground.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(barColor),
            navigationBarStyle = SystemBarStyle.dark(barColor),
        )
        setContent {
            val launchAction by homeLaunchAction.collectAsStateWithLifecycle()
            UkRailTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NeonBackground,
                ) {
                    AppNavHost(
                        homeLaunchAction = launchAction,
                        onHomeLaunchActionConsumed = { homeLaunchAction.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    private fun consumeLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val frequent = intent.action == GeofenceIntents.ACTION_OPEN_HOME_FREQUENT_CHECKS ||
            intent.getBooleanExtra(GeofenceIntents.EXTRA_FREQUENT_CHECKS, false)
        if (!frequent) return
        val crs = intent.getStringExtra(GeofenceIntents.EXTRA_STATION_CRS)
        homeLaunchAction.value = HomeLaunchAction.FrequentLocationChecks(hintCrs = crs)
        // Consume so process death / rotation does not re-fire elevated checks forever.
        intent.action = null
        intent.removeExtra(GeofenceIntents.EXTRA_FREQUENT_CHECKS)
    }
}
