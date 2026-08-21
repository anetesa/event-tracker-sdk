package com.eventtracker.sdk.model

/**
 * A single event previously recorded via [com.eventtracker.sdk.EventTrackerSDK.track].
 */
data class TrackedEvent(
    val id: String,
    val name: String,
    val properties: Map<String, String>,
    val timestamp: Long,
)
