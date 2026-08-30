package com.ukrailtracker.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ukrailtracker.app.R
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonMagenta
import com.ukrailtracker.app.ui.theme.NeonMuted

@Composable
internal fun HomeLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = NeonCyan)
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.home_loading), color = NeonMuted)
    }
}

@Composable
internal fun EmptyHome(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            color = NeonCyan,
        )
        Spacer(modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = NeonMuted,
        )
        Spacer(modifier.height(16.dp))
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.home_open_settings), color = NeonCyan)
        }
    }
}

@Composable
internal fun HomeError(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        color = NeonMagenta,
        modifier = modifier.padding(24.dp),
    )
}

@Composable
internal fun HomeRefreshingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = NeonCyan,
        )
        Text(stringResource(R.string.home_refreshing), color = NeonMuted)
    }
}

@Composable
internal fun HomeNationalRailFooter() {
    Text(
        text = stringResource(R.string.powered_by_national_rail),
        style = MaterialTheme.typography.bodyMedium,
        color = NeonMuted,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )
}
