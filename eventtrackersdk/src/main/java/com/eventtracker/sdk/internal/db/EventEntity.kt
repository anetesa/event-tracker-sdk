package com.eventtracker.sdk.internal.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted row for a tracked event.
 *
 * [createdDate] is precomputed at insert time (rather than derived in SQL) so day-grouping
 * queries are a plain `GROUP BY` with no per-row date math — matches the schema requirement.
 *
 * [isSeen] backs the "an event is not eligible for clearing until the UI has displayed it" rule:
 * it starts `false` and is flipped to `true` only once the row has been delivered to the UI's
 * observed event-list flow (see `EventRepositoryImpl`). `clearAllEvents()` only deletes rows
 * where this is `true`; the automatic retention/count cleanup ignores it by design (see
 * `CleanupWorker` doc).
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val name: String,
    val propertiesJson: String,
    val timestamp: Long,
    val createdDate: String,
    val isSeen: Boolean = false,
)
