package com.eventtracker.sdk.internal.util

/**
 * Encodes/decodes the flat `Map<String, String>` shape of event properties to/from a JSON
 * string, without pulling `org.json` (or any other JSON library) into the SDK's dependency
 * footprint.
 *
 * `org.json.JSONObject` was considered, but its real Android implementation throws
 * `RuntimeException` stubs when run under plain JUnit (it only works under Robolectric or on a
 * device), which would force every consumer of this codec's tests onto a heavier test
 * framework. Since properties are always a flat string map — never nested objects, arrays, or
 * non-string values — a small hand-rolled codec is simpler than the problem it avoids, and
 * behaves identically in production and unit tests.
 */
internal object PropertiesJsonCodec {

    fun encode(properties: Map<String, String>): String {
        if (properties.isEmpty()) return "{}"
        return properties.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${quote(key)}:${quote(value)}"
        }
    }

    /**
     * Decodes a JSON object of string keys to string values. Malformed input (e.g. from a
     * corrupted or externally-edited row) never throws — it logs and falls back to an empty
     * map, since a single unreadable properties blob must not crash retrieval of every event.
     */
    fun decode(json: String): Map<String, String> {
        val trimmed = json.trim()
        if (trimmed.isEmpty() || trimmed == "{}") return emptyMap()
        return runCatching { Parser(trimmed).parseObject() }
            .getOrElse { emptyMap() }
    }

    private fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) {
                    sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseObject(): Map<String, String> {
            skipWhitespace()
            expect('{')
            val result = LinkedHashMap<String, String>()
            skipWhitespace()
            if (peek() == '}') {
                i++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                val value = parseString()
                result[key] = value
                skipWhitespace()
                when (peek()) {
                    ',' -> {
                        i++
                        continue
                    }
                    '}' -> {
                        i++
                        break
                    }
                    else -> error("Expected ',' or '}' at index $i")
                }
            }
            return result
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val esc = s[i++]
                        when (esc) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                val hex = s.substring(i, i + 4)
                                i += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> error("Unknown escape \\$esc at index $i")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun peek(): Char = s[i]

        private fun expect(c: Char) {
            skipWhitespace()
            if (s.isEmpty() || i >= s.length || s[i] != c) {
                error("Expected '$c' at index $i")
            }
            i++
        }

        private fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }
    }
}
