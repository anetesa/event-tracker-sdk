package com.eventtracker.sdk.internal.util

/** Deterministic [Clock] fake — tests advance time explicitly instead of relying on wall time. */
internal class FakeClock(private var millis: Long) : Clock {
    override fun nowMillis(): Long = millis
    fun advanceByDays(days: Long) {
        millis += days * 24L * 60 * 60 * 1000
    }
    fun set(newMillis: Long) {
        millis = newMillis
    }
}

/** Deterministic [IdGenerator] fake — sequential ids instead of random UUIDs. */
internal class FakeIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String = "id-${++counter}"
}
