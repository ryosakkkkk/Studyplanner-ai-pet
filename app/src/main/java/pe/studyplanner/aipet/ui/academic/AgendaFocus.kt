package pe.studyplanner.aipet.ui.academic

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.studyplanner.aipet.domain.academic.ActivityTime

/** Lo que la mascota acaba de agendar por voz o texto: la Agenda abre ese día, lo marca y ofrece «Deshacer». */
data class AgendaFocus(val activityId: String, val date: LocalDate?, val title: String)

object AgendaFocusBus {
    private val mutable = MutableStateFlow<AgendaFocus?>(null)
    val focus = mutable.asStateFlow()
    fun show(focus: AgendaFocus) { mutable.value = focus }
    fun clear() { mutable.value = null }

    /** Día en que cae una actividad (null si no tiene fecha o se repite cada semana). */
    fun dateOf(time: ActivityTime): LocalDate? = when (time) {
        is ActivityTime.DateOnly -> time.date
        is ActivityTime.Timed -> runCatching { time.start.atZone(ZoneId.of(time.zoneId)).toLocalDate() }.getOrNull()
        else -> null
    }
}
