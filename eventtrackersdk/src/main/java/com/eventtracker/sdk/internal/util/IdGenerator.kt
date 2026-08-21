package com.eventtracker.sdk.internal.util

import java.util.UUID

/** Seam over unique-id generation so tests can supply deterministic ids. */
internal interface IdGenerator {
    fun newId(): String
}

internal object UuidGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
