package com.eventtracker.sdk.internal.util

/**
 * Seam over [System.currentTimeMillis] so time-dependent logic (day grouping, retention cutoffs)
 * is deterministic in tests.
 */
internal interface Clock {
    fun nowMillis(): Long
}

internal object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
