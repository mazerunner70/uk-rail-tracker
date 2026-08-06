package com.ukrailtracker.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ukrailtracker.app.ui.components.AppScreen
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonMagenta
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonOutline
import com.ukrailtracker.app.ui.theme.NeonSurface
import java.time.DayOfWeek

@Composable
fun SettingsRoute(
    pendingFavouriteCrs: String?,
    pendingWindowStationCrs: String?,
    onConsumePendingFavourite: () -> Unit,
    onConsumePendingWindowStation: () -> Unit,
    onPickFavouriteStation: () -> Unit,
    onPickWindowStation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = context.appContainer()
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            stationRepository = container.stationRepository,
            favouritesStore = container.favouritesStore,
            commuteWindowStore = container.commuteWindowStore,
            appPreferencesStore = container.appPreferencesStore,
            homeRefreshScheduler = container.homeRefreshScheduler,
        ),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(pendingFavouriteCrs) {
        val crs = pendingFavouriteCrs ?: return@LaunchedEffect
        viewModel.addFavourite(crs)
        onConsumePendingFavourite()
    }
    LaunchedEffect(pendingWindowStationCrs) {
        val crs = pendingWindowStationCrs ?: return@LaunchedEffect
        viewModel.onEditorStationPicked(crs)
        onConsumePendingWindowStation()
    }

    AppScreen(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
    ) {
        SettingsContent(
            state = state,
            onRemoveFavourite = viewModel::removeFavourite,
            onAddFavourite = onPickFavouriteStation,
            onAddWindow = viewModel::startAddWindow,
            onEditWindow = viewModel::startEditWindow,
            onDeleteWindow = viewModel::deleteWindow,
            onBackgroundRefreshChange = viewModel::setBackgroundRefresh,
            modifier = Modifier.fillMaxSize(),
        )
    }

    state.editor?.let { editor ->
        CommuteEditorDialog(
            editor = editor,
            onDismiss = viewModel::dismissEditor,
            onPickStation = onPickWindowStation,
            onStartChange = viewModel::onEditorStartChange,
            onEndChange = viewModel::onEditorEndChange,
            onLabelChange = viewModel::onEditorLabelChange,
            onToggleDay = viewModel::toggleEditorDay,
            onSave = viewModel::saveEditor,
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onRemoveFavourite: (String) -> Unit,
    onAddFavourite: () -> Unit,
    onAddWindow: () -> Unit,
    onEditWindow: (String) -> Unit,
    onDeleteWindow: (String) -> Unit,
    onBackgroundRefreshChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(stringResource(R.string.settings_commute_title))
        }
        item {
            Text(
                text = stringResource(R.string.settings_commute_body),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
            )
        }
        items(state.windows, key = { it.id }) { window ->
            WindowRow(
                window = window,
                onClick = { onEditWindow(window.id) },
                onDelete = { onDeleteWindow(window.id) },
            )
        }
        item {
            TextButton(onClick = onAddWindow) {
                Icon(Icons.Default.Add, contentDescription = null, tint = NeonCyan)
                Text(
                    text = stringResource(R.string.settings_add_window),
                    color = NeonCyan,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.settings_favourites_title))
        }
        items(state.favourites, key = { it.crsCode }) { fav ->
            FavouriteRow(fav = fav, onRemove = { onRemoveFavourite(fav.crsCode) })
        }
        item {
            TextButton(onClick = onAddFavourite) {
                Icon(Icons.Default.Add, contentDescription = null, tint = NeonCyan)
                Text(
                    text = stringResource(R.string.settings_add_favourite),
                    color = NeonCyan,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.settings_refresh_title))
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeonSurface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_refresh_label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_refresh_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonMuted,
                    )
                }
                Switch(
                    checked = state.backgroundRefreshEnabled,
                    onCheckedChange = onBackgroundRefreshChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonCyan,
                        checkedTrackColor = NeonOutline,
                    ),
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.powered_by_national_rail),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = NeonCyan,
    )
}

@Composable
private fun FavouriteRow(fav: FavouriteRowUi, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = fav.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = fav.crsCode, style = MaterialTheme.typography.bodyMedium, color = NeonMuted)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.settings_remove), tint = NeonMagenta)
        }
    }
}

@Composable
private fun WindowRow(
    window: CommuteWindowRowUi,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = window.label.ifBlank { window.stationName },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${window.stationName} (${window.stationCrs})",
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
            )
            Text(
                text = "${window.daysLabel} · ${window.timeRangeLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.settings_remove), tint = NeonMagenta)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CommuteEditorDialog(
    editor: CommuteEditorUi,
    onDismiss: () -> Unit,
    onPickStation: () -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onSave: () -> Unit,
) {
    val days = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    )
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = NeonCyan,
        unfocusedBorderColor = NeonOutline,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        cursorColor = NeonCyan,
        focusedLabelColor = NeonCyan,
        unfocusedLabelColor = NeonMuted,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeonSurface,
        title = {
            Text(
                text = if (editor.id == null) {
                    stringResource(R.string.settings_add_window)
                } else {
                    stringResource(R.string.settings_edit_window)
                },
                color = NeonCyan,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onPickStation) {
                    Text(
                        text = if (editor.stationCrs.isBlank()) {
                            stringResource(R.string.settings_pick_station)
                        } else {
                            "${editor.stationName.ifBlank { editor.stationCrs }} (${editor.stationCrs})"
                        },
                        color = NeonCyan,
                    )
                }
                OutlinedTextField(
                    value = editor.label,
                    onValueChange = onLabelChange,
                    label = { Text(stringResource(R.string.settings_window_label)) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editor.startTime,
                        onValueChange = onStartChange,
                        label = { Text(stringResource(R.string.settings_start_time)) },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = editor.endTime,
                        onValueChange = onEndChange,
                        label = { Text(stringResource(R.string.settings_end_time)) },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { day ->
                        FilterChip(
                            selected = day in editor.selectedDays,
                            onClick = { onToggleDay(day) },
                            label = {
                                Text(day.name.take(3).lowercase().replaceFirstChar(Char::uppercase))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan,
                                labelColor = NeonMuted,
                            ),
                        )
                    }
                }
                editor.error?.let {
                    Text(text = it, color = NeonMagenta, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.settings_save), color = NeonCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel), color = NeonMuted)
            }
        },
    )
}
