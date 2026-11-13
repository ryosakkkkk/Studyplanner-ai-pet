package pe.studyplanner.aipet.domain.calendar

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.studyplanner.aipet.domain.academic.AcademicActivity
import pe.studyplanner.aipet.domain.academic.AcademicSnapshot
import pe.studyplanner.aipet.domain.academic.ActivityKind
import pe.studyplanner.aipet.domain.academic.ActivityStatus
import pe.studyplanner.aipet.domain.academic.ActivityTime
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.academic.RecurrenceException
import pe.studyplanner.aipet.domain.academic.WeeklyRule

class CalendarEngineTest {
    private val monday = LocalDate.of(2026, 10, 19)
    private val lima = "America/Lima"

    private fun instant(date: LocalDate, hour: Int, minute: Int = 0, zone: String = lima): Instant =
        StrictLocalTime.resolve(date.atTime(hour, minute), zone)

    private fun block(id: String, start: Instant, end: Instant, kind: ActivityKind = ActivityKind.PERSONAL) =
        AcademicActivity(id = id, kind = kind, title = id, time = ActivityTime.Timed(start, end, lima))

    private fun series(
        id: String = "serie", startDate: LocalDate = monday, until: LocalDate? = monday.plusWeeks(3),
        weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY), interval: Int = 1,
        start: LocalTime = LocalTime.of(10, 0), end: LocalTime = LocalTime.of(11, 0),
        endDayOffset: Int = 0, zone: String = lima,
    ) = AcademicActivity(
        id = id, kind = ActivityKind.CLASS, title = "Clase", courseId = "curso",
        time = ActivityTime.Weekly(WeeklyRule(weekdays, start, end, zone, startDate, until, interval, endDayOffset)),
    )

    private fun snapshot(vararg activities: AcademicActivity, exceptions: List<RecurrenceException> = emptyList()) =
        AcademicSnapshot(DatasetScope.PERSONAL, revision = 7, activities = activities.toList(), exceptions = exceptions)

    @Test fun `fecha sin hora conserva fecha y nunca ocupa ni recibe instante al cambiar zona`() {
        val exam = AcademicActivity(id = "exam", kind = ActivityKind.EXAM, title = "Examen", time = ActivityTime.DateOnly(monday))
        val task = AcademicActivity(id = "task", kind = ActivityKind.TASK, title = "Entrega", time = ActivityTime.DateOnly(monday))
        listOf(lima, "Asia/Tokyo", "America/New_York").forEach { zone ->
            val result = CalendarEngine.day(snapshot(exam, task), monday, zone)
            assertEquals(2, result.entries.size)
            result.entries.forEach {
                assertEquals(monday, it.date)
                assertNull(it.start)
                assertNull(it.end)
                assertTrue(it.dateOnly)
                assertFalse(it.occupiesTime)
            }
            assertTrue(result.conflicts.isEmpty())
        }
    }

    @Test fun `tarea sin fecha no inventa vencimiento ni aparece como compromiso`() {
        val undated = AcademicActivity(id = "task", kind = ActivityKind.TASK, title = "Sin fecha")
        val result = CalendarEngine.week(snapshot(undated), monday, lima)
        assertTrue(result.entries.isEmpty())
        assertEquals(emptyList<CalendarEntry>(), CalendarEngine.next(snapshot(undated), instant(monday, 0), lima))
        assertEquals(ActivityTime.Unscheduled, undated.time)
    }

    @Test fun `plazo con hora no reserva franja y conserva instante fuente al cambiar zona`() {
        val due = instant(monday, 23, 30)
        val task = AcademicActivity(id = "task", kind = ActivityKind.TASK, title = "Entrega", time = ActivityTime.Timed(due, zoneId = lima))
        val event = block("deporte", due.minusSeconds(1800), due.plusSeconds(1800), ActivityKind.SPORT)
        val result = CalendarEngine.day(snapshot(task, event), monday.plusDays(1), "Europe/Madrid")
        val deadline = result.entries.single { it.activityId == "task" }
        assertEquals(due, deadline.start)
        assertEquals(monday.plusDays(1), deadline.date)
        assertFalse(deadline.occupiesTime)
        assertNull(deadline.end)
        assertEquals(lima, deadline.sourceZoneId)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test fun `recurrencia expande solo dias vigentes con fin inclusivo y revision de origen`() {
        val result = CalendarEngine.project(snapshot(series(until = monday.plusWeeks(2))), CalendarRange(monday.minusDays(7), monday.plusWeeks(5), lima))
        assertEquals(listOf(monday, monday.plusWeeks(1), monday.plusWeeks(2)), result.entries.map { it.occurrenceDate })
        assertEquals(7L, result.revision)
        assertTrue(result.issues.isEmpty())
    }

    @Test fun `intervalo bisemanal se ancla al lunes de semana inicial sin depender de vista`() {
        val rule = series(startDate = monday.plusDays(2), until = monday.plusWeeks(5), weekdays = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY), interval = 2)
        val result = CalendarEngine.project(snapshot(rule), CalendarRange(monday, monday.plusDays(34), lima))
        assertEquals(listOf(2L, 6L, 16L, 20L, 30L, 34L).map(monday::plusDays), result.entries.map { it.date })
        val sundayView = CalendarEngine.week(snapshot(rule), monday.plusDays(3), lima, DayOfWeek.SUNDAY)
        assertEquals(monday.minusDays(1), sundayView.range.startDate)
        assertEquals(listOf(monday.plusDays(2)), sundayView.entries.map { it.date })
    }

    @Test fun `cancelacion puntual omite solo la ocurrencia indicada`() {
        val source = snapshot(series(), exceptions = listOf(RecurrenceException("serie", monday.plusWeeks(1))))
        val result = CalendarEngine.project(source, CalendarRange(monday, monday.plusWeeks(3), lima))
        assertEquals(listOf(monday, monday.plusWeeks(2), monday.plusWeeks(3)), result.entries.map { it.occurrenceDate })
        assertEquals(1, source.activities.size)
        assertEquals(1, source.exceptions.size)
    }

    @Test fun `reemplazo movido desde otra semana aparece exactamente una vez en nuevo dia`() {
        val replacementDay = monday.plusWeeks(2).plusDays(1)
        val replacement = block("reemplazo", instant(replacementDay, 14), instant(replacementDay, 15), ActivityKind.CLASS)
        val source = snapshot(series(), replacement, exceptions = listOf(RecurrenceException("serie", monday, replacement.id)))
        assertTrue(CalendarEngine.day(source, monday, lima).entries.isEmpty())
        val result = CalendarEngine.day(source, replacementDay, lima)
        val occurrence = result.entries.single()
        assertEquals("reemplazo", occurrence.activityId)
        assertEquals("serie", occurrence.ruleId)
        assertEquals(monday, occurrence.occurrenceDate)
        assertEquals("serie@$monday", occurrence.key)
    }

    @Test fun `evento nocturno cruza medianoche con mismo instante y clave en ambos dias`() {
        val event = block("noche", instant(monday, 23), instant(monday.plusDays(1), 1))
        val result = CalendarEngine.week(snapshot(event), monday, lima)
        assertEquals(listOf(monday, monday.plusDays(1)), result.entries.map { it.date })
        assertEquals(1, result.entries.map { it.key }.distinct().size)
        assertEquals(event.time.let { it as ActivityTime.Timed }.start, result.entries[1].start)
        val midnight = block("hasta medianoche", instant(monday, 23), instant(monday.plusDays(1), 0))
        assertTrue(CalendarEngine.day(snapshot(midnight), monday.plusDays(1), lima).entries.isEmpty())
    }

    @Test fun `filtro de reemplazo usa curso y tipo reales del reemplazo`() {
        val replacement = block("reemplazo", instant(monday, 14), instant(monday, 15), ActivityKind.EXAM).copy(courseId = "otro")
        val source = snapshot(series(), replacement, exceptions = listOf(RecurrenceException("serie", monday, replacement.id)))
        val result = CalendarEngine.day(source, monday, lima, CalendarFilter(courseIds = setOf("otro"), kinds = setOf(ActivityKind.EXAM)))
        assertEquals(listOf("reemplazo"), result.entries.map { it.activityId })
        assertTrue(CalendarEngine.day(source, monday, lima, CalendarFilter(courseIds = setOf("curso"))).entries.isEmpty())
    }

    @Test fun `serie nocturna se incluye al consultar solo dia posterior`() {
        val overnight = series(start = LocalTime.of(23, 0), end = LocalTime.of(1, 0), endDayOffset = 1)
        val result = CalendarEngine.day(snapshot(overnight), monday.plusDays(1), lima)
        assertEquals(1, result.entries.size)
        assertEquals(monday, result.entries.single().occurrenceDate)
        assertEquals(instant(monday.plusDays(1), 1), result.entries.single().end)
    }

    @Test fun `bloques contiguos no chocan pero todos los pares con cruce son detectados una vez`() {
        val first = block("a", instant(monday, 9), instant(monday, 10))
        val next = block("b", instant(monday, 10), instant(monday, 11))
        val overlap = block("c", instant(monday, 9, 30), instant(monday, 10, 30))
        val result = CalendarEngine.day(snapshot(first, next, overlap), monday, lima)
        assertEquals(setOf(setOf("a", "c"), setOf("b", "c")), result.conflicts.map { setOf(it.firstKey, it.secondKey) }.toSet())
        assertEquals(2, result.conflicts.size)
        assertTrue(result.conflicts.all { it.start < it.end })
    }

    @Test fun `conflicto nocturno se informa una vez aunque ambos bloques salgan en dos fechas`() {
        val first = block("a", instant(monday, 23), instant(monday.plusDays(1), 2))
        val second = block("b", instant(monday, 23, 30), instant(monday.plusDays(1), 1))
        val result = CalendarEngine.week(snapshot(first, second), monday, lima)
        assertEquals(4, result.entries.size)
        assertEquals(1, result.conflicts.size)
    }

    @Test fun `filtros curso tipo y completado se combinan y orden es independiente de Room`() {
        val a = block("a", instant(monday, 10), instant(monday, 11), ActivityKind.EXAM).copy(courseId = "curso1")
        val b = a.copy(id = "b", title = "b", status = ActivityStatus.COMPLETED)
        val otherCourse = a.copy(id = "c", courseId = "curso2")
        val cancelled = a.copy(id = "d", status = ActivityStatus.CANCELLED)
        val otherKind = a.copy(id = "e", kind = ActivityKind.SPORT)
        val all = listOf(a, b, otherCourse, cancelled, otherKind)
        val filter = CalendarFilter(courseIds = setOf("curso1"), kinds = setOf(ActivityKind.EXAM))
        assertEquals(listOf("a"), CalendarEngine.day(snapshot(*all.toTypedArray()), monday, lima, filter).entries.map { it.activityId })
        assertEquals(listOf("a", "b"), CalendarEngine.day(snapshot(*all.reversed().toTypedArray()), monday, lima, filter.copy(includeCompleted = true)).entries.map { it.activityId })
        assertEquals(CalendarEngine.day(snapshot(*all.toTypedArray()), monday, lima), CalendarEngine.day(snapshot(*all.reversed().toTypedArray()), monday, lima))
        assertTrue(CalendarEngine.day(snapshot(a), monday, lima, CalendarFilter(courseIds = emptySet())).entries.isEmpty())
    }

    @Test fun `proximos tiene horizonte finito conserva fecha sin hora y elimina copias nocturnas`() {
        val dated = AcademicActivity(id = "fecha", kind = ActivityKind.EXAM, title = "Sin hora", time = ActivityTime.DateOnly(monday))
        val past = block("pasado", instant(monday, 7), instant(monday, 8))
        val night = block("noche", instant(monday, 23), instant(monday.plusDays(1), 1))
        val openSeries = series(id = "lejana", startDate = monday.plusDays(400), until = null)
        val result = CalendarEngine.next(snapshot(dated, past, night, openSeries), instant(monday, 9), lima, limit = 10)
        assertEquals(listOf("fecha", "noche"), result.map { it.activityId })
        assertNull(result.first().start)
        assertEquals(1, CalendarEngine.next(snapshot(dated, night), instant(monday, 9), lima, limit = 1).size)
    }

    @Test fun `zona de serie permanece fija y cambia solo la fecha presentada al cambiar zona perfil`() {
        val event = series(start = LocalTime.of(23, 0), end = LocalTime.of(23, 30), until = monday)
        val inLima = CalendarEngine.day(snapshot(event), monday, lima).entries.single()
        val inTokyo = CalendarEngine.day(snapshot(event), monday.plusDays(1), "Asia/Tokyo").entries.single()
        assertEquals(inLima.start, inTokyo.start)
        assertEquals(lima, inTokyo.sourceZoneId)
        assertEquals(monday, inTokyo.occurrenceDate)
    }

    @Test fun `proyeccion DST no corrige silenciosamente una ocurrencia inexistente`() {
        val start = LocalDate.of(2026, 3, 1)
        val event = series(startDate = start, until = start.plusWeeks(2), weekdays = setOf(DayOfWeek.SUNDAY), start = LocalTime.of(2, 30), end = LocalTime.of(3, 30), zone = "America/New_York")
        val result = CalendarEngine.project(snapshot(event), CalendarRange(start, start.plusWeeks(2), "America/New_York"))
        assertEquals(listOf(start, start.plusWeeks(2)), result.entries.map { it.date })
        assertEquals(start.plusWeeks(1), result.issues.single().occurrenceDate)
        assertTrue(result.issues.single().message.contains("no existe"))
    }

    @Test fun `proyeccion DST no elige automaticamente entre dos horas ambiguas`() {
        val date = LocalDate.of(2026, 11, 1)
        val event = series(startDate = date, until = date, weekdays = setOf(DayOfWeek.SUNDAY), start = LocalTime.of(1, 30), end = LocalTime.of(2, 30), zone = "America/New_York")
        val result = CalendarEngine.day(snapshot(event), date, "America/New_York")
        assertTrue(result.entries.isEmpty())
        assertTrue(result.issues.single().message.contains("dos veces"))
    }

    @Test fun `rango maximo son 366 dias inclusivos y nunca una expansion infinita`() {
        val source = snapshot(series(until = null))
        val result = CalendarEngine.project(source, CalendarRange(monday, monday.plusDays(365), lima))
        assertEquals(53, result.entries.size)
        rejects { CalendarRange(monday, monday.plusDays(366), lima) }
        rejects { CalendarRange(monday, monday.minusDays(1), lima) }
        rejects { CalendarEngine.next(source, instant(monday, 9), lima, horizonDays = 0) }
        rejects { CalendarEngine.next(source, instant(monday, 9), lima, limit = 101) }
    }

    @Test fun `fuente invalida no fabrica bloques ni reemplazos perdidos`() {
        val invalid = block("invalido", instant(monday, 11), instant(monday, 10))
        val result = CalendarEngine.day(snapshot(invalid, series(), exceptions = listOf(RecurrenceException("serie", monday, "ausente"))), monday, lima)
        assertTrue(result.entries.isEmpty())
        assertEquals(2, result.issues.size)
    }

    @Test fun `resolucion estricta exige zona IANA y offset explicito en solapamiento DST`() {
        val gap = LocalDateTime.of(2026, 3, 8, 2, 30)
        val overlap = LocalDateTime.of(2026, 11, 1, 1, 30)
        rejectsDate { StrictLocalTime.resolve(gap, "America/New_York") }
        rejectsDate { StrictLocalTime.resolve(overlap, "America/New_York") }
        val early = StrictLocalTime.resolve(overlap, "America/New_York", ZoneOffset.ofHours(-4))
        val late = StrictLocalTime.resolve(overlap, "America/New_York", ZoneOffset.ofHours(-5))
        assertEquals(3600L, late.epochSecond - early.epochSecond)
        rejectsDate { StrictLocalTime.resolve(overlap, "America/New_York", ZoneOffset.UTC) }
        rejects { StrictLocalTime.resolve(monday.atTime(10, 0), "+02:00") }
        rejects { StrictLocalTime.zone("Zona/Inexistente") }
    }

    private fun rejects(action: () -> Unit) {
        try { action() } catch (_: IllegalArgumentException) { return }
        throw AssertionError("La entrada inválida debe rechazarse.")
    }

    private fun rejectsDate(action: () -> Unit) {
        try { action() } catch (_: DateTimeException) { return }
        throw AssertionError("La hora local inválida o ambigua debe rechazarse.")
    }
}
