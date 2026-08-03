package com.ukrailtracker.app.ui.station

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ukrailtracker.app.R
import com.ukrailtracker.app.appContainer
import com.ukrailtracker.app.domain.model.Station
import com.ukrailtracker.app.ui.theme.NeonBackground
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailRoute(
    crsCode: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = context.applicationContext.appContainer().stationRepository
    var station by remember { mutableStateOf<Station?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(crsCode) {
        loading = true
        station = withContext(Dispatchers.IO) {
            repository.ensureImported()
            repository.getByCrs(crsCode)
        }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeonBackground),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = station?.name ?: stringResource(R.string.station_detail_title),
                    color = NeonCyan,
                )
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
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = NeonBackground,
            ),
        )

        when {
            loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            }
            station == null -> {
                Text(
                    text = stringResource(R.string.station_not_found),
                    color = NeonMuted,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> StationDetailContent(station = station!!)
        }
    }
}

@Composable
private fun StationDetailContent(station: Station) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailBlock(label = stringResource(R.string.station_crs), value = station.crsCode)
        DetailBlock(
            label = stringResource(R.string.station_operator),
            value = listOf(station.operatorName, station.operatorCode)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "—" },
        )
        val address = buildList {
            addAll(station.addressLines)
            station.postcode?.let(::add)
        }.joinToString("\n")
        DetailBlock(
            label = stringResource(R.string.station_address),
            value = address.ifBlank { "—" },
        )
    }
}

@Composable
private fun DetailBlock(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = NeonMuted)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
