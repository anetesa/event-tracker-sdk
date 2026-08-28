package com.eventtracker.sdk.internal.util

/**
 * Кодирует/декодирует плоскую форму `Map<String, String>` свойств события в JSON-строку и
 * обратно, не затягивая `org.json` (или любую другую JSON-библиотеку) в зависимости SDK.
 *
 * `org.json.JSONObject` рассматривался, но его настоящая Android-реализация бросает заглушки
 * `RuntimeException` при запуске под чистым JUnit (работает только под Robolectric или на
 * устройстве), что вынудило бы каждого потребителя тестов этого кодека тянуть более тяжёлый
 * тестовый фреймворк. Поскольку properties — это всегда плоская строковая карта, никогда не
 * вложенные объекты, массивы или нестроковые значения, — маленький написанный вручную кодек
 * проще той проблемы, которую он обходит, и ведёт себя одинаково и в проде, и в юнит-тестах.
 */
internal object PropertiesJsonCodec {

    fun encode(properties: Map<String, String>): String {
        if (properties.isEmpty()) return "{}"
        return properties.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${quote(key)}:${quote(value)}"
        }
    }

    /**
     * Декодирует JSON-объект со строковыми ключами и строковыми значениями. Некорректный ввод
     * (например, из повреждённой или отредактированной извне строки) никогда не бросает
     * исключение — он логируется и заменяется на пустую карту, поскольку один нечитаемый блок
     * properties не должен ронять выборку всех остальных событий.
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
