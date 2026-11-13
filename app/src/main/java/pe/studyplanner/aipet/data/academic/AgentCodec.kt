package pe.studyplanner.aipet.data.academic

import org.json.JSONObject
import java.security.MessageDigest
import java.time.*
import java.util.UUID
import pe.studyplanner.aipet.domain.academic.*
import pe.studyplanner.aipet.domain.agent.*
import pe.studyplanner.aipet.domain.alerts.AlertPrecision
import pe.studyplanner.aipet.domain.profile.ProfileValidator
import pe.studyplanner.aipet.domain.profile.UserProfile

/** Closed, versioned transport contract. Untrusted provider fields never select a dataset or executor. */
object AgentCodec {
    fun tool(action:AgentAction):String=when(action) {
        is AgentAction.CreateTask->"create_task";is AgentAction.CreateExam->"create_exam"
        is AgentAction.RenameTask->"rename_task";is AgentAction.CompleteTask->"complete_task"
        is AgentAction.DeleteTask->"delete_task";is AgentAction.SetAlarm->if(action.reminder) "set_reminder" else "set_alarm"
        is AgentAction.CancelAlarm->"cancel_alarm";is AgentAction.PlanWeek->"plan_week"
        is AgentAction.ChangePetName->"change_pet_name";AgentAction.HidePet->"hide_pet"
    }
    fun encode(action:AgentAction):String {
        val a=JSONObject()
        when(action) {
            is AgentAction.CreateTask->{a.put("title",action.title).put("time",time(action.time)).put("priority",action.priority.name);action.courseId?.let{a.put("courseId",it)};action.estimateMinutes?.let{a.put("estimateMinutes",it)}}
            is AgentAction.CreateExam->{a.put("title",action.title).put("time",time(action.time));action.courseId?.let{a.put("courseId",it)}}
            is AgentAction.RenameTask->a.put("id",action.id).put("version",action.version).put("title",action.title)
            is AgentAction.CompleteTask->a.put("id",action.id).put("version",action.version)
            is AgentAction.DeleteTask->a.put("id",action.id).put("version",action.version)
            is AgentAction.SetAlarm->a.put("title",action.title).put("at",action.at.toString()).put("zone",action.zone).put("precision",action.precision.name)
            is AgentAction.CancelAlarm->a.put("id",action.id).put("generation",action.generation)
            is AgentAction.PlanWeek->a.put("start",action.start.toString()).put("days",action.days)
            is AgentAction.ChangePetName->a.put("name",action.name)
            AgentAction.HidePet->Unit
        }
        return JSONObject().put("v",1).put("tool",tool(action)).put("arguments",a).toString()
    }
    fun hash(action:AgentAction):String=MessageDigest.getInstance("SHA-256").digest(encode(action).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    fun uuid(raw:String):String { require(runCatching{UUID.fromString(raw).toString()==raw}.getOrDefault(false)){"Identificador UUID inválido o referencia provisional."};return raw }
    fun decode(raw:String):AgentAction {
        require(raw.toByteArray(Charsets.UTF_8).size<=65536){"La propuesta supera 64 KiB."}
        StrictJson(raw).validate()
        val o=JSONObject(raw);keys(o,setOf("v","tool","arguments"));require(number(o,"v")==1L){"Versión de acción no compatible."}
        val a=o.get("arguments");require(a is JSONObject){"arguments debe ser un objeto."}
        fun fields(required:Set<String>,optional:Set<String> = emptySet())=keys(a,required,optional)
        fun id()=uuid(string(a,"id"))
        fun version(name:String="version")=number(a,name).also{require(it>=1){"Versión o generación inválida."}}
        fun course()=if(a.has("courseId"))uuid(string(a,"courseId"))else null
        return when(string(o,"tool")) {
            "create_task"->{fields(setOf("title"),setOf("time","priority","courseId","estimateMinutes"));AgentAction.CreateTask(title(a),if(a.has("time"))readTime(obj(a,"time"))else ActivityTime.Unscheduled,if(a.has("priority"))Priority.valueOf(string(a,"priority"))else Priority.MEDIUM,course(),if(a.has("estimateMinutes"))number(a,"estimateMinutes").also{require(it in 1..10080)}.toInt()else null)}
            "create_exam"->{fields(setOf("title","time"),setOf("courseId"));AgentAction.CreateExam(title(a),readTime(obj(a,"time")).also{require(it!=ActivityTime.Unscheduled){"Un examen necesita fecha u horario completo."};if(it is ActivityTime.Timed)require(it.end!=null && it.end>it.start){"El examen necesita una hora final explícita."}},course())}
            "rename_task"->{fields(setOf("id","version","title"));AgentAction.RenameTask(id(),version(),title(a))}
            "complete_task"->{fields(setOf("id","version"));AgentAction.CompleteTask(id(),version())}
            "delete_task"->{fields(setOf("id","version"));AgentAction.DeleteTask(id(),version())}
            "set_alarm","set_reminder"->{fields(setOf("title","at","zone","precision"));val zone=string(a,"zone");ZoneId.of(zone);AgentAction.SetAlarm(title(a),Instant.parse(string(a,"at")),zone,AlertPrecision.valueOf(string(a,"precision")),string(o,"tool")=="set_reminder")}
            "cancel_alarm"->{fields(setOf("id","generation"));AgentAction.CancelAlarm(id(),version("generation"))}
            "plan_week"->{fields(setOf("start"),setOf("days"));AgentAction.PlanWeek(LocalDate.parse(string(a,"start")),if(a.has("days"))number(a,"days").also{require(it in 1..31)}.toInt()else 7)}
            "change_pet_name"->{fields(setOf("name"));val name=string(a,"name");require(ProfileValidator.validate(UserProfile(petName=name))["petName"]==null){"El nombre de la mascota no es válido."};AgentAction.ChangePetName(name)}
            "hide_pet"->{fields(emptySet());AgentAction.HidePet}
            else->error("Herramienta no habilitada en este corte.")
        }
    }
    private fun title(o:JSONObject)=string(o,"title").also{require(it.trim().isNotEmpty() && it.length<=160){"El título debe tener entre 1 y 160 caracteres."}}
    private fun time(t:ActivityTime):JSONObject=when(t) {
        ActivityTime.Unscheduled->JSONObject().put("type","none")
        is ActivityTime.DateOnly->JSONObject().put("type","date").put("date",t.date.toString())
        is ActivityTime.Timed->JSONObject().put("type","timed").put("start",t.start.toString()).put("zone",t.zoneId).also{o->t.end?.let{o.put("end",it.toString())}}
        is ActivityTime.Weekly->error("El agente no crea series en este corte.")
    }
    private fun readTime(o:JSONObject):ActivityTime=when(string(o,"type")) {
        "none"->{keys(o,setOf("type"));ActivityTime.Unscheduled}
        "date"->{keys(o,setOf("type","date"));ActivityTime.DateOnly(LocalDate.parse(string(o,"date")))}
        "timed"->{keys(o,setOf("type","start","zone"),setOf("end"));val start=Instant.parse(string(o,"start"));val end=if(o.has("end"))Instant.parse(string(o,"end"))else null;require(end==null || end>start){"El final debe ser posterior al inicio."};val zone=string(o,"zone");ZoneId.of(zone);ActivityTime.Timed(start,end,zone)}
        else->error("Tipo temporal no permitido para el agente.")
    }
    private fun keys(o:JSONObject,required:Set<String>,optional:Set<String> = emptySet()) { val present=o.keys().asSequence().toSet();require(present.containsAll(required) && present.all{it in required || it in optional}){"Campos ausentes o desconocidos en la acción."};require(present.none{o.isNull(it)}){"No se admiten valores null."} }
    private fun obj(o:JSONObject,key:String)=o.get(key).also{require(it is JSONObject){"$key debe ser un objeto."}} as JSONObject
    private fun string(o:JSONObject,key:String)=o.get(key).also{require(it is String){"$key debe ser texto."}} as String
    private fun number(o:JSONObject,key:String):Long { val n=o.get(key);require(n is Int || n is Long){"$key debe ser un entero JSON."};return (n as Number).toLong() }

    /** Android JSONObject is permissive: first reject comments, duplicates, coercions and trailing data. */
    private class StrictJson(val input:String) {
        var p=0
        fun validate(){ws();value(0);ws();require(p==input.length){"Contenido después del JSON."}}
        fun ws(){while(p<input.length && input[p] in " \r\n\t")p++}
        fun value(depth:Int){require(depth<16){"JSON demasiado anidado."};ws();require(p<input.length);when(input[p]) {
            '{'->{p++;ws();val names=mutableSetOf<String>();if(take('}'))return;while(true){ws();require(p<input.length && input[p]=='"');val name=quoted();require(names.add(name)){"Campo JSON duplicado."};ws();require(take(':'));value(depth+1);ws();if(take('}'))break;require(take(','))}}
            '['->{p++;ws();if(take(']'))return;while(true){value(depth+1);ws();if(take(']'))break;require(take(','))}}
            '"'->quoted()
            't'->literal("true");'f'->literal("false");'n'->literal("null")
            else->{val begin=p;if(input[p]=='-')p++;require(p<input.length);if(input[p]=='0')p++ else {require(input[p] in '1'..'9');while(p<input.length && input[p].isDigit())p++};if(p<input.length && input[p]=='.'){p++;require(p<input.length && input[p].isDigit());while(p<input.length && input[p].isDigit())p++};if(p<input.length && input[p] in "eE"){p++;if(p<input.length && input[p] in "+-")p++;require(p<input.length && input[p].isDigit());while(p<input.length && input[p].isDigit())p++};require(p>begin)}
        }}
        fun take(c:Char):Boolean=if(p<input.length && input[p]==c){p++;true}else false
        fun literal(text:String){require(input.startsWith(text,p));p+=text.length}
        fun quoted():String { val start=p;require(take('"'));while(p<input.length){val c=input[p++];if(c=='"')return org.json.JSONTokener(input.substring(start,p)).nextValue() as String;require(c.code>=32){"Control sin escapar en JSON."};if(c=='\\'){require(p<input.length);val escape=input[p++];require(escape in "\"\\/bfnrtu");if(escape=='u'){require(p+4<=input.length && input.substring(p,p+4).all{it in "0123456789abcdefABCDEF"});p+=4}}};error("Texto JSON sin cerrar.") }
    }
}
