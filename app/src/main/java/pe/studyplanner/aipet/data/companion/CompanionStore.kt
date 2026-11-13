package pe.studyplanner.aipet.data.companion

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import pe.studyplanner.aipet.domain.focus.FocusTimer

data class Habit(val id:String,val title:String,val dates:Set<String> = emptySet())
data class FocusBlock(val id:String,val minutes:Int,val endedAt:Long)
data class CompanionData(val timer:FocusTimer=FocusTimer(),val habits:List<Habit> = emptyList(),val blocks:List<FocusBlock> = emptyList(),val error:String="")

class CompanionStore internal constructor(context:Context, preferencesName:String="companion_v2") {
    private val preferences=context.applicationContext.getSharedPreferences(preferencesName,Context.MODE_PRIVATE)
    private val mutable=MutableStateFlow(read())
    val state=mutable.asStateFlow()
    init { finishDue(System.currentTimeMillis()) }
    private fun read():CompanionData=try {
        val raw=preferences.getString("data",null)
        if(raw==null) CompanionData() else {
            val j=JSONObject(raw);val t=j.getJSONObject("timer")
            val habits=j.getJSONArray("habits");val blocks=j.getJSONArray("blocks")
            CompanionData(FocusTimer(t.getString("id"),t.getInt("minutes"),t.getBoolean("break"),t.getLong("remaining"),if(t.isNull("deadline"))null else t.getLong("deadline")),
                (0 until habits.length()).map { i->val h=habits.getJSONObject(i);val dates=h.getJSONArray("dates");Habit(h.getString("id"),h.getString("title"),(0 until dates.length()).map{dates.getString(it)}.toSet()) },
                (0 until blocks.length()).map { i->val b=blocks.getJSONObject(i);FocusBlock(b.getString("id"),b.getInt("minutes"),b.getLong("ended")) })
        }
    } catch(e:Exception){CompanionData(error="No pudimos leer hábitos y enfoque. Tus datos se conservan; vuelve a abrir la app para reintentar.")}
    @Synchronized private fun update(change:(CompanionData)->CompanionData) {
        val old=mutable.value;if(old.error.isNotBlank())return
        val next=change(old);if(next==old)return;val t=next.timer
        val j=JSONObject().put("timer",JSONObject().put("id",t.id).put("minutes",t.workMinutes).put("break",t.breakMode).put("remaining",t.remainingMillis).put("deadline",t.deadline ?: JSONObject.NULL))
        j.put("habits",JSONArray().apply { next.habits.forEach{h->put(JSONObject().put("id",h.id).put("title",h.title).put("dates",JSONArray(h.dates.sorted())))} })
        j.put("blocks",JSONArray().apply{next.blocks.forEach{b->put(JSONObject().put("id",b.id).put("minutes",b.minutes).put("ended",b.endedAt))}})
        if(preferences.edit().putString("data",j.toString()).commit()) mutable.value=next
        else mutable.value=old.copy(error="No se pudo guardar. Tus registros anteriores se conservan; vuelve a abrir la app.")
    }
    private fun advance(d:CompanionData,now:Long):CompanionData {
        val t=d.timer
        if(t.deadline==null || t.remaining(now)>0)return d
        val blocks=if(!t.breakMode && d.blocks.none{it.id==t.id})d.blocks+FocusBlock(t.id,t.workMinutes,t.deadline) else d.blocks
        return d.copy(timer=if(t.breakMode)FocusTimer(workMinutes=t.workMinutes,remainingMillis=t.workMinutes*60_000L) else FocusTimer(workMinutes=t.workMinutes,breakMode=true,remainingMillis=5*60_000L),blocks=blocks)
    }
    fun toggleTimer(now:Long)=update { d->
        val advanced=advance(d,now)
        if(advanced!=d)advanced else {val t=d.timer;d.copy(timer=if(t.deadline==null)t.copy(id=t.id.ifBlank{UUID.randomUUID().toString()}).start(now) else t.pause(now))}
    }
    fun resetTimer(minutes:Int)=update{d->val rest=d.timer.breakMode;d.copy(timer=FocusTimer(workMinutes=minutes,breakMode=rest,remainingMillis=(if(rest)5 else minutes)*60_000L))}
    fun setMode(rest:Boolean)=update{d->val m=d.timer.workMinutes;d.copy(timer=FocusTimer(workMinutes=m,breakMode=rest,remainingMillis=(if(rest)5 else m)*60_000L))}
    fun finishDue(now:Long)=update { advance(it,now) }
    fun addHabit(title:String)=update { d->val clean=title.trim();if(clean.length !in 1..48 || d.habits.any{it.title.equals(clean,true)})d else d.copy(habits=d.habits+Habit(UUID.randomUUID().toString(),clean)) }
    fun toggleHabit(id:String,date:String)=update{d->d.copy(habits=d.habits.map{h->if(h.id!=id)h else h.copy(dates=if(date in h.dates)h.dates-date else h.dates+date)})}
    fun deleteHabit(id:String)=update{d->d.copy(habits=d.habits.filterNot{it.id==id})}
    companion object {
        @Volatile private var instance:CompanionStore?=null
        fun get(context:Context):CompanionStore=instance ?: synchronized(this){instance ?: CompanionStore(context).also{instance=it}}
    }
}
