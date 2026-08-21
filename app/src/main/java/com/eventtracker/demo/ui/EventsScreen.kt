package com.eventtracker.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventtracker.demo.R
import com.eventtracker.sdk.model.DayCount
import com.eventtracker.sdk.model.TrackedEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.US)

private fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timestampFormatter)

@Composable
fun EventsScreen(viewModel: EventsViewModel) {
    val state by viewModel.state.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item { StatisticsSection(state) }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
        item {
            ConfigurationSection(
                retentionDaysInput = state.retentionDaysInput,
                eventLimitInput = state.eventLimitInput,
                onRetentionDaysChanged = viewModel::onRetentionDaysInputChanged,
                onEventLimitChanged = viewModel::onEventLimitInputChanged,
                onApply = viewModel::onApplyConfigClicked,
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
        item {
            ActionButtonsSection(
                onTrackEvent = viewModel::onTrackEventClicked,
                onTrack100 = viewModel::onTrack100EventsClicked,
                onTrackWithProperties = viewModel::onTrackWithPropertiesClicked,
                onClearAll = viewModel::onClearAllClicked,
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
        item {
            Text(
                stringResource(R.string.events_header, state.events.size),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(state.events, key = { it.id }) { event ->
            EventRow(event)
        }
    }
}

@Composable
private fun StatisticsSection(state: EventsUiState) {
    Column {
        Text(stringResource(R.string.statistics_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.stat_total_events, state.totalCount))
        Text(stringResource(R.string.stat_today_events, state.todayCount))
        Text(
            stringResource(R.string.stat_by_day_header),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (state.byDay.isEmpty()) {
            Text(stringResource(R.string.stat_no_events))
        } else {
            state.byDay.forEach { dayCount: DayCount ->
                Text(stringResource(R.string.stat_day_count_row, dayCount.date, dayCount.count))
            }
        }
    }
}

@Composable
private fun ConfigurationSection(
    retentionDaysInput: String,
    eventLimitInput: String,
    onRetentionDaysChanged: (String) -> Unit,
    onEventLimitChanged: (String) -> Unit,
    onApply: () -> Unit,
) {
    Column {
        Text(stringResource(R.string.configuration_title), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = retentionDaysInput,
            onValueChange = onRetentionDaysChanged,
            label = { Text(stringResource(R.string.config_retention_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = eventLimitInput,
            onValueChange = onEventLimitChanged,
            label = { Text(stringResource(R.string.config_event_limit_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(onClick = onApply, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.config_apply_button))
        }
    }
}

@Composable
private fun ActionButtonsSection(
    onTrackEvent: () -> Unit,
    onTrack100: () -> Unit,
    onTrackWithProperties: () -> Unit,
    onClearAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onTrackEvent, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_track_event))
        }
        Button(onClick = onTrack100, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_track_100))
        }
        Button(onClick = onTrackWithProperties, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_track_with_properties))
        }
        OutlinedButton(onClick = onClearAll, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_clear_all))
        }
    }
}

@Composable
private fun EventRow(event: TrackedEvent) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTimestamp(event.timestamp))
            Text(event.name, fontWeight = FontWeight.Bold)
        }
        if (event.properties.isNotEmpty()) {
            Text(event.properties.entries.joinToString { (k, v) -> "$k=$v" })
        }
    }
}
