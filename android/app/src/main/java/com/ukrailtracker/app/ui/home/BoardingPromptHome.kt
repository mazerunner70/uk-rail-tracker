package com.ukrailtracker.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ukrailtracker.app.R
import com.ukrailtracker.app.ui.theme.NeonAmber
import com.ukrailtracker.app.ui.theme.NeonBackground
import com.ukrailtracker.app.ui.theme.NeonCyan
import com.ukrailtracker.app.ui.theme.NeonMuted
import com.ukrailtracker.app.ui.theme.NeonSurface

@Composable
internal fun BoardingPromptHome(
    prompt: HomeBodyUi.BoardingPrompt,
    onConfirm: () -> Unit,
    onRejectAssumed: () -> Unit,
    onSelectCandidate: (serviceId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonSurface, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_boarding_heading),
            style = MaterialTheme.typography.bodyMedium,
            color = NeonMuted,
        )
        Text(
            text = stringResource(
                R.string.home_boarding_question,
                prompt.assumed.scheduledDepartureLabel,
                prompt.assumed.destinationName,
            ),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        BoardingCandidateMeta(prompt.assumed)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.home_boarding_confirm), color = NeonCyan)
            }
            TextButton(onClick = onRejectAssumed) {
                Text(stringResource(R.string.home_boarding_not_this), color = NeonAmber)
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.home_boarding_dismiss), color = NeonMuted)
            }
        }
        if (prompt.showPicker) {
            Text(
                text = stringResource(R.string.home_boarding_picker_heading),
                style = MaterialTheme.typography.bodyMedium,
                color = NeonMuted,
            )
            if (prompt.alternatives.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_boarding_no_alternatives),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonMuted,
                )
            } else {
                prompt.alternatives.forEach { candidate ->
                    BoardingCandidateRow(
                        candidate = candidate,
                        onClick = { onSelectCandidate(candidate.serviceId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardingCandidateMeta(candidate: BoardingCandidateUi) {
    val meta = when {
        candidate.operatorName.isNotBlank() && !candidate.platform.isNullOrBlank() ->
            stringResource(
                R.string.home_boarding_operator_platform,
                candidate.operatorName,
                candidate.platform,
            )
        candidate.operatorName.isNotBlank() ->
            stringResource(R.string.home_boarding_operator_only, candidate.operatorName)
        !candidate.platform.isNullOrBlank() ->
            stringResource(R.string.home_walkup_platform, candidate.platform)
        else -> stringResource(R.string.home_walkup_platform_unknown)
    }
    Text(
        text = meta,
        style = MaterialTheme.typography.bodyMedium,
        color = NeonMuted,
    )
}

@Composable
private fun BoardingCandidateRow(
    candidate: BoardingCandidateUi,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonBackground, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = candidate.scheduledDepartureLabel,
            color = NeonCyan,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.destinationName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            BoardingCandidateMeta(candidate)
        }
    }
}
