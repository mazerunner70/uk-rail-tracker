package com.ukrailtracker.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeContent(
    content: HomeUiState.Content,
    onStationClick: (String) -> Unit,
    onBannerClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onEndOnTrainTrip: () -> Unit,
    onOpenOnTrainDetail: () -> Unit,
    onSelectForPlatformWatch: (originCrs: String, destCrs: String, serviceId: String) -> Unit,
    onClearPlatformWatch: () -> Unit,
    onRefreshPlatformWatch: () -> Unit,
    onOpenWatchedService: () -> Unit,
    onConfirmBoarding: () -> Unit,
    onRejectAssumedBoarding: () -> Unit,
    onSelectBoardingCandidate: (serviceId: String) -> Unit,
    onDismissBoarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val walkUp = content.walkUp) {
        is WalkUpHomeUi.OnJourney -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (content.refreshing) {
                    HomeRefreshingRow()
                }
                OnJourneyHome(
                    journey = walkUp,
                    onEndTrip = onEndOnTrainTrip,
                    onOpenDetail = onOpenOnTrainDetail,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        is WalkUpHomeUi.BoardingPrompt -> {
            HomeScrollPane(modifier, content.refreshing) {
                item {
                    BoardingPromptHome(
                        prompt = walkUp,
                        onConfirm = onConfirmBoarding,
                        onRejectAssumed = onRejectAssumedBoarding,
                        onSelectCandidate = onSelectBoardingCandidate,
                        onDismiss = onDismissBoarding,
                    )
                }
            }
        }
        is WalkUpHomeUi.AtStation -> {
            HomeScrollPane(modifier, content.refreshing) {
                atStationHomeItems(
                    atStation = walkUp,
                    onOpenSettings = onOpenSettings,
                    onSelectForPlatformWatch = onSelectForPlatformWatch,
                    onClearPlatformWatch = onClearPlatformWatch,
                    onRefreshPlatformWatch = onRefreshPlatformWatch,
                    onOpenWatchedService = onOpenWatchedService,
                )
            }
        }
        else -> {
            HomeScrollPane(modifier, content.refreshing) {
                stationListHomeItems(
                    stations = content.stations,
                    onStationClick = onStationClick,
                    onBannerClick = onBannerClick,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun HomeScrollPane(
    modifier: Modifier,
    refreshing: Boolean,
    extraItems: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (refreshing) {
            item { HomeRefreshingRow() }
        }
        extraItems()
        item { HomeNationalRailFooter() }
    }
}
