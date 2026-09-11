package com.eventtracker.sdk.internal.config

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plain JUnit, no Robolectric: [SdkInitConfigReader.parse] is a pure string-in/data-class-out
 * function with no Android dependency (see its doc comment for why it avoids `org.json`), so
 * there's nothing here a fake or a heavier test runner would buy.
 */
class SdkInitConfigReaderTest {

    @Test
    fun `parses both keys regardless of formatting`() {
        val result = SdkInitConfigReader.parse("""{"retentionDays": 14, "maxEventCount": 200}""")
        assertEquals(SdkInitValues(14, 200), result)
    }

    @Test
    fun `parses compact json with no whitespace`() {
        val result = SdkInitConfigReader.parse("""{"retentionDays":14,"maxEventCount":200}""")
        assertEquals(SdkInitValues(14, 200), result)
    }

    @Test
    fun `missing key falls back to that key's default only`() {
        val result = SdkInitConfigReader.parse("""{"retentionDays": 30}""")
        assertEquals(SdkInitValues(30, SdkInitValues.DEFAULTS.maxEventCount), result)
    }

    @Test
    fun `empty document falls back to all defaults`() {
        assertEquals(SdkInitValues.DEFAULTS, SdkInitConfigReader.parse(""))
        assertEquals(SdkInitValues.DEFAULTS, SdkInitConfigReader.parse("{}"))
    }

    @Test
    fun `malformed json falls back to defaults instead of throwing`() {
        assertEquals(SdkInitValues.DEFAULTS, SdkInitConfigReader.parse("not json at all"))
        assertEquals(SdkInitValues.DEFAULTS, SdkInitConfigReader.parse("""{"retentionDays": }"""))
    }

    @Test
    fun `non-numeric value for a key falls back to that key's default only`() {
        val result = SdkInitConfigReader.parse("""{"retentionDays": "soon", "maxEventCount": 200}""")
        assertEquals(SdkInitValues(SdkInitValues.DEFAULTS.retentionDays, 200), result)
    }

    @Test
    fun `negative maxEventCount is preserved as the unlimited sentinel`() {
        val result = SdkInitConfigReader.parse("""{"maxEventCount": -1}""")
        assertEquals(-1, result.maxEventCount)
    }

    @Test
    fun `key name appearing only as part of another key is not matched`() {
        // "retentionDaysExtra" must not be mistaken for "retentionDays".
        val result = SdkInitConfigReader.parse("""{"retentionDaysExtra": 999, "maxEventCount": 50}""")
        assertEquals(SdkInitValues(SdkInitValues.DEFAULTS.retentionDays, 50), result)
    }
}
