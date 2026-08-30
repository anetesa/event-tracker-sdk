package com.eventtracker.sdk.internal.util

/**
 * Подход: рукописный фейк [Clock] (не мок).
 *
 * Почему: тестам нужно не «вернуть одно застабленное число», а управляемо двигать время между
 * шагами теста ([advanceByDays], [set]) — то есть сам объект должен хранить и изменять состояние.
 * Мок с `every { nowMillis() } returns X` не позволил бы естественно выразить «сдвинуть на день
 * назад, потом вернуться к базовому времени» в одном тесте.
 */
internal class FakeClock(private var millis: Long) : Clock {
    override fun nowMillis(): Long = millis
    fun advanceByDays(days: Long) {
        millis += days * 24L * 60 * 60 * 1000
    }
    fun set(newMillis: Long) {
        millis = newMillis
    }
}

/**
 * Подход: рукописный фейк [IdGenerator] (не мок).
 *
 * Почему: нужны предсказуемые, воспроизводимые id ("id-1", "id-2", ...) для точных assertEquals
 * в тестах — случайный UUID из реальной реализации или последовательность `every {} returnsMany`
 * на мок были бы либо непроверяемы напрямую, либо избыточно многословны для счётчика из одной строки.
 */
internal class FakeIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String = "id-${++counter}"
}
