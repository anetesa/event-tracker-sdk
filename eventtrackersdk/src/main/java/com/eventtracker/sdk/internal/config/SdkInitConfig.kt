package com.eventtracker.sdk.internal.config

/** Initial `retentionDays`/`maxEventCount` resolved for SDK auto-init — see [SdkInitConfigReader]. */
internal data class SdkInitValues(val retentionDays: Int, val maxEventCount: Int) {
    companion object {
        val DEFAULTS = SdkInitValues(
            retentionDays = SdkConfigStore.DEFAULT_RETENTION_DAYS,
            maxEventCount = SdkConfigStore.DEFAULT_MAX_EVENT_COUNT,
        )
    }
}

/**
 * Parses the optional JSON config asset a host app may ship to declare the SDK's initial
 * configuration without writing any Kotlin — the same "drop a config file in, no code required"
 * shape as Firebase's `google-services.json`. Read once by
 * [com.eventtracker.sdk.internal.init.EventTrackerInitProvider] on process start.
 *
 * Deliberately not `org.json.JSONObject`: same reasoning as
 * [com.eventtracker.sdk.internal.util.PropertiesJsonCodec] — org.json's real Android
 * implementation throws under plain JUnit (it only cooperates with Robolectric), which would
 * force this parser's tests onto a heavier test setup for no real payoff. The schema is a flat
 * two-key object of integers, so a full JSON parser would be solving a much bigger problem than
 * this file actually poses; a small key-anchored scan is simpler than the problem it avoids.
 */
internal object SdkInitConfigReader {

    const val ASSET_FILE_NAME = "event_tracker_config.json"

    /**
     * Any missing key, or a value that isn't a plain integer, silently falls back to
     * [SdkInitValues.DEFAULTS] for that key alone — a malformed or partial config file must never
     * crash app startup, since this runs before any host app code has had a chance to run.
     */
    fun parse(text: String): SdkInitValues {
        val retentionDays = extractInt(text, "retentionDays") ?: SdkInitValues.DEFAULTS.retentionDays
        val maxEventCount = extractInt(text, "maxEventCount") ?: SdkInitValues.DEFAULTS.maxEventCount
        return SdkInitValues(retentionDays, maxEventCount)
    }

    private fun extractInt(text: String, key: String): Int? {
        val marker = "\"$key\""
        val keyIndex = text.indexOf(marker)
        if (keyIndex == -1) return null

        var i = keyIndex + marker.length
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != ':') return null
        i++
        while (i < text.length && text[i].isWhitespace()) i++

        val start = i
        if (i < text.length && text[i] == '-') i++
        val digitsStart = i
        while (i < text.length && text[i].isDigit()) i++
        if (i == digitsStart) return null // no digits after an optional leading '-'

        return text.substring(start, i).toIntOrNull()
    }
}
