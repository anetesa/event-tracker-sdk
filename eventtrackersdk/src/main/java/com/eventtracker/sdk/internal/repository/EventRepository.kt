package com.eventtracker.sdk.internal.repository

import com.eventtracker.sdk.model.EventStatistics
import com.eventtracker.sdk.model.TrackedEvent
import kotlinx.coroutines.flow.Flow

/**
 * Owns all event business rules (id/timestamp generation, the "seen" flag lifecycle, statistics
 * shaping). Kept as an interface — separate from [com.eventtracker.sdk.internal.db.EventDao],
 * which is pure SQL — so it can be substituted with a fake in tests without touching Room, and
 * so [com.eventtracker.sdk.EventTrackerSDK] stays a thin facade with no business logic of its
 * own (dependency inversion + single-responsibility).
 */
internal interface EventRepository {

    /** Inserts a new event row. Suspends only for the DB write; the caller decides threading. */
    suspend fun trackEvent(name: String, properties: Map<String, String> = emptyMap())

    /**
     * The live "event list" the UI observes. Every batch this [Flow] emits is marked seen as it
     * is emitted — see the class doc on `isSeen` in `EventEntity` for why that is the chosen
     * "seen" semantics.
     */
    fun observeRecentEvents(limit: Int): Flow<List<TrackedEvent>>

    /** [dayWindow] caps how many of the most recent distinct days appear in [EventStatistics.byDay]. */
    suspend fun getStatistics(dayWindow: Int = 7): EventStatistics

    /** Deletes only events the UI has already displayed (see `observeRecentEvents`). */
    suspend fun clearAllEvents()
}
