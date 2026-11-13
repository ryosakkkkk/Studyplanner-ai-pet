package pe.studyplanner.aipet.domain.agent

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.alerts.AlertPrecision
import pe.studyplanner.aipet.domain.calendar.StrictLocalTime
import pe.studyplanner.aipet.domain.profile.ProfileValidator
import pe.studyplanner.aipet.domain.profile.UserProfile

/** Bounded local grammar. It only returns candidates; it has no repositories or platform handles. */
class LocalRulesFallback : AgentProvider {
    override val mode: String = "DEMO_SIN_IA"

    override suspend fun interpret(text: String, context: AgentContext): AgentInterpretation {
        if (text.length > 4000) return clarify("Usa una sola solicitud de hasta 4000 caracteres.")
        val input = text.trim()
        if (input.isEmpty()) return clarify("Escribe una solicitud. Por ejemplo: muestra mis pendientes.")
        if (input.any { Character.isISOControl(it) || it == '\u2028' || it == '\u2029' })
            return clarify("Envía una sola instrucción sin saltos de línea ni caracteres de control.")
        return try {
            val today = context.now.atZone(StrictLocalTime.zone(context.zoneId)).toLocalDate()
            interpretOne(spokenTitle(spokenAlias(input)), context, today)
        } catch (error: ParseIssue) {
            clarify(error.message.orEmpty())
        } catch (error: java.time.DateTimeException) {
            clarify("Revisa la fecha, la hora y la zona. ${error.message.orEmpty()}")
        } catch (error: IllegalArgumentException) {
            clarify(error.message ?: "Revisa los datos o utiliza el formulario correspondiente.")
        }
    }

    /** Frases habladas frecuentes que equivalen a una consulta o plan local. Lo demás se conserva igual. */
    internal fun spokenAlias(input: String): String {
        val clean = input.trim().trimStart('¿', '¡').trimEnd('?', '!', '.', ' ').trim()
        val body = java.text.Normalizer.normalize(clean.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[,;:¿?¡!.]"), " ").replace(Regex("\\s+"), " ").trim()
            .removePrefix("por favor ").removeSuffix(" por favor").trim()
        fun any(vararg patterns: String) = patterns.any { Regex(it).matches(body) }
        return when {
            any("(que|cual(es)?) (tengo|hay)( que hacer)?( (para|de))? hoy", "mi dia", "(mi )?agenda (de )?hoy", "que hago hoy") -> "muestra mi agenda hoy"
            any("(que|cual(es)?) (tengo|hay)( que hacer)?( (para|de))? manana", "(mi )?agenda (de )?manana") -> "muestra mi agenda mañana"
            any("(que|cual(es)?) (tengo|hay)( que hacer)?( (para|de))? esta semana", "(mi )?agenda (de )?esta semana") -> "muestra mi agenda esta semana"
            any("(que|cuales) (tareas|pendientes) tengo", "(mis )?tareas", "que me falta( hacer)?", "que tengo pendiente") -> "muestra mis pendientes"
            any("(organiza|ordena|planifica|planea)( me)? (mi|la) semana", "ayudame a (organizar|planificar) (mi|la) semana") -> "planifica mi semana"
            any("(que|cuales) (alarmas|recordatorios|avisos) tengo", "mis (alarmas|recordatorios|avisos)") -> "muestra mis alarmas"
            any("(que|cuales) cursos tengo", "mis cursos") -> "muestra mis cursos"
            else -> input
        }
    }

    /** Accept a bounded spoken title without requiring the speaker to dictate quotation marks. */
    private fun spokenTitle(raw: String): String {
        // «Agéndame…», «anótame…», «apúntame…»: es lo mismo que crear una tarea.
        val agenda = Regex("""^(?:ag[eé]nd(?:a|ame|alo|ar)|an[oó]ta(?:me|lo)?|ap[uú]nta(?:me|lo)?)\s+(?:una?\s+)?(?:tarea\s+)?(.+)$""", RegexOption.IGNORE_CASE).matchEntire(raw.trim())
        val input = if (agenda != null) "crea una tarea " + agenda.groupValues[1] else raw
        if ('"' in input || '“' in input || '”' in input) return input
        val command = Regex("""^((?:crea|crear|registra|agrega|añade) (?:una? )?(?:tarea|examen)|(?:crea|programa|pon) (?:una? )?(?:alarma|recordatorio)|recu[eé]rdame)\s+(.+)$""", RegexOption.IGNORE_CASE).matchEntire(input) ?: return input
        val remainder = command.groupValues[2]
        val clause = Regex("""\s+(?=para\s+(?:hoy|ma[nñ]ana|\d)|(?:hoy|ma[nñ]ana)(?:\s|$)|en\s+\d+\s+minutos?\b)""", RegexOption.IGNORE_CASE).find(remainder) ?: return input
        val title = remainder.substring(0, clause.range.first).trim()
        if (title.isBlank() || Regex("""\b(?:y|luego|despu[eé]s)\s+(?:crea|borra|elimina|programa|recu[eé]rdame)\b""", RegexOption.IGNORE_CASE).containsMatchIn(remainder)) return input
        val tail = remainder.substring(clause.range.last + 1)
        return command.groupValues[1] + " \"" + title + "\" " + tail
    }

    private fun interpretOne(input: String, context: AgentContext, today: LocalDate): AgentInterpretation {
        val queryPrefix = "(?:(?:muestra|consulta|ver)\\s+)?(?:mis\\s+)?"
        if (matches("${queryPrefix}(?:pendientes|tareas pendientes)\\??", input))
            return AgentInterpretation.Query(AgentQuery.Tasks)
        if (matches("${queryPrefix}cursos\\??", input))
            return AgentInterpretation.Query(AgentQuery.Courses)
        if (matches("${queryPrefix}(?:avisos|alarmas|recordatorios)\\??", input))
            return AgentInterpretation.Query(AgentQuery.Alarms)
        match("(?:(?:muestra|consulta|ver)\\s+)?(?:mi\\s+)?(?:calendario|agenda)(?:\\s+de)?\\s+(hoy|ma[nñ]ana|esta semana)\\??", input)?.let {
            val term = it.groupValues[1].lowercase()
            val start = when (term) {
                "hoy" -> today
                "mañana", "manana" -> today.plusDays(1)
                else -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            }
            return AgentInterpretation.Query(AgentQuery.Calendar(start, if (term == "esta semana") 7 else 1))
        }
        if (matches("organiza mis pendientes de esta semana|planifica esta semana", input))
            return candidate(AgentAction.PlanWeek(today, 8 - today.dayOfWeek.value))
        if (matches("planifica mi semana", input)) return candidate(AgentAction.PlanWeek(today, 7))

        match("(?:ll[aá]mate|cambia tu nombre a) \\\"([^\\\"]+)\\\"", input)?.let {
            val name = it.groupValues[1].trim()
            val profile = UserProfile(petName = name, zoneId = context.zoneId)
            ProfileValidator.validate(profile)["petName"]?.let { problem -> issue(problem) }
            return candidate(AgentAction.ChangePetName(ProfileValidator.normalize(profile).petName))
        }
        if (matches("(?:oculta|ocultar|esconde) (?:la )?mascota", input)) return candidate(AgentAction.HidePet)

        match("(?:renombra|renombrar) (?:la )?tarea \\\"([^\\\"]+)\\\" a \\\"([^\\\"]+)\\\"", input)?.let {
            val task = uniqueTask(it.groupValues[1], context)
            return candidate(AgentAction.RenameTask(task.id, task.version, title(it.groupValues[2])))
        }
        match("(?:completa|completar|marca como completada) (?:la )?tarea \\\"([^\\\"]+)\\\"", input)?.let {
            val task = uniqueTask(it.groupValues[1], context)
            if (task.status !in listOf(ActivityStatus.TODO, ActivityStatus.IN_PROGRESS))
                issue("La tarea ya está completada o cancelada. Revisa su estado en Pendientes.")
            return candidate(AgentAction.CompleteTask(task.id, task.version))
        }
        match("(?:elimina|eliminar|borra|borrar) (?:la )?tarea \\\"([^\\\"]+)\\\"", input)?.let {
            val task = uniqueTask(it.groupValues[1], context)
            return candidate(AgentAction.DeleteTask(task.id, task.version))
        }
        match("(?:cancela|cancelar|elimina|eliminar) (?:la |el )?(?:alarma|recordatorio|aviso) \\\"([^\\\"]+)\\\"", input)?.let {
            val selected = context.alerts.filter { alert -> alert.enabled && alert.title == it.groupValues[1] }
            if (selected.size != 1) issue(if (selected.isEmpty())
                "No hay un aviso habilitado con ese título exacto en este espacio. Abre Alarmas y recordatorios."
                else "Hay varios avisos habilitados con ese título. Elige uno en Alarmas y recordatorios.")
            return candidate(AgentAction.CancelAlarm(selected.single().id, selected.single().generation))
        }

        match("(?:crea|crear|registra|registrar|a[nñ]ade|agrega) (?:una )?tarea \\\"([^\\\"]+)\\\"(.*)", input)?.let {
            val title = title(it.groupValues[1])
            val options = options(it.groupValues[2], context, today, setOf(Clause.DATE, Clause.TIME, Clause.PRIORITY, Clause.MINUTES, Clause.COURSE))
            val temporal = activityTime(options, context.zoneId, exam = false)
            return candidate(AgentAction.CreateTask(title, temporal, options.priority ?: Priority.MEDIUM,
                options.courseId, options.minutes))
        }
        match("(?:crea|crear|registra|registrar|a[nñ]ade|agrega) (?:un )?examen \\\"([^\\\"]+)\\\"(.*)", input)?.let {
            val title = title(it.groupValues[1])
            val options = options(it.groupValues[2], context, today, setOf(Clause.DATE, Clause.TIME, Clause.MINUTES, Clause.COURSE))
            return candidate(AgentAction.CreateExam(title, activityTime(options, context.zoneId, exam = true), options.courseId))
        }
        match("(?:crea|crear|programa|programar|pon) (?:una |un )?(alarma|recordatorio) \\\"([^\\\"]+)\\\"(.*)", input)?.let {
            return alarm(it.groupValues[2], it.groupValues[3], it.groupValues[1].equals("recordatorio", true), context, today)
        }
        match("recu[eé]rdame \\\"([^\\\"]+)\\\"(.*)", input)?.let {
            return alarm(it.groupValues[1], it.groupValues[2], true, context, today)
        }

        if (matches("(?:borra|borrar|elimina|eliminar|cancela|cancelar)\\s+(?:todo|todas(?: las)? tareas|todos(?: los)? avisos).*", input))
            return clarify("El modo local no admite borrado masivo. Selecciona cada elemento en su formulario y revisa la confirmación.")
        return clarify("Modo DEMO_SIN_IA: uso reglas locales, no comprendo texto libre. Usa el formulario o una sola orden como: crea una tarea \"Lectura\" para mañana. Las horas deben escribirse en formato de 24 horas, HH:mm.")
    }

    private fun alarm(rawTitle: String, tail: String, reminder: Boolean, context: AgentContext, today: LocalDate): AgentInterpretation {
        val name = title(rawTitle)
        val options = options(tail, context, today, setOf(Clause.DATE, Clause.TIME, Clause.RELATIVE, Clause.PRECISION))
        val at: Instant = if (options.relativeMinutes != null) {
            if (options.date != null || options.time != null) issue("Elige fecha y hora o tiempo relativo; no ambos.")
            context.now.plusSeconds(options.relativeMinutes!! * 60L)
        } else {
            val date = options.date ?: issue("Indica la fecha del aviso: hoy, mañana o YYYY-MM-DD; no asumiré un día.")
            val hour = options.time ?: issue("Indica una hora de 24 horas, HH:mm. Por ejemplo, 06:00 o 18:00; no asumiré AM o PM.")
            StrictLocalTime.resolve(date.atTime(hour), context.zoneId)
        }
        if (at <= context.now) issue("El aviso debe quedar en el futuro. Elige una fecha y hora posterior.")
        return candidate(AgentAction.SetAlarm(name, at, context.zoneId, options.precision ?: AlertPrecision.EXACT, reminder))
    }

    private fun activityTime(options: Options, zone: String, exam: Boolean): ActivityTime {
        val date = options.date
        if (date == null) {
            if (exam) issue("El examen necesita una fecha. Indica hoy, mañana o YYYY-MM-DD, o usa el formulario.")
            if (options.time != null) issue("Indica también la fecha de la tarea; no asumiré un día para esa hora.")
            return ActivityTime.Unscheduled
        }
        val time = options.time ?: run {
            if (exam && options.minutes != null) issue("Para un examen con duración indica también la hora HH:mm, o usa el formulario.")
            return ActivityTime.DateOnly(date)
        }
        val start = StrictLocalTime.resolve(date.atTime(time), zone)
        val end = if (exam) {
            val minutes = options.minutes ?: issue("Para un examen con hora indica su duración explícita en minutos, o usa el formulario; no inventaré la hora de fin.")
            start.plusSeconds(minutes * 60L)
        } else null
        return ActivityTime.Timed(start, end, zone)
    }

    private enum class Clause { DATE, TIME, PRIORITY, MINUTES, COURSE, PRECISION, RELATIVE }
    private data class Options(var date: LocalDate? = null, var time: LocalTime? = null,
        var priority: Priority? = null, var minutes: Int? = null, var courseId: String? = null,
        var precision: AlertPrecision? = null, var relativeMinutes: Int? = null)

    /** Every character of the suffix must belong to an admitted clause; nothing is silently discarded. */
    private fun options(tail: String, context: AgentContext, today: LocalDate, allowed: Set<Clause>): Options {
        if (tail.isNotEmpty() && !tail.first().isWhitespace()) issue("Separa cada dato con un espacio o utiliza el formulario.")
        var rest = tail.trimStart()
        val found = mutableSetOf<Clause>()
        val result = Options()
        fun consume(clause: Clause, expression: String, apply: (MatchResult) -> Unit): Boolean {
            val match = regex("^(?:$expression)(?=\\s|$)").find(rest) ?: return false
            if (clause !in allowed) issue("Ese dato no corresponde a esta orden. Usa el formulario para configurar más opciones.")
            if (!found.add(clause)) issue("Has indicado el mismo dato más de una vez. Envía una sola fecha, hora y valor por opción.")
            apply(match)
            rest = rest.substring(match.range.last + 1).trimStart()
            return true
        }
        while (rest.isNotEmpty()) {
            if (consume(Clause.DATE, "(?:(?:para|el)\\s+)?(hoy|ma[nñ]ana|\\d{4}-\\d{2}-\\d{2}|\\d{2}-\\d{2}-\\d{4})") {
                result.date = when (it.groupValues[1].lowercase()) {
                    "hoy" -> today
                    "mañana", "manana" -> today.plusDays(1)
                    else -> parseDate(it.groupValues[1])
                }
            }) continue
            if (consume(Clause.TIME, "(?:a\\s+las?|hora)\\s+(\\d{2}:\\d{2})") {
                result.time = LocalTime.parse(it.groupValues[1], timeFormat)
            }) continue
            if (consume(Clause.PRIORITY, "prioridad\\s+(alta|media|baja)") {
                result.priority = when (it.groupValues[1].lowercase()) { "alta" -> Priority.HIGH; "baja" -> Priority.LOW; else -> Priority.MEDIUM }
            }) continue
            if (consume(Clause.MINUTES, "(?:duraci[oó]n|estimaci[oó]n)\\s+(\\d{1,10})\\s+minutos?") {
                result.minutes = minutes(it.groupValues[1])
            }) continue
            if (consume(Clause.COURSE, "(?:del\\s+)?curso\\s+\\\"([^\\\"]+)\\\"") {
                val name = it.groupValues[1]
                val courses = context.snapshot.courses.filter { course -> !course.archived && course.name == name }
                if (courses.size != 1) issue(if (courses.isEmpty())
                    "No existe un curso activo con ese nombre exacto en este espacio. Revisa Cursos o usa el formulario."
                    else "Hay varios cursos activos con ese nombre exacto. Elige uno en el formulario.")
                result.courseId = courses.single().id
            }) continue
            if (consume(Clause.PRECISION, "(?:precisi[oó]n\\s+)?(exacta|exacto|exact|flexible|inexacta|inexacto|inexact)") {
                result.precision = if (it.groupValues[1].startsWith("exact", true)) AlertPrecision.EXACT else AlertPrecision.FLEXIBLE
            }) continue
            if (consume(Clause.RELATIVE, "en\\s+(\\d{1,10})\\s+minutos?") { result.relativeMinutes = minutes(it.groupValues[1]) }) continue
            issue("No pude interpretar todos los datos sin adivinar. Envía una sola orden, usa títulos entre comillas, fecha YYYY-MM-DD y hora HH:mm; también puedes usar el formulario.")
        }
        return result
    }

    private fun uniqueTask(title: String, context: AgentContext): AcademicActivity {
        val selected = context.snapshot.activities.filter { it.kind == ActivityKind.TASK && it.title == title }
        if (selected.size != 1) issue(if (selected.isEmpty())
            "No hay una tarea con ese título exacto en este espacio. Revisa Pendientes."
            else "Hay varias tareas con ese título exacto. Selecciona una en Pendientes.")
        return selected.single()
    }

    private fun parseDate(value: String): LocalDate = LocalDate.parse(value,
        if (value.indexOf('-') == 4) isoDateFormat else dayFirstDateFormat)
    private fun minutes(value: String): Int = value.toIntOrNull()?.takeIf { it in 1..10080 }
        ?: issue("La duración debe ser un número entero entre 1 y 10080 minutos.")
    private fun title(value: String): String = value.trim().also {
        if (it.isEmpty() || it.length > 160) issue("Escribe un título o nombre de 1 a 160 caracteres.")
    }
    private fun candidate(action: AgentAction) = AgentInterpretation.Candidate(action)
    private fun clarify(message: String) = AgentInterpretation.Clarification(message)
    private fun regex(value: String) = Regex(value, RegexOption.IGNORE_CASE)
    private fun matches(expression: String, input: String) = regex(expression).matches(input)
    private fun match(expression: String, input: String) = regex(expression).matchEntire(input)
    private fun issue(message: String): Nothing = throw ParseIssue(message)
    private class ParseIssue(message: String) : RuntimeException(message)
    private companion object {
        val isoDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
        val dayFirstDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT)
        val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)
    }
}
