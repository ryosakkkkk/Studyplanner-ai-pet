package pe.studyplanner.aipet.domain.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptLogTest {

    private fun attempt(
        t: Long = 1_761_600_000_250L,
        topic: String = "porcentajes",
        id: String = "porcentajes-d2-s7",
        mode: AttemptMode = AttemptMode.GUIDED,
        selected: Int = 2,
        correct: Boolean = true,
        hints: Int = 1,
        elapsed: Long = 12_345,
    ) = Attempt(t, topic, id, mode, selected, correct, hints, elapsed)

    private fun roundTrip(attempts: List<Attempt>): List<Attempt> {
        val decoded = AttemptLog.decode(AttemptLog.encode(attempts))
        assertEquals(emptyList<String>(), decoded.errors)
        return decoded.attempts
    }

    @Test
    fun elEncabezadoTieneLasColumnasPedidas() {
        assertEquals("fecha_iso,tema,modo,ejercicio,opcion,correcta,pistas,tiempo_ms", AttemptLog.HEADER)
        assertEquals(AttemptLog.HEADER + "\n", AttemptLog.encode(emptyList()))
    }

    @Test
    fun unaFilaTieneElFormatoDocumentado() {
        val row = AttemptLog.encodeRow(attempt())
        assertEquals(8, row.split(",").size)
        assertTrue(row, row.startsWith("2025-10-27T21:20:00.250Z,"))
        assertTrue(row.endsWith(",porcentajes,GUIDED,porcentajes-d2-s7,C,1,1,12345"))
    }

    @Test
    fun ida_y_vuelta_sin_perdida() {
        val attempts = listOf(
            attempt(),
            attempt(t = 1_761_600_060_000L, mode = AttemptMode.UNAIDED, selected = 0, correct = false, hints = 0, elapsed = 900),
            attempt(t = 1_761_600_120_999L, mode = AttemptMode.UNAIDED, selected = Attempt.NO_ANSWER, correct = false, hints = 0, elapsed = 0),
            attempt(selected = 4, correct = true, hints = 3, elapsed = 3_600_000),
        )
        assertEquals(attempts, roundTrip(attempts))
    }

    @Test
    fun unaOpcionNoElegidaSeEscribeConGuion() {
        val row = AttemptLog.encodeRow(attempt(selected = Attempt.NO_ANSWER, correct = false, mode = AttemptMode.UNAIDED))
        assertTrue(row, row.contains(",UNAIDED,"))
        assertTrue(row, row.contains(",-,0,"))
    }

    @Test
    fun camposConComasComillasYTildesSobrevivenAlViajeDeIdaYVuelta() {
        val nasty = listOf(
            attempt(topic = "tema, con coma", id = "id \"con comillas\""),
            attempt(topic = "razones-trigonométricas", id = "año-ñandú-d1-s1"),
            attempt(topic = "dos\nlíneas", id = "retorno\r\nde carro"),
            attempt(topic = "\"\"", id = ",,,"),
            attempt(topic = " espacios al borde ", id = "a,\"b\",c"),
        )
        assertEquals(nasty, roundTrip(nasty))
    }

    @Test
    fun loQueNecesitaComillasVaEntreComillas() {
        val row = AttemptLog.encodeRow(attempt(topic = "a,b", id = "dice \"hola\""))
        assertTrue(row, row.contains(",\"a,b\","))
        assertTrue(row, row.contains(",\"dice \"\"hola\"\"\","))
        val plain = AttemptLog.encodeRow(attempt())
        assertFalse(plain, plain.contains('"'))
    }

    @Test
    fun aceptaFinesDeLineaWindowsYLineasVacias() {
        val attempts = listOf(attempt(), attempt(t = 1_761_600_999_000L, selected = 3, correct = false))
        val csv = AttemptLog.encode(attempts).replace("\n", "\r\n") + "\r\n\r\n"
        val decoded = AttemptLog.decode(csv)
        assertEquals(emptyList<String>(), decoded.errors)
        assertEquals(attempts, decoded.attempts)
    }

    @Test
    fun aceptaUnArchivoSinSaltoFinalYConMarcaBom() {
        val attempts = listOf(attempt())
        val csv = "﻿" + AttemptLog.encode(attempts).trimEnd('\n')
        val decoded = AttemptLog.decode(csv)
        assertEquals(emptyList<String>(), decoded.errors)
        assertEquals(attempts, decoded.attempts)
    }

    @Test
    fun lasFilasInvalidasSeReportanYNoDetienenLaLectura() {
        val good1 = attempt()
        val good2 = attempt(t = 1_761_700_000_000L, selected = 1, correct = false)
        val csv = listOf(
            AttemptLog.HEADER,
            AttemptLog.encodeRow(good1),
            "esto no es una fila",
            "2025-10-28T00:00:00Z,tema,GUIDED,tema-d1-s1,Z,1,0,100",     // opción inválida
            "2025-10-28T00:00:00Z,tema,OTRO,tema-d1-s1,A,1,0,100",       // modo inválido
            "ayer,tema,GUIDED,tema-d1-s1,A,1,0,100",                      // fecha inválida
            "2025-10-28T00:00:00Z,tema,GUIDED,tema-d1-s1,A,2,0,100",     // correcta inválida
            "2025-10-28T00:00:00Z,tema,GUIDED,tema-d1-s1,A,1,-1,100",    // pistas negativas
            "2025-10-28T00:00:00Z,tema,GUIDED,tema-d1-s1,A,1,0,abc",     // tiempo inválido
            "2025-10-28T00:00:00Z,,GUIDED,tema-d1-s1,A,1,0,100",         // tema vacío
            AttemptLog.encodeRow(good2),
        ).joinToString("\n")
        val decoded = AttemptLog.decode(csv)
        assertEquals(listOf(good1, good2), decoded.attempts)
        assertEquals(8, decoded.errors.size)
        assertTrue(decoded.errors.first(), decoded.errors.first().startsWith("Fila 3"))
    }

    @Test
    fun unEncabezadoDistintoSeReportaPeroLasFilasSeLeen() {
        val csv = "fecha,tema\n" + AttemptLog.encodeRow(attempt()) + "\n"
        val decoded = AttemptLog.decode(csv)
        assertEquals(1, decoded.attempts.size)
        assertEquals(1, decoded.errors.size)
        assertTrue(decoded.errors[0], decoded.errors[0].contains("encabezado"))
    }

    @Test
    fun unArchivoSinEncabezadoSeLeeCompleto() {
        val attempts = listOf(attempt(), attempt(t = 1_761_700_000_000L, selected = 4, correct = false))
        val csv = attempts.joinToString("\n", postfix = "\n") { AttemptLog.encodeRow(it) }
        val decoded = AttemptLog.decode(csv)
        assertEquals(emptyList<String>(), decoded.errors)
        assertEquals(attempts, decoded.attempts)
    }

    @Test
    fun unArchivoVacioNoDaErrores() {
        val decoded = AttemptLog.decode("")
        assertTrue(decoded.attempts.isEmpty())
        assertTrue(decoded.errors.isEmpty())
        assertTrue(AttemptLog.decode(AttemptLog.HEADER).attempts.isEmpty())
    }

    @Test
    fun noGuardaDatosPersonales() {
        val rows = AttemptLog.encode(listOf(attempt())).trim().split("\n")
        val columns = rows[0].split(",")
        assertEquals(
            listOf("fecha_iso", "tema", "modo", "ejercicio", "opcion", "correcta", "pistas", "tiempo_ms"),
            columns,
        )
        // Ninguna columna admite nombre, correo ni identificador de dispositivo.
        assertFalse(columns.any { it.contains("nombre") || it.contains("correo") || it.contains("email") || it.contains("usuario") })
    }

    @Test
    fun sePuedeAñadirUnaFilaAlFinalDeUnArchivoExistente() {
        val first = attempt()
        val second = attempt(t = 1_762_000_000_000L, selected = 0, correct = false)
        val file = AttemptLog.encode(listOf(first)) + AttemptLog.encodeRow(second) + "\n"
        assertEquals(listOf(first, second), AttemptLog.decode(file).attempts)
    }

    @Test
    fun losIntentosDeUnaSesionRealSobrevivenAlCsv() {
        val s = newSession(config = SessionConfig(seed = 17L))
        s.toGuided()
        s.answer(wrongOf(s.state))
        s.answer(correctOf(s.state))
        s.next()
        s.requestHint()
        s.answer(correctOf(s.state))
        s.next()
        s.answer(correctOf(s.state))
        s.next()
        s.runUnaided(listOf(true, null))
        assertEquals(s.attempts, roundTrip(s.attempts))
    }
}
