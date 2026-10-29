package io.github.saadahmed1122.bibi.spike.s4

// Pure Kotlin (no android.*). org.json is Android-only on the classpath and kotlinx.serialization
// would be a library, so the spike carries this small strict parser.

class JsonError(message: String) : Exception(message)

/**
 * Strict RFC 8259 subset: objects, arrays, strings, numbers, true/false/null. No comments, single
 * quotes, trailing commas, duplicate keys or trailing content. Integers that fit a Long become
 * [Long]; anything else numeric becomes [Double]. Nesting is capped at [MAX_DEPTH].
 */
object MiniJson {
    const val MAX_DEPTH = 8

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value(0)
        p.ws()
        if (p.i != text.length) throw JsonError("unexpected content at ${p.i}")
        return v
    }

    /** JSON string literal with escapes. */
    fun quote(s: String): String = buildString {
        append('"')
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw JsonError("nested deeper than $MAX_DEPTH levels")
            if (i >= s.length) throw JsonError("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else throw JsonError("unexpected '$c' at $i")
            }
        }

        fun obj(depth: Int): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (peek() == '}') { i++; return out }
            while (true) {
                ws()
                if (peek() != '"') throw JsonError("expected a key at $i")
                val k = str()
                ws()
                expect(':')
                ws()
                if (out.containsKey(k)) throw JsonError("duplicate key \"$k\"")
                out[k] = value(depth + 1)
                ws()
                when (next()) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw JsonError("expected ',' or '}' at ${i - 1}")
                }
            }
        }

        fun arr(depth: Int): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            ws()
            if (peek() == ']') { i++; return out }
            while (true) {
                ws()
                out += value(depth + 1)
                ws()
                when (next()) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw JsonError("expected ',' or ']' at ${i - 1}")
                }
            }
        }

        fun str(): String {
            i++
            val b = StringBuilder()
            while (true) {
                if (i >= s.length) throw JsonError("unterminated string")
                val c = s[i++]
                when {
                    c == '"' -> return b.toString()
                    c == '\\' -> {
                        if (i >= s.length) throw JsonError("unterminated escape")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> b.append(e)
                            'b' -> b.append('\b')
                            'f' -> b.append('\u000c')
                            'n' -> b.append('\n')
                            'r' -> b.append('\r')
                            't' -> b.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw JsonError("bad \\u escape")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: throw JsonError("bad \\u escape")
                                b.append(code.toChar())
                                i += 4
                            }
                            else -> throw JsonError("bad escape \\$e")
                        }
                    }
                    c < ' ' -> throw JsonError("control character in string")
                    else -> b.append(c)
                }
            }
        }

        fun num(): Any {
            val start = i
            if (peek() == '-') i++
            if (peek() == '0') {
                i++
            } else if (peek() in '1'..'9') {
                while (peek() in '0'..'9') i++
            } else {
                throw JsonError("bad number at $start")
            }
            var integral = true
            if (peek() == '.') {
                integral = false
                i++
                if (peek() !in '0'..'9') throw JsonError("bad number at $start")
                while (peek() in '0'..'9') i++
            }
            if (peek() == 'e' || peek() == 'E') {
                integral = false
                i++
                if (peek() == '+' || peek() == '-') i++
                if (peek() !in '0'..'9') throw JsonError("bad number at $start")
                while (peek() in '0'..'9') i++
            }
            val t = s.substring(start, i)
            return (if (integral) t.toLongOrNull() else null) ?: t.toDouble()
        }

        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw JsonError("unexpected token at $i")
            i += word.length
            return v
        }

        fun peek(): Char = if (i < s.length) s[i] else '\u0000'
        fun next(): Char = if (i < s.length) s[i++] else '\u0000'
        fun expect(c: Char) {
            if (next() != c) throw JsonError("expected '$c' at ${i - 1}")
        }
    }
}
