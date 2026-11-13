package pe.studyplanner.aipet.domain.json

/** El texto no es JSON válido (o supera los límites de seguridad). El mensaje está en español y dice dónde falló. */
class JsonException(message: String) : IllegalArgumentException(message)

/**
 * Lector y escritor de JSON mínimos, en Kotlin puro (sin `org.json`, para que las pruebas de la JVM no necesiten
 * Android). Valores: `Map<String, Any?>` (objeto, conserva el orden), `List<Any?>` (arreglo), `String`, `Long`
 * (enteros), `Double` (decimales o exponentes), `Boolean` y `null`.
 */
object MiniJson {

    private const val MAX_DEPTH = 64

    /** Lee un documento JSON completo. Lanza [JsonException] si hay algo después del valor o si está mal formado. */
    fun parse(text: String): Any? {
        val reader = Reader(text)
        reader.skipSpaces()
        val value = reader.readValue(0)
        reader.skipSpaces()
        if (!reader.atEnd()) reader.fail("hay texto después del valor")
        return value
    }

    /** Escribe [value] como JSON compacto. Acepta los mismos tipos que devuelve [parse] (y `Int`/`Float`/`Number`). */
    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    /** Comillas, barras y caracteres de control escapados; el resto (tildes, ñ) pasa tal cual. */
    fun quote(text: String): String = StringBuilder().also { appendString(it, text) }.toString()

    private fun append(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> appendString(sb, value)
            is Boolean -> sb.append(value)
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Double -> {
                require(value.isFinite()) { "JSON no admite NaN ni infinito" }
                sb.append(value.toString())
            }
            is Float -> {
                require(value.isFinite()) { "JSON no admite NaN ni infinito" }
                sb.append(value.toString())
            }
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    appendString(sb, k.toString())
                    sb.append(':')
                    append(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    append(sb, v)
                }
                sb.append(']')
            }
            else -> throw IllegalArgumentException("Tipo no admitido en JSON: ${value::class.simpleName}")
        }
    }

    private fun appendString(sb: StringBuilder, text: String) {
        sb.append('"')
        for (c in text) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' || c == ' ' || c == ' ' -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Reader(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun fail(what: String): Nothing = throw JsonException("JSON inválido en la posición $i: $what")

        fun skipSpaces() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun readValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("anidación demasiado profunda")
            if (atEnd()) fail("se esperaba un valor")
            return when (val c = s[i]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') readNumber() else fail("carácter inesperado '$c'")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, i)) fail("se esperaba $word")
            i += word.length
            return value
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            i++ // {
            val out = LinkedHashMap<String, Any?>()
            skipSpaces()
            if (peek() == '}') { i++; return out }
            while (true) {
                skipSpaces()
                if (peek() != '"') fail("se esperaba el nombre de un campo")
                val key = readString()
                skipSpaces()
                if (peek() != ':') fail("se esperaba ':'")
                i++
                skipSpaces()
                if (out.containsKey(key)) fail("campo repetido '$key'")
                out[key] = readValue(depth + 1)
                skipSpaces()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> fail("se esperaba ',' o '}'")
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            i++ // [
            val out = ArrayList<Any?>()
            skipSpaces()
            if (peek() == ']') { i++; return out }
            while (true) {
                skipSpaces()
                out += readValue(depth + 1)
                skipSpaces()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> fail("se esperaba ',' o ']'")
                }
            }
        }

        private fun peek(): Char = if (i < s.length) s[i] else fail("fin inesperado del texto")

        private fun readString(): String {
            i++ // comillas de apertura
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) fail("cadena sin cerrar")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) fail("escape incompleto")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("escape \\u incompleto")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: fail("escape \\u inválido")
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> fail("escape desconocido '\\$e'")
                        }
                    }
                    c < ' ' -> fail("carácter de control sin escapar en una cadena")
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = i
            if (s[i] == '-') i++
            val digitsStart = i
            while (i < s.length && s[i] in '0'..'9') i++
            if (i == digitsStart) fail("número sin dígitos")
            if (s[digitsStart] == '0' && i - digitsStart > 1) fail("número con ceros a la izquierda")
            var decimal = false
            if (i < s.length && s[i] == '.') {
                decimal = true
                i++
                val fracStart = i
                while (i < s.length && s[i] in '0'..'9') i++
                if (i == fracStart) fail("faltan dígitos después del punto")
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                decimal = true
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                val expStart = i
                while (i < s.length && s[i] in '0'..'9') i++
                if (i == expStart) fail("faltan dígitos en el exponente")
            }
            val text = s.substring(start, i)
            return if (decimal) text.toDouble() else text.toLongOrNull() ?: text.toDouble()
        }
    }
}
