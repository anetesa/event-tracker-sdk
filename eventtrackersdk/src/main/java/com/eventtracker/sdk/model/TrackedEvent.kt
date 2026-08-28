package com.eventtracker.sdk.model

/**
 * Одно событие, ранее записанное через [com.eventtracker.sdk.EventTrackerSDK.track].
 */
data class TrackedEvent(
    val id: String,
    val name: String,
    val properties: Map<String, String>,
    val timestamp: Long,
)
