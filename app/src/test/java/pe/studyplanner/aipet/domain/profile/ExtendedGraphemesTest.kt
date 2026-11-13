package pe.studyplanner.aipet.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtendedGraphemesTest {
    @Test
    fun `cumple todas las fronteras del corpus oficial Unicode 17`() {
        val stream = requireNotNull(javaClass.getResourceAsStream("/unicode/GraphemeBreakTest-17.0.0.txt"))
        var cases = 0
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEachIndexed { lineIndex, line ->
                val specification = line.substringBefore('#').trim()
                if (specification.isNotEmpty()) {
                    val text = StringBuilder()
                    val expected = mutableListOf<Int>()
                    specification.split(Regex("\\s+")).forEach { token ->
                        when (token) {
                            "÷" -> expected.add(text.length)
                            "×" -> Unit
                            else -> text.appendCodePoint(token.toInt(16))
                        }
                    }
                    assertEquals("Unicode 17, línea ${lineIndex + 1}", expected, ExtendedGraphemes.boundaries(text.toString()))
                    assertEquals("Conteo en línea ${lineIndex + 1}", expected.size - 1, ExtendedGraphemes.count(text.toString()))
                    cases++
                }
            }
        }
        assertEquals("No se permite recortar el corpus oficial", 766, cases)
    }

    @Test
    fun `incluye vacio controles banderas y uniones que no forman emoji`() {
        assertEquals(0, ExtendedGraphemes.count(""))
        assertEquals(listOf(0), ExtendedGraphemes.boundaries(""))
        assertEquals(listOf(0, 2), ExtendedGraphemes.boundaries("\r\n"))
        assertEquals(listOf(0, 1, 2, 3), ExtendedGraphemes.boundaries("a\nb"))
        assertEquals(listOf(0, 4, 6), ExtendedGraphemes.boundaries("🇵🇪🇺"))
        assertEquals(listOf(0, 2, 3), ExtendedGraphemes.boundaries("a\u200Db"))
    }

    @Test
    fun `cuenta nombres largos sin perder estado en secuencias extendidas`() {
        listOf("👩🏽‍🚀", "👩‍👩‍👧‍👦", "🙂‍↔️", "🇵🇪", "1️⃣", "e\u0301", "각", "क्षि").forEach { cluster ->
            assertEquals(cluster, 1, ExtendedGraphemes.count(cluster))
            assertEquals(cluster, 80, ExtendedGraphemes.count(cluster.repeat(80)))
        }
    }
}
