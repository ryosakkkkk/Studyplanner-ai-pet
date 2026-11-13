package pe.studyplanner.aipet.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.studyplanner.aipet.domain.academic.DatasetScope
import pe.studyplanner.aipet.domain.agent.*

data class AgentChatState(val scope:DatasetScope=DatasetScope.PERSONAL,
    val messages:List<AgentMessage> = emptyList(),val proposal:AgentProposal?=null,
    val receipt:AgentReceipt?=null,val busy:Boolean=true,val historyEnabled:Boolean=false,val ready:Boolean=false,val error:String="",
    /** Solo mientras se prepara una respuesta: confirmar un cambio nunca se puede cortar a medias. */
    val cancellable:Boolean=false)

class AgentChatViewModel(private val gateway:AgentGateway):ViewModel() {
    private val mutable=MutableStateFlow(AgentChatState())
    val state=mutable.asStateFlow()
    init { load(DatasetScope.PERSONAL) }
    private fun load(scope:DatasetScope) {
        mutable.value=AgentChatState(scope=scope)
        viewModelScope.launch {
            try {
                val enabled=gateway.historyEnabled(scope)
                mutable.value=mutable.value.copy(messages=if(enabled)gateway.history(scope) else emptyList(),historyEnabled=enabled,busy=false,ready=true)
            } catch(e:CancellationException){throw e}
            catch(_:Exception){mutable.value=mutable.value.copy(busy=false,error="No se pudo leer el historial. La agenda se conserva. Reintenta al volver a abrir el chat.")}
        }
    }
    fun retryLoad() {if(!mutable.value.busy)load(mutable.value.scope)}
    override fun onCleared() { mutable.value.proposal?.let {gateway.discard(it.id)}; super.onCleared() }
    fun changeScope(scope:DatasetScope) {
        val s=mutable.value
        if(s.busy || s.proposal!=null || s.scope==scope)return
        load(scope)
    }
    private suspend fun append(scope:DatasetScope,role:String,text:String) {
        val m=AgentMessage(role=role,text=text.take(8000))
        mutable.value=mutable.value.copy(messages=(mutable.value.messages+m).takeLast(100))
        gateway.recordMessage(scope,m)
    }
    fun send(text:String) {
        val s=mutable.value
        if(s.busy || !s.ready || s.proposal!=null || text.isBlank())return
        mutable.value=s.copy(busy=true,error="",receipt=null,cancellable=true)
        val turnId=++turnSeq
        sending=viewModelScope.launch {
            try {
                append(s.scope,"user",text)
                when(val turn=gateway.prepare(s.scope,text)) {
                    is AgentTurn.Reply->append(s.scope,"assistant",turn.text)
                    is AgentTurn.Clarify->append(s.scope,"assistant",turn.text)
                    is AgentTurn.Review->if(turnId==turnSeq)mutable.value=mutable.value.copy(proposal=turn.proposal) else gateway.discard(turn.proposal.id)
                }
            } catch(e:CancellationException){throw e}
            catch(e:Exception){mutable.value=mutable.value.copy(error=e.message ?: "No se pudo revisar la solicitud. No se confirmó ningún cambio.")}
            finally {if(turnId==turnSeq)mutable.value=mutable.value.copy(busy=false,cancellable=false)}
        }
    }
    private var sending:Job?=null
    private var turnSeq=0L
    /** Corta la preparación de una respuesta. No hay nada que deshacer: todavía no se propuso ni guardó ningún cambio. */
    fun cancel() {
        val s=mutable.value
        if(!s.busy || !s.cancellable)return
        turnSeq++; sending?.cancel(); sending=null
        val note=AgentMessage(role="assistant",text="Cancelado. No se guardó ningún cambio.")
        mutable.value=s.copy(busy=false,cancellable=false,error="",messages=(s.messages+note).takeLast(100))
    }
    fun clearError() { if(!mutable.value.busy) mutable.value=mutable.value.copy(error="") }
    /** Vuelve a enviar la última pregunta, por ejemplo tras un error de conexión. */
    fun retryLast() {
        val last=mutable.value.messages.lastOrNull{it.role=="user"}?.text ?: return
        send(last)
    }
    fun discard() {
        val s=mutable.value;if(s.busy)return
        s.proposal?.let {gateway.discard(it.id)}
        mutable.value=s.copy(proposal=null,error="",receipt=null,messages=(s.messages+AgentMessage(role="assistant",text="Propuesta descartada. No se aplicaron sus cambios.")).takeLast(100))
    }
    fun confirm() {
        val s=mutable.value;val p=s.proposal ?: return
        if(s.busy)return
        mutable.value=s.copy(busy=true,error="")
        viewModelScope.launch {
            try {
                val r=gateway.confirm(p.id)
                mutable.value=mutable.value.copy(proposal=null,receipt=r)
                try {append(s.scope,"assistant",r.message)}
                catch(e:CancellationException){throw e}
                catch(_:Exception){mutable.value=mutable.value.copy(error="La operación está registrada, pero no se pudo guardar su mensaje en el historial. Consulta el comprobante.")}
            } catch(e:CancellationException){throw e}
            catch(e:Exception){mutable.value=mutable.value.copy(error=e.message ?: "No se pudo confirmar. Revisa el estado antes de intentar de nuevo.")}
            finally {mutable.value=mutable.value.copy(busy=false)}
        }
    }
    fun historyEnabled(enabled:Boolean) {
        val s=mutable.value;if(s.busy || !s.ready)return
        mutable.value=s.copy(busy=true,error="")
        viewModelScope.launch {
            try {gateway.setHistoryEnabled(s.scope,enabled);mutable.value=mutable.value.copy(historyEnabled=enabled)}
            catch(e:CancellationException){throw e}
            catch(_:Exception){mutable.value=mutable.value.copy(error="No se pudo cambiar la conservación del historial.")}
            finally {mutable.value=mutable.value.copy(busy=false)}
        }
    }
    fun clearHistory() {
        val s=mutable.value;if(s.busy || s.proposal!=null)return
        mutable.value=s.copy(busy=true,error="")
        viewModelScope.launch {
            try {gateway.clearHistory(s.scope);mutable.value=mutable.value.copy(messages=emptyList(),receipt=null)}
            catch(e:CancellationException){throw e}
            catch(_:Exception){mutable.value=mutable.value.copy(error="No se pudo borrar el historial. Reintenta.")}
            finally {mutable.value=mutable.value.copy(busy=false)}
        }
    }
}
