package com.eventtracker.sdk.internal.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PropertiesJsonCodecTest {

    @Test
    fun `empty map encodes to empty json object`() {
        assertEquals("{}", PropertiesJsonCodec.encode(emptyMap()))
    }

    @Test
    fun `empty json decodes to empty map`() {
        assertEquals(emptyMap<String, String>(), PropertiesJsonCodec.decode(""))
        assertEquals(emptyMap<String, String>(), PropertiesJsonCodec.decode("{}"))
    }

    @Test
    fun `round trips simple values`() {
        val original = mapOf("screen" to "home", "duration" to "5s")
        val encoded = PropertiesJsonCodec.encode(original)
        assertEquals(original, PropertiesJsonCodec.decode(encoded))
    }

    @Test
    fun `round trips quotes backslashes and newlines`() {
        val original = mapOf("quote" to "say \"hi\"", "path" to "C:\\temp", "multiline" to "a\nb")
        val encoded = PropertiesJsonCodec.encode(original)
        assertEquals(original, PropertiesJsonCodec.decode(encoded))
    }

    @Test
    fun `round trips unicode`() {
        val original = mapOf("greeting" to "Привет", "emoji" to "🚀")
        val encoded = PropertiesJsonCodec.encode(original)
        assertEquals(original, PropertiesJsonCodec.decode(encoded))
    }

    @Test
    fun `malformed json falls back to empty map instead of throwing`() {
        assertEquals(emptyMap<String, String>(), PropertiesJsonCodec.decode("not json"))
        assertEquals(emptyMap<String, String>(), PropertiesJsonCodec.decode("{\"broken\":"))
    }
}
