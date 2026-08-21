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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                isStressTestRunning = state.isStressTestRunning,
                onTrackEvent = viewModel::onTrackEventClicked,
                onTrack100 = viewModel::onTrack100EventsClicked,
                onTrackWithProperties = viewModel::onTrackWithPropertiesClicked,
                onClearAll = viewModel::onClearAllClicked,
            )
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
        item { Text("Events (${state.events.size})", style = MaterialTheme.typography.titleMedium) }
        items(state.events, key = { it.id }) { event ->
            EventRow(event)
        }
    }
}

@Composable
private fun StatisticsSection(state: EventsUiState) {
    Column {
        Text("Statistics", style = MaterialTheme.typography.titleLarge)
        Text("Total events tracked: ${state.totalCount}")
        Text("Events tracked today: ${state.todayCount}")
        Text("By day (last 7 days):", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        if (state.byDay.isEmpty()) {
            Text("No events tracked")
        } else {
            state.byDay.forEach { dayCount: DayCount ->
                Text("${dayCount.date} - ${dayCount.count}")
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
        Text("Configuration", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = retentionDaysInput,
            onValueChange = onRetentionDaysChanged,
            label = { Text("Retention period (days)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = eventLimitInput,
            onValueChange = onEventLimitChanged,
            label = { Text("Max events count") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(onClick = onApply, modifier = Modifier.padding(top = 8.dp)) {
            Text("Apply")
        }
    }
}

@Composable
private fun ActionButtonsSection(
    isStressTestRunning: Boolean,
    onTrackEvent: () -> Unit,
    onTrack100: () -> Unit,
    onTrackWithProperties: () -> Unit,
    onClearAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onTrackEvent, modifier = Modifier.fillMaxWidth()) {
            Text("Track Event")
        }
        Button(onClick = onTrack100, enabled = !isStressTestRunning, modifier = Modifier.fillMaxWidth()) {
            Text(if (isStressTestRunning) "Tracking 100 events..." else "Track 100 Events")
        }
        Button(onClick = onTrackWithProperties, modifier = Modifier.fillMaxWidth()) {
            Text("Track with Properties")
        }
        OutlinedButton(onClick = onClearAll, modifier = Modifier.fillMaxWidth()) {
            Text("Clear All Events")
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
