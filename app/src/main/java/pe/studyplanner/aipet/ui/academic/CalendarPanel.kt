package pe.studyplanner.aipet.ui.academic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.*
import java.time.format.DateTimeFormatter
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.calendar.*
import pe.studyplanner.aipet.domain.profile.UserProfile
import pe.studyplanner.aipet.ui.theme.PetDesign

@Composable
internal fun CalendarPanel(snapshot: AcademicSnapshot, profile: UserProfile, busy: Boolean,
    onEdit: (AcademicActivity) -> Unit, onCancelOccurrence: (CalendarEntry) -> Unit,
    onRestoreOccurrence: (RecurrenceException) -> Unit, onNew: () -> Unit, focus: AgendaFocus? = null) {
    // «Hoy» primero: tira de siete días y lista del día. El calendario completo (día, semana, mes) se abre al pedirlo.
    var mode by rememberSaveable{mutableStateOf("AGENDA")}
    val today=LocalDate.now(ZoneId.of(profile.zoneId))
    var dateText by rememberSaveable{mutableStateOf(today.toString())}
    var courseFilter by rememberSaveable{mutableStateOf("")}
    var kindFilter by rememberSaveable{mutableStateOf("")}
    var includeCompleted by rememberSaveable{mutableStateOf(false)}
    var search by rememberSaveable{mutableStateOf("")}
    var advanced by rememberSaveable{mutableStateOf(false)}
    // Lo recién agendado por la mascota: se abre su día (fuera de las dos semanas de «Hoy», en vista de día).
    LaunchedEffect(focus?.activityId) {
        val day=focus?.date ?: return@LaunchedEffect
        dateText=day.toString(); mode=if(day.isBefore(today) || day.isAfter(today.plusDays(13))) "DAY" else "AGENDA"
    }
    val date=runCatching{require(dateText.matches(Regex("\\d{4}-\\d{2}-\\d{2}")));LocalDate.parse(dateText)}.getOrNull()
    val filter=CalendarFilter(if(courseFilter.isEmpty())null else setOf(courseFilter),if(kindFilter.isEmpty())null else setOf(ActivityKind.valueOf(kindFilter)),includeCompleted)
    val projectionResult=remember(snapshot,date,profile.zoneId,profile.weekStartsOn,mode,courseFilter,kindFilter,includeCompleted){runCatching {
        requireNotNull(date){"Selecciona una fecha válida."}
        when(mode){
            "DAY"->CalendarEngine.day(snapshot,date,profile.zoneId,filter)
            "WEEK"->CalendarEngine.week(snapshot,date,profile.zoneId,profile.weekStartsOn,filter)
            "AGENDA"->CalendarEngine.project(snapshot,CalendarRange(today,today.plusDays(13),profile.zoneId),filter)
            else->CalendarEngine.project(snapshot,CalendarRange(date.withDayOfMonth(1),date.withDayOfMonth(date.lengthOfMonth()),profile.zoneId),filter)
        }
    }}
    val projection=projectionResult.getOrNull()
    val activities=snapshot.activities.associateBy{it.id}
    val allEntries=projection?.entries.orEmpty().filter{entry->search.isBlank() || entry.title.contains(search.trim(),ignoreCase=true) || activities[entry.activityId]?.notes?.contains(search.trim(),ignoreCase=true)==true}
    val entries=if(mode=="MONTH" || mode=="AGENDA")allEntries.filter{it.date==date} else allEntries
    val entryKeys=entries.map{it.key}.toSet()
    val conflicts=projection?.conflicts.orEmpty().filter{it.firstKey in entryKeys && it.secondKey in entryKeys}
    val format=DateTimeFormatter.ofPattern("dd/MM HH:mm",spanishLocale)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(mode=="AGENDA") {
            DayStrip(today,date ?: today,allEntries.map{it.date}.toSet()){dateText=it.toString()}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                val day=(date ?: today)
                Text((if(day==today) "Hoy, " else "")+day.format(DateTimeFormatter.ofPattern("EEEE d",spanishLocale)),
                    style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                TextButton(onClick={mode="MONTH"},modifier=Modifier.heightIn(min=48.dp)){Text("Ver calendario")}
            }
            if(projection==null)Text(projectionResult.exceptionOrNull()?.message?:"No se pudo calcular este rango.",color=MaterialTheme.colorScheme.error)
            else {
                if(conflicts.isNotEmpty())Text("${conflicts.size} horarios superpuestos. Revisa tus actividades.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                if(entries.isEmpty()) Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLowest),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(if(date==today) "No tienes actividades para hoy" else "No tienes actividades este día",style=MaterialTheme.typography.titleMedium)
                        TextButton(onClick=onNew,enabled=!busy,contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp)){Text("Añadir tarea")}
                    }
                }
                entries.forEach{entry->EntryCard(entry,snapshot,profile,busy,activities,conflicts,false,format,onEdit,onCancelOccurrence,entry.activityId==focus?.activityId)}
                if(date==today) {
                    val undated=snapshot.activities.filter{it.time==ActivityTime.Unscheduled && it.status!=ActivityStatus.COMPLETED && it.status!=ActivityStatus.CANCELLED}
                    if(undated.isNotEmpty()) {
                        Text("Sin fecha",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=6.dp))
                        (undated.filter{it.id==focus?.activityId}+undated.filter{it.id!=focus?.activityId}).take(5).forEach{activity->
                            val isNew=activity.id==focus?.activityId
                            OutlinedCard(onClick={onEdit(activity)},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp),
                                border=if(isNew)BorderStroke(2.dp,MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder(!busy)) {
                                Row(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Text("—",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.width(52.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(activity.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                                        Text(activity.kind.spanish(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                val upcoming=allEntries.filter{d->date!=null && d.date>date}.take(3)
                if(upcoming.isNotEmpty()) {
                    Text("Próximos",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=6.dp))
                    upcoming.forEach{entry->EntryCard(entry,snapshot,profile,busy,activities,conflicts,true,format,onEdit,onCancelOccurrence)}
                }
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("AGENDA" to "Hoy","DAY" to "Día","WEEK" to "Semana","MONTH" to "Mes").forEach{(value,label)->FilterChip(selected=mode==value,onClick={mode=value;if(value=="AGENDA")dateText=today.toString()},label={Text(label)},modifier=Modifier.weight(1f))}
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={date?.let{dateText=when(mode){"DAY"->it.minusDays(1);"WEEK"->it.minusDays(7);else->it.minusMonths(1)}.toString()}},enabled=date!=null){Text("‹",style=MaterialTheme.typography.headlineSmall)}
            Text(date?.format(DateTimeFormatter.ofPattern(if(mode=="MONTH")"MMMM yyyy" else "d MMM yyyy",spanishLocale))?.replaceFirstChar{it.titlecase(spanishLocale)} ?: "Selecciona fecha",style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={date?.let{dateText=when(mode){"DAY"->it.plusDays(1);"WEEK"->it.plusDays(7);else->it.plusMonths(1)}.toString()}},enabled=date!=null){Text("›",style=MaterialTheme.typography.headlineSmall)}
        }
        if(mode=="MONTH" && date!=null) MonthGrid(date,profile.weekStartsOn,allEntries.map{it.date}.toSet()){dateText=it.toString()}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={dateText=LocalDate.now(ZoneId.of(profile.zoneId)).toString()}){Text("Hoy")}
            TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Cerrar filtros" else "Filtros y opciones")}
        }
        if(advanced) {
            DateInput("Fecha de referencia",dateText,if(date==null)"Introduce una fecha válida, AAAA-MM-DD." else null,true){dateText=it}
            ChoiceField("Curso del calendario",courseFilter,listOf("" to "Todos los cursos")+snapshot.courses.map{it.id to it.name}){courseFilter=it}
            ChoiceField("Tipo del calendario",kindFilter,listOf("" to "Todos los tipos")+ActivityKind.entries.map{it.name to it.spanish()}){kindFilter=it}
            ToggleField("Incluir actividades completadas",includeCompleted){includeCompleted=it}
            OutlinedTextField(search,{search=it},label={Text("Buscar en el calendario")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Text("${profile.zoneId}. Las tareas indican vencimiento; las fechas sin hora no reservan una franja.",style=MaterialTheme.typography.bodySmall)
        }
        if(projection==null)Text(projectionResult.exceptionOrNull()?.message?:"No se pudo calcular este rango.",color=MaterialTheme.colorScheme.error)
        else {
            Text(if(mode=="MONTH")date!!.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM",spanishLocale)).replaceFirstChar{it.titlecase(spanishLocale)} else "${projection.range.startDate.format(displayDate)}${if(mode=="WEEK")" — ${projection.range.endDateInclusive.format(displayDate)}" else ""}",style=MaterialTheme.typography.titleMedium)
            if(conflicts.isNotEmpty()) {
                Text("${conflicts.size} horarios superpuestos. Revisa tus actividades.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                if(advanced)conflicts.forEach{conflict->
                    val first=entries.firstOrNull{it.key==conflict.firstKey};val second=entries.firstOrNull{it.key==conflict.secondKey}
                    Text("${first?.title?:"Actividad"} y ${second?.title?:"actividad"}: ${conflict.start.atZone(ZoneId.of(profile.zoneId)).format(format)}–${conflict.end.atZone(ZoneId.of(profile.zoneId)).format(format)}.",style=MaterialTheme.typography.bodySmall)
                }
            }
            projection.issues.forEach{issue->Text("Revisar «${activities[issue.activityId]?.title?:"actividad"}»: ${issue.message}",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
            if(entries.isEmpty())Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.3f))) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Un espacio para tus planes",style=MaterialTheme.typography.titleMedium)
                    Text("No hay actividades en esta vista.",style=MaterialTheme.typography.bodyMedium)
                    TextButton(onClick=onNew,enabled=!busy){Text("＋ Agregar actividad")}
                }
            }
            entries.groupBy{it.date}.forEach{(day,dayEntries)->
                if(mode=="WEEK")Text(day.format(DateTimeFormatter.ofPattern("EEEE d",spanishLocale)),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                dayEntries.forEach{entry->EntryCard(entry,snapshot,profile,busy,activities,conflicts,false,format,onEdit,onCancelOccurrence,entry.activityId==focus?.activityId)}
            }
            if(advanced) {
            val omitted = snapshot.exceptions.filter { exception ->
                exception.replacementActivityId == null && runCatching {
                    val rule = (activities[exception.activityId]?.time as? ActivityTime.Weekly)?.rule ?: return@runCatching false
                    val originalStart = StrictLocalTime.resolve(exception.occurrenceDate.atTime(rule.startTime), rule.zoneId)
                    val originalEnd = StrictLocalTime.resolve(exception.occurrenceDate.plusDays(rule.endDayOffset.toLong()).atTime(rule.endTime), rule.zoneId)
                    val windowStart = projection.range.startDate.atStartOfDay(ZoneId.of(profile.zoneId)).toInstant()
                    val windowEnd = projection.range.endDateInclusive.plusDays(1).atStartOfDay(ZoneId.of(profile.zoneId)).toInstant()
                    originalStart < windowEnd && originalEnd > windowStart
                }.getOrDefault(false) &&
                    activities[exception.activityId]?.let { source -> (courseFilter.isBlank() || source.courseId == courseFilter) &&
                        (kindFilter.isBlank() || source.kind.name == kindFilter) && (search.isBlank() || source.title.contains(search.trim(), ignoreCase = true)) } == true
            }
            if (omitted.isNotEmpty()) {
                HorizontalDivider()
                Text("Ocurrencias canceladas en este rango", style = MaterialTheme.typography.titleMedium)
                omitted.forEach { exception ->
                    val seriesZone = (activities[exception.activityId]?.time as? ActivityTime.Weekly)?.rule?.zoneId ?: profile.zoneId
                    Text("${activities[exception.activityId]?.title} · ${exception.occurrenceDate} (fecha local de la serie en $seriesZone)")
                    OutlinedButton(onClick = { onRestoreOccurrence(exception) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Restaurar ocurrencia del ${exception.occurrenceDate}") }
                }
            }
            }
        }
    }
}

@Composable
private fun MonthGrid(selected:LocalDate,weekStart:DayOfWeek,occupied:Set<LocalDate>,onSelect:(LocalDate)->Unit) {
    val first=selected.withDayOfMonth(1)
    val leading=(first.dayOfWeek.value-weekStart.value+7)%7
    val cells=((leading+selected.lengthOfMonth()+6)/7)*7
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.12f)),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth()) {
                repeat(7){offset->val day=DayOfWeek.of((weekStart.value-1+offset)%7+1);Box(Modifier.weight(1f).height(24.dp),contentAlignment=Alignment.Center){Text(day.getDisplayName(java.time.format.TextStyle.NARROW,spanishLocale),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            }
            repeat(cells/7){row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                repeat(7){column->
                    val value=row*7+column-leading+1
                    if(value !in 1..selected.lengthOfMonth())Spacer(Modifier.weight(1f).height(40.dp))
                    else {
                        val date=selected.withDayOfMonth(value);val active=date==selected
                        Surface(onClick={onSelect(date)},modifier=Modifier.weight(1f).height(40.dp),shape=RoundedCornerShape(12.dp),color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,contentColor=if(active)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                Text(value.toString(),style=MaterialTheme.typography.bodySmall,fontWeight=if(active)FontWeight.Bold else FontWeight.Normal)
                                if(date in occupied)Text("•",style=MaterialTheme.typography.labelSmall,color=if(active)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }}
        }
    }
}

@Composable
private fun EntryCard(entry: CalendarEntry, snapshot: AcademicSnapshot, profile: UserProfile, busy: Boolean, activities: Map<String, AcademicActivity>,
    conflicts: List<CalendarConflict>, showDate: Boolean, format: DateTimeFormatter, onEdit: (AcademicActivity) -> Unit, onCancelOccurrence: (CalendarEntry) -> Unit,
    isNew: Boolean = false) {
    val advanced=false
                    var menu by remember(entry.key,entry.date){mutableStateOf(false)}
                    val accent=when(entry.kind){ActivityKind.TASK->PetDesign.Blue;ActivityKind.EXAM->PetDesign.Pink;ActivityKind.STUDY->PetDesign.Violet;ActivityKind.SPORT->PetDesign.Turquoise;else->PetDesign.Orange}
                    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),border=if(isNew)BorderStroke(2.dp,accent) else BorderStroke(1.dp,accent.copy(alpha=.2f)),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
                            Column(Modifier.width(56.dp)) {
                                if(showDate)Text(entry.date.format(DateTimeFormatter.ofPattern("EEE d",spanishLocale)),style=MaterialTheme.typography.labelMedium,color=accent)
                                Text(if(entry.dateOnly)(if(showDate)"" else "Sin hora") else entry.start?.atZone(ZoneId.of(profile.zoneId))?.format(DateTimeFormatter.ofPattern("HH:mm"))?:"—",style=MaterialTheme.typography.labelLarge,color=accent)
                            }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(entry.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                                if(isNew)Text("Nuevo",style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold,color=accent)
                                Text("${entry.kind.spanish()}${snapshot.courses.firstOrNull{it.id==entry.courseId}?.name?.let{" · $it"}?:""}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                if(entry.status==ActivityStatus.COMPLETED)Text("Completada",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.tertiary)
                                if(conflicts.any{it.firstKey==entry.key || it.secondKey==entry.key})Text("Horario superpuesto",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error)
                                if(advanced) {
                                    if(entry.sourceZoneId!=null && entry.sourceZoneId!=profile.zoneId)Text("Origen: ${entry.sourceZoneId}",style=MaterialTheme.typography.bodySmall)
                                    entry.end?.let{Text("Hasta ${it.atZone(ZoneId.of(profile.zoneId)).format(format)}",style=MaterialTheme.typography.bodySmall)}
                                }
                            }
                            Box {
                                TextButton(onClick={menu=true},contentPadding=PaddingValues(0.dp)){Text("•••")}
                                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                                    (activities[entry.ruleId?:entry.activityId]?:activities[entry.activityId])?.let{activity->DropdownMenuItem(text={Text(if(entry.ruleId==null)"Editar actividad" else "Editar toda la serie")},onClick={menu=false;onEdit(activity)},enabled=!busy)}
                                    if(entry.ruleId!=null && entry.activityId!=entry.ruleId)activities[entry.activityId]?.let{replacement->DropdownMenuItem(text={Text("Editar reemplazo")},onClick={menu=false;onEdit(replacement)},enabled=!busy)}
                                    if(entry.ruleId!=null && entry.occurrenceDate!=null)DropdownMenuItem(text={Text("Cancelar esta ocurrencia")},onClick={menu=false;onCancelOccurrence(entry)},enabled=!busy)
                                }
                            }
                        }
                    }
}

/** Tira de siete días desde hoy. Cada día es un objetivo táctil de 48 dp o más y dice si tiene actividades. */
@Composable
private fun DayStrip(today: LocalDate, selected: LocalDate, occupied: Set<LocalDate>, onSelect: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        (0L..6L).map{today.plusDays(it)}.forEach{day->
            val active=day==selected
            val label=if(day==today) "Hoy" else day.format(DateTimeFormatter.ofPattern("EEE",spanishLocale)).removeSuffix(".")
            Surface(onClick={onSelect(day)},modifier=Modifier.weight(1f).heightIn(min=64.dp).semantics{contentDescription=day.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM",spanishLocale))+if(day in occupied) ", con actividades" else ""},
                shape=RoundedCornerShape(16.dp),color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor=if(active)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border=if(active)null else BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(label,style=MaterialTheme.typography.labelSmall,maxLines=1)
                    Text(day.dayOfMonth.toString(),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Box(Modifier.size(5.dp).background(if(day in occupied)(if(active)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary) else androidx.compose.ui.graphics.Color.Transparent,RoundedCornerShape(50)))
                }
            }
        }
    }
}
