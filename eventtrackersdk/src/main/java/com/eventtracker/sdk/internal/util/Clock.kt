package com.eventtracker.sdk.internal.util

/**
 * Шов над [System.currentTimeMillis], чтобы логика, зависящая от времени (группировка по дням,
 * границы retention), была детерминированной в тестах.
 */
internal interface Clock {
    fun nowMillis(): Long
}

internal object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
