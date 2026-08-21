package com.eventtracker.demo.data

import com.eventtracker.sdk.EventTrackerSDK
import com.eventtracker.sdk.model.EventStatistics
import com.eventtracker.sdk.model.TrackedEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Thin wrapper around the [EventTrackerSDK] singleton so [com.eventtracker.demo.ui.EventsViewModel]
 * depends on an interface instead of a static object — the ViewModel can then be unit-tested
 * with a fake, with no Room/WorkManager/real SDK involved.
 */
interface SdkGateway {
    fun track(name: String, properties: Map<String, String> = emptyMap())
    fun recentEvents(limit: Int): Flow<List<TrackedEvent>>
    suspend fun statistics(dayWindow: Int = 7): EventStatistics
    suspend fun clearAllEvents()
    fun updateConfig(retentionDays: Int? = null, maxEventCount: Int? = null)
}

class SdkGatewayImpl @Inject constructor() : SdkGateway {
    override fun track(name: String, properties: Map<String, String>) =
        EventTrackerSDK.track(name, properties)

    override fun recentEvents(limit: Int): Flow<List<TrackedEvent>> =
        EventTrackerSDK.getRecentEvents(limit)

    override suspend fun statistics(dayWindow: Int): EventStatistics =
        EventTrackerSDK.getStatistics(dayWindow)

    override suspend fun clearAllEvents() = EventTrackerSDK.clearAllEvents()

    override fun updateConfig(retentionDays: Int?, maxEventCount: Int?) =
        EventTrackerSDK.updateConfig(retentionDays, maxEventCount)
}
