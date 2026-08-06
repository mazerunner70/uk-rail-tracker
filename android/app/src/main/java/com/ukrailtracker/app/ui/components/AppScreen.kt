package com.ukrailtracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ukrailtracker.app.R
import com.ukrailtracker.app.ui.theme.NeonBackground
import com.ukrailtracker.app.ui.theme.NeonCyan

/**
 * Shared page chrome for screens hosted inside [com.ukrailtracker.app.ui.navigation.AppNavHost]'s
 * Scaffold. Keeps the top app bar height/insets consistent across tabs and sub-pages.
 *
 * Status-bar padding is applied by the outer Scaffold — this top bar uses zero window insets
 * so titles align across Home / Nearby / Journeys / Settings.
 *
 * @param belowTopBar optional strip under the app bar (e.g. Home presence status) that stays
 * outside the scrollable content pane.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    belowTopBar: @Composable (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeonBackground),
    ) {
        TopAppBar(
            title = { Text(text = title, color = NeonCyan) },
            navigationIcon = {
                if (onBack != null) {
                    AppBarIconButton(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        onClick = onBack,
                    )
                }
            },
            actions = actions,
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonBackground),
        )
        belowTopBar?.invoke()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            content = content,
        )
    }
}

@Composable
fun AppBarIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = NeonCyan,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}
