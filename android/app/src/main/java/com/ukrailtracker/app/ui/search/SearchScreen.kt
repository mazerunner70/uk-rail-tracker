package com.ukrailtracker.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ukrailtracker.app.R
import com.ukrailtracker.app.appContainer
import com.ukrailtracker.app.ui.theme.NeonBackground
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonOutline
import com.ukrailtracker.app.ui.theme.NeonSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchRoute(
    onBack: () -> Unit,
    onStationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer()
    val viewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.factory(
            stationRepository = container.stationRepository,
            recentSearchStore = container.recentSearchStore,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeonBackground),
    ) {
        TopAppBar(
            title = {
                Text(text = stringResource(R.string.search_title), color = NeonCyan)
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = NeonCyan,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = NeonBackground),
        )
        SearchScreen(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onStationClick = { crs ->
                viewModel.onStationOpened(crs)
                onStationClick(crs)
            },
        )
    }
}

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onStationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan)
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = NeonOutline,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = NeonCyan,
                focusedPlaceholderColor = NeonMuted,
                unfocusedPlaceholderColor = NeonMuted,
            ),
        )

        when {
            state.importing -> {
                SearchLoadingRow(label = stringResource(R.string.nearby_importing))
            }
            state.query.isBlank() -> {
                if (state.recent.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_empty_prompt),
                        color = NeonMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.search_recent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonMuted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.recent, key = { it.crsCode }) { row ->
                            SearchResultRow(row, onClick = { onStationClick(row.crsCode) })
                        }
                    }
                }
            }
            state.loading -> {
                SearchLoadingRow(label = stringResource(R.string.search_loading))
            }
            state.results.isEmpty() -> {
                Text(
                    text = stringResource(R.string.search_no_results),
                    color = NeonMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.results, key = { it.crsCode }) { row ->
                        SearchResultRow(row, onClick = { onStationClick(row.crsCode) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchLoadingRow(label: String) {
    Row(
        modifier = Modifier.padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = NeonCyan,
        )
        Text(text = label, color = NeonMuted)
    }
}

@Composable
private fun SearchResultRow(
    row: SearchStationRowUi,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = listOf(row.crsCode, row.operator)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = NeonMuted,
        )
    }
}
