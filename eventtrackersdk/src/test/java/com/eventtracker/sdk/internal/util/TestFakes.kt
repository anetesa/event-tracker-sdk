package com.eventtracker.sdk.internal.util

/** Детерминированный фейк [Clock] — тесты сдвигают время явно, не полагаясь на реальное время. */
internal class FakeClock(private var millis: Long) : Clock {
    override fun nowMillis(): Long = millis
    fun advanceByDays(days: Long) {
        millis += days * 24L * 60 * 60 * 1000
    }
    fun set(newMillis: Long) {
        millis = newMillis
    }
}

/** Детерминированный фейк [IdGenerator] — последовательные id вместо случайных UUID. */
internal class FakeIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String = "id-${++counter}"
}
