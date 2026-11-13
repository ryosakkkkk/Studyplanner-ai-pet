package pe.studyplanner.aipet.domain.focus

/** Small persistent timer. A completed focus block is recorded once by its id. */
data class FocusTimer(val id:String="", val workMinutes:Int=25, val breakMode:Boolean=false,
    val remainingMillis:Long=25*60_000L, val deadline:Long?=null) {
    fun remaining(now:Long):Long=(deadline?.minus(now) ?: remainingMillis).coerceAtLeast(0)
    fun start(now:Long)=if(deadline!=null)this else copy(deadline=now+remainingMillis.coerceAtLeast(1))
    fun pause(now:Long)=copy(remainingMillis=remaining(now),deadline=null)
}
