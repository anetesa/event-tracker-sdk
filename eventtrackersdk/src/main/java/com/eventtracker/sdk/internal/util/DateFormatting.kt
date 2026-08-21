package com.eventtracker.sdk.internal.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats a Unix-millis timestamp into the `DD/MM/YYYY` bucket string used as the persisted
 * `createdDate` column (see schema requirement) and for statistics grouping.
 *
 * Uses a fixed [Locale.US] so the separator/order never depend on device locale, but the device's
 * default [ZoneId] so "today" matches what the user actually perceives as today rather than a
 * UTC day that could differ by one day depending on timezone.
 *
 * [DateTimeFormatter] is immutable and thread-safe, so this is safe to call concurrently from
 * multiple threads without any external synchronization.
 */
internal object DateFormatting {

    private val pattern = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)

    fun dayBucket(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(pattern)
}
