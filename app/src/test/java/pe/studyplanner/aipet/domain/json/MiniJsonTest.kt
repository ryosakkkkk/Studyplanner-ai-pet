package pe.studyplanner.aipet.domain.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    private fun fails(text: String): String {
        try {
            MiniJson.parse(text)
        } catch (e: JsonException) {
            return e.message.orEmpty()
        }
        throw AssertionError("se esperaba JsonException para: $text")
    }

    @Test
    fun leeObjetosArreglosYValoresSimples() {
        val v = MiniJson.parse("""{"a": 1, "b": [true, false, null], "c": {"d": "x"}, "e": -2.5, "f": 1e2}""") as Map<*, *>
        assertEquals(1L, v["a"])
        assertEquals(listOf(true, false, null), v["b"])
        assertEquals(mapOf("d" to "x"), v["c"])
        assertEquals(-2.5, v["e"] as Double, 0.0)
        assertEquals(100.0, v["f"] as Double, 0.0)
        assertEquals(listOf("a", "b", "c", "e", "f"), v.keys.toList())
    }

    @Test
    fun leeCadenasConEscapesYUnicode() {
        assertEquals("a\"b\\c/d\ne\tf", MiniJson.parse("\"a\\\"b\\\\c\\/d\\ne\\tf\""))
        assertEquals("Perú ñandú ³⁺ ½", MiniJson.parse("\"Perú ñandú ³⁺ ½\""))
        assertEquals("é", MiniJson.parse("\"\\u00e9\""))
        // Par sustituto: 😀 (U+1F600)
        assertEquals("\uD83D\uDE00", MiniJson.parse("\"\\uD83D\\uDE00\""))
    }

    @Test
    fun aceptaEspaciosAlrededorYArreglosVacios() {
        assertEquals(emptyList<Any?>(), MiniJson.parse("  [ ]  "))
        assertEquals(emptyMap<String, Any?>(), MiniJson.parse("\n{ }\n"))
        assertNull(MiniJson.parse("null"))
    }

    @Test
    fun rechazaJsonMalFormado() {
        for (bad in listOf("", "{", "[1,]", "{\"a\":}", "{\"a\" 1}", "[1 2]", "\"sin cerrar", "tru", "01", "1.", "-", "{\"a\":1}x", "{'a':1}", "[1,,2]")) {
            assertTrue("'$bad'", fails(bad).startsWith("JSON inválido"))
        }
    }

    @Test
    fun rechazaCamposRepetidosEscapesInvalidosYControlesSinEscapar() {
        assertTrue(fails("""{"a":1,"a":2}""").contains("repetido"))
        assertTrue(fails("\"\\q\"").contains("escape"))
        assertTrue(fails("\"\\u12\"").contains("\\u"))
        assertTrue(fails("\"a\nb\"").contains("control"))
    }

    @Test
    fun limitaLaProfundidad() {
        val deep = "[".repeat(200) + "]".repeat(200)
        assertTrue(fails(deep).contains("profunda"))
        MiniJson.parse("[".repeat(30) + "]".repeat(30))
    }

    @Test
    fun escribeYVuelveALeerLoMismo() {
        val doc = linkedMapOf<String, Any?>(
            "texto" to "línea 1\nlínea 2 \"cita\" \\ ³⁺ \u0001",
            "n" to 7,
            "l" to 9_000_000_000L,
            "d" to 1.5,
            "ok" to true,
            "nada" to null,
            "lista" to listOf(1, "dos", listOf(3)),
            "obj" to mapOf("k" to "v"),
        )
        val json = MiniJson.write(doc)
        assertFalse(json.contains("\n"))
        val back = MiniJson.parse(json) as Map<*, *>
        assertEquals(doc["texto"], back["texto"])
        assertEquals(7L, back["n"])
        assertEquals(9_000_000_000L, back["l"])
        assertEquals(1.5, back["d"] as Double, 0.0)
        assertEquals(listOf(1L, "dos", listOf(3L)), back["lista"])
        assertEquals(mapOf("k" to "v"), back["obj"])
        assertEquals(json, MiniJson.write(MiniJson.parse(json)))
    }

    @Test
    fun quoteEscapaLoNecesario() {
        assertEquals("\"a\\\"b\\n\"", MiniJson.quote("a\"b\n"))
        assertEquals("\"ñ\"", MiniJson.quote("ñ"))
    }
}
