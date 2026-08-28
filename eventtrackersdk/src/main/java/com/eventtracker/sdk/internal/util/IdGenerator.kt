package com.eventtracker.sdk.internal.util

import java.util.UUID

/** Шов над генерацией уникальных id, чтобы тесты могли подставлять детерминированные значения. */
internal interface IdGenerator {
    fun newId(): String
}

internal object UuidGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
