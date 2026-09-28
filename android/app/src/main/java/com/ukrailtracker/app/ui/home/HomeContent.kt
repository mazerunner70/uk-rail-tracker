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
    content: HomeUiState.Ready,
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
    onConfirmBoarded: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (val body = content.body) {
        is HomeBodyUi.OnJourney -> {
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
                    journey = body,
                    onEndTrip = onEndOnTrainTrip,
                    onOpenDetail = onOpenOnTrainDetail,
                    onConfirmBoarded = onConfirmBoarded,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        is HomeBodyUi.BoardingPrompt -> {
            HomeScrollPane(modifier, content.refreshing) {
                item {
                    BoardingPromptHome(
                        prompt = body,
                        onConfirm = onConfirmBoarding,
                        onRejectAssumed = onRejectAssumedBoarding,
                        onSelectCandidate = onSelectBoardingCandidate,
                        onDismiss = onDismissBoarding,
                    )
                }
            }
        }
        is HomeBodyUi.AtStation -> {
            HomeScrollPane(modifier, content.refreshing) {
                atStationHomeItems(
                    atStation = body,
                    onOpenSettings = onOpenSettings,
                    onSelectForPlatformWatch = onSelectForPlatformWatch,
                    onClearPlatformWatch = onClearPlatformWatch,
                    onRefreshPlatformWatch = onRefreshPlatformWatch,
                    onOpenWatchedService = onOpenWatchedService,
                )
            }
        }
        is HomeBodyUi.StationList -> {
            HomeScrollPane(modifier, content.refreshing) {
                stationListHomeItems(
                    stations = body.stations,
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
