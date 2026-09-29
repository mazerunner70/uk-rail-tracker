package com.ukrailtracker.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ukrailtracker.app.R
import com.ukrailtracker.app.domain.usecase.JourneyTimeUtils
import com.ukrailtracker.app.domain.usecase.ProgressPointUiModel
import com.ukrailtracker.app.ui.station.StatusKind
import com.ukrailtracker.app.ui.theme.NeonAmber
import com.ukrailtracker.app.ui.theme.NeonBackground
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonGreen
import com.ukrailtracker.app.ui.theme.NeonMagenta
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonSurface

@Composable
internal fun OnJourneyHome(
    journey: HomeBodyUi.OnJourney,
    onEndTrip: () -> Unit,
    onOpenDetail: () -> Unit,
    onConfirmBoarded: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val liveUnavailable = journey.serviceMissing ||
        (journey.errorMessage != null && journey.minutesRemaining == null && journey.preDeparture == null)
    val remainingPoints = remember(journey.points) {
        remainingJourneyPoints(journey.points)
    }
    val preDeparture = journey.preDeparture

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (journey.routeMismatch && !liveUnavailable) {
            RouteMismatchBanner(
                stationName = journey.mismatchStationName ?: "—",
                expectedNext = journey.expectedNextStopName,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeonSurface, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (liveUnavailable) {
                Text(
                    text = stringResource(R.string.home_on_train_countdown_unavailable),
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonAmber,
                    fontWeight = FontWeight.Bold,
                )
            } else if (preDeparture != null) {
                PreDepartureHero(preDeparture = preDeparture, journey = journey)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = journey.minutesRemaining?.let { "$it" } ?: "…",
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        lineHeight = 30.sp,
                    )
                    if (journey.minutesRemaining != null) {
                        Text(
                            text = stringResource(R.string.home_on_train_countdown_unit),
                            color = NeonCyan,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = stringResource(R.string.home_on_train_hero_sep),
                        color = NeonMuted,
                    )
                    Text(
                        text = journey.destinationName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    journey.delayMinutes?.takeIf { it > 0 }?.let { delay ->
                        Text(
                            text = stringResource(R.string.home_on_train_delay, delay),
                            color = NeonAmber,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    } ?: Text(
                        text = statusKindLabel(journey.statusKind),
                        color = statusKindColor(journey.statusKind),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (journey.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = NeonCyan,
                        )
                    }
                }
                val metaParts = buildList {
                    if (journey.scheduledDepartureLabel.isNotBlank()) {
                        add(
                            stringResource(
                                R.string.home_on_train_scheduled,
                                journey.scheduledDepartureLabel,
                            ),
                        )
                    }
                    journey.nextStopName?.let { next ->
                        add(stringResource(R.string.home_on_train_next_stop, next))
                    }
                    if (journey.stoppedAtStation) {
                        add(stringResource(R.string.home_on_train_refreshing_eta))
                    }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (liveUnavailable) {
                Text(
                    text = stringResource(R.string.home_on_train_service_missing),
                    color = NeonMagenta,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                journey.errorMessage?.let { err ->
                    Text(text = err, color = NeonMagenta, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeonSurface, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (preDeparture != null && !journey.showJourneyProgress) {
                PreDeparturePanel(
                    preDeparture = preDeparture,
                    onConfirmBoarded = onConfirmBoarded,
                )
            } else {
                JourneyProgressTimeline(
                    points = journey.points,
                    fractionComplete = journey.fractionComplete,
                    predictedDurationMinutes = journey.predictedDurationMinutes,
                )
                if (remainingPoints.isEmpty()) {
                    Text(
                        text = stringResource(
                            if (liveUnavailable) {
                                R.string.home_on_train_progress_unavailable
                            } else {
                                R.string.journey_calling_points_empty
                            },
                        ),
                        color = NeonMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    val viewportRows = remainingPoints.size.coerceAtMost(REMAINING_STOP_VIEWPORT)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(remainingStopListHeight(viewportRows)),
                        verticalArrangement = Arrangement.spacedBy(REMAINING_STOP_GAP),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        itemsIndexed(
                            items = remainingPoints,
                            key = { index, point ->
                                "${point.crs.orEmpty()}-${point.name}-$index"
                            },
                        ) { _, point ->
                            ProgressPointRow(point = point)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!liveUnavailable) {
                TextButton(onClick = onOpenDetail) {
                    Text(stringResource(R.string.home_on_train_open_detail), color = NeonCyan)
                }
            }
            TextButton(onClick = onEndTrip) {
                Text(stringResource(R.string.home_on_train_end), color = NeonMagenta)
            }
        }
    }
}

@Composable
private fun PreDepartureHero(
    preDeparture: PreDepartureUi,
    journey: HomeBodyUi.OnJourney,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (preDeparture.awaitingDeparture) {
            Text(
                text = stringResource(R.string.home_on_train_awaiting_departure_title),
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    R.string.home_on_train_awaiting_departure_body,
                    preDeparture.originStationName,
                    journey.destinationName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = stringResource(
                    R.string.home_on_train_waiting_at,
                    preDeparture.originStationName,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
            )
            preDeparture.approachingFromStopName?.let { from ->
                Text(
                    text = stringResource(R.string.home_on_train_approaching_from, from),
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonMuted,
                )
            }
        }
        if (journey.scheduledDepartureLabel.isNotBlank()) {
            Text(
                text = stringResource(
                    R.string.home_on_train_scheduled,
                    journey.scheduledDepartureLabel,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = NeonMuted,
            )
        }
        if (journey.refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = NeonCyan,
            )
        }
    }
}

@Composable
private fun PreDeparturePanel(
    preDeparture: PreDepartureUi,
    onConfirmBoarded: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (preDeparture.awaitingDeparture) {
            Text(
                text = stringResource(R.string.home_on_train_awaiting_movement),
                color = NeonMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_on_train_train_arrives_in),
                    color = NeonMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = preDeparture.minutesUntilTrainArrives?.let { "$it" } ?: "…",
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 30.sp,
                )
                if (preDeparture.minutesUntilTrainArrives != null) {
                    Text(
                        text = stringResource(R.string.home_on_train_countdown_unit),
                        color = NeonCyan,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (preDeparture.showBoardedButton) {
                Button(
                    onClick = onConfirmBoarded,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = NeonBackground,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.home_on_train_boarded),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteMismatchBanner(stationName: String, expectedNext: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonAmber, RoundedCornerShape(12.dp))
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.home_on_train_mismatch_title),
            color = NeonAmber,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (expectedNext.isNullOrBlank()) {
                stringResource(R.string.home_on_train_mismatch_body_no_next, stationName)
            } else {
                stringResource(R.string.home_on_train_mismatch_body, stationName, expectedNext)
            },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun JourneyProgressTimeline(
    points: List<ProgressPointUiModel>,
    fractionComplete: Float,
    predictedDurationMinutes: Int?,
    modifier: Modifier = Modifier,
) {
    val markers = points.mapNotNull { point ->
        val along = point.fractionAlong ?: return@mapNotNull null
        point to along.coerceIn(0f, 1f)
    }.sortedBy { it.second }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (markers.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TIMELINE_LABEL_ROW_HEIGHT),
            ) {
                val trackWidth = maxWidth
                val labelWidth = 28.dp
                val labeled = remember(markers, trackWidth) {
                    labeledMarkerIndexes(markers, trackWidth, labelWidth)
                }
                markers.forEachIndexed { index, (point, along) ->
                    if (index !in labeled) return@forEachIndexed
                    val crs = point.crs?.trim()?.takeIf { it.isNotEmpty() }
                        ?: point.name.take(3).uppercase()
                    val x = labelLeft(along, trackWidth, labelWidth)
                    Text(
                        text = crs,
                        color = progressPointAccent(point),
                        style = TextStyle(
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            fontWeight = if (
                                point.isCurrent || point.isNext ||
                                point.isOrigin || point.isDestination
                            ) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .offset(x = x)
                            .width(labelWidth),
                    )
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TIMELINE_TICK_HEIGHT),
            ) {
                val trackWidth = maxWidth
                val tickWidth = 2.dp
                markers.forEach { (point, along) ->
                    val x = ((trackWidth * along) - tickWidth / 2)
                        .coerceIn(0.dp, (trackWidth - tickWidth).coerceAtLeast(0.dp))
                    Box(
                        modifier = Modifier
                            .offset(x = x)
                            .width(tickWidth)
                            .height(TIMELINE_TICK_HEIGHT)
                            .background(progressPointAccent(point), RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { fractionComplete },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = NeonCyan,
            trackColor = NeonBackground,
        )
        if (predictedDurationMinutes != null && predictedDurationMinutes > 0) {
            Text(
                text = stringResource(R.string.home_on_train_duration, predictedDurationMinutes),
                color = NeonMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

private fun labeledMarkerIndexes(
    markers: List<Pair<ProgressPointUiModel, Float>>,
    trackWidth: Dp,
    labelWidth: Dp,
): Set<Int> {
    val placedX = ArrayList<Dp>(markers.size)
    val shown = LinkedHashSet<Int>()
    fun overlaps(x: Dp): Boolean =
        placedX.any { kotlin.math.abs(it.value - x.value) < labelWidth.value }

    fun tryPlace(index: Int) {
        if (index in shown) return
        val x = labelLeft(markers[index].second, trackWidth, labelWidth)
        if (overlaps(x)) return
        shown += index
        placedX += x
    }

    markers.forEachIndexed { index, (point, _) -> if (point.isOrigin) tryPlace(index) }
    markers.forEachIndexed { index, (point, _) -> if (point.isDestination) tryPlace(index) }
    markers.forEachIndexed { index, (point, _) -> if (point.isCurrent) tryPlace(index) }
    markers.forEachIndexed { index, (point, _) -> if (point.isNext) tryPlace(index) }
    markers.indices.forEach { tryPlace(it) }
    return shown
}

private fun labelLeft(
    along: Float,
    trackWidth: Dp,
    labelWidth: Dp,
): Dp =
    ((trackWidth * along) - labelWidth / 2)
        .coerceIn(0.dp, (trackWidth - labelWidth).coerceAtLeast(0.dp))

@Composable
private fun ProgressPointRow(point: ProgressPointUiModel) {
    val color = progressPointAccent(point)
    val expected = point.expectedLabel?.trim().orEmpty()
    val statusLabel = when {
        point.isCancelled || expected.equals("Cancelled", ignoreCase = true) -> "Cancelled"
        expected.equals("Delayed", ignoreCase = true) -> "Delayed"
        expected.isNotEmpty() &&
            !expected.equals("On time", ignoreCase = true) &&
            JourneyTimeUtils.parseTimeLabel(expected) == null -> expected
        else -> null
    }
    val variance = point.scheduleVarianceMinutes
    val arrivalClock = progressPointArrivalClock(point)
    val etaLabel = when {
        arrivalClock.isNotBlank() -> arrivalClock
        statusLabel != null -> statusLabel
        else -> point.scheduledLabel.orEmpty()
    }
    val varianceLabel = when {
        variance == null || variance == 0 -> null
        variance > 0 -> stringResource(R.string.home_on_train_stop_variance_late, variance)
        else -> stringResource(R.string.home_on_train_stop_variance_early, -variance)
    }
    val varianceColor = when {
        variance == null || variance == 0 -> NeonMuted
        variance > 0 -> NeonAmber
        else -> NeonGreen
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(REMAINING_STOP_ROW_HEIGHT)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = when {
                        point.isCurrent || point.isNext -> color
                        point.isPast -> NeonMuted
                        else -> NeonBackground
                    },
                    shape = CircleShape,
                )
                .border(1.dp, color, CircleShape),
        )
        Text(
            text = point.name,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (point.isCurrent || point.isNext || point.isDestination) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        point.crs?.takeIf { it.isNotBlank() }?.let { crs ->
            Text(
                text = crs,
                color = NeonMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        if (etaLabel.isNotBlank()) {
            Text(
                text = etaLabel,
                color = when {
                    point.isCancelled || statusLabel == "Cancelled" -> NeonMagenta
                    statusLabel == "Delayed" -> NeonAmber
                    point.actualLabel != null && (variance ?: 0) > 0 -> NeonAmber
                    point.isNext || point.isCurrent -> color
                    else -> NeonMuted
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (point.isNext || point.isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
        if (varianceLabel != null) {
            Text(
                text = varianceLabel,
                color = varianceColor,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private const val REMAINING_STOP_VIEWPORT = 4
private val TIMELINE_LABEL_ROW_HEIGHT = 18.dp
private val TIMELINE_TICK_HEIGHT = 8.dp
private val REMAINING_STOP_ROW_HEIGHT = 36.dp
private val REMAINING_STOP_GAP = 2.dp

private fun remainingStopListHeight(rows: Int): Dp {
    val gaps = REMAINING_STOP_GAP * (rows - 1).coerceAtLeast(0)
    return REMAINING_STOP_ROW_HEIGHT * rows + gaps + 4.dp
}

/** Last visited (current, else last past) through the favourite destination. */
internal fun remainingJourneyPoints(
    points: List<ProgressPointUiModel>,
): List<ProgressPointUiModel> {
    if (points.isEmpty()) return points
    val destIndex = points.indexOfLast { it.isDestination }.takeIf { it >= 0 }
        ?: points.lastIndex
    val startIndex = points.indexOfFirst { it.isCurrent }.takeIf { it >= 0 }
        ?: points.indexOfLast { it.isPast }.takeIf { it >= 0 }
        ?: 0
    val from = startIndex.coerceAtMost(destIndex)
    return points.subList(from, destIndex + 1)
}

private fun progressPointArrivalClock(point: ProgressPointUiModel): String {
    if (point.isCancelled) return ""
    val expected = point.expectedLabel?.trim().orEmpty()
    if (expected.equals("Cancelled", ignoreCase = true)) return ""

    JourneyTimeUtils.parseableClockLabel(point.actualLabel)?.let { return it }
    if (point.actualLabel?.equals("On time", ignoreCase = true) == true) {
        return point.scheduledLabel.orEmpty()
    }

    JourneyTimeUtils.parseableClockLabel(expected)?.let { return it }
    if (expected.equals("Delayed", ignoreCase = true)) {
        return point.scheduledLabel.orEmpty()
    }
    return point.scheduledLabel.orEmpty()
}

private fun progressPointAccent(point: ProgressPointUiModel): Color = when {
    point.isCancelled -> NeonMagenta
    point.isCurrent -> NeonCyan
    point.isNext -> NeonAmber
    point.isPast -> NeonMuted
    point.isDestination -> NeonGreen
    else -> NeonMuted
}

private fun statusKindLabel(kind: StatusKind): String = when (kind) {
    StatusKind.OnTime -> "On time"
    StatusKind.Delayed -> "Delayed"
    StatusKind.Cancelled -> "Cancelled"
    StatusKind.Other -> "Live"
}

private fun statusKindColor(kind: StatusKind): Color = when (kind) {
    StatusKind.OnTime -> NeonGreen
    StatusKind.Delayed -> NeonAmber
    StatusKind.Cancelled -> NeonMagenta
    StatusKind.Other -> NeonMuted
}
