package pe.studyplanner.aipet.ui.agent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.studyplanner.aipet.data.ai.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConnectionScreen(store:AiConnectionStore,onBack:()->Unit) {
    val config by store.state.collectAsStateWithLifecycle()
    var endpoint by remember {mutableStateOf(config.endpoint)}
    // Do not put access tokens in saved instance state.
    var token by remember {mutableStateOf("")}
    var consent by remember {mutableStateOf(config.enabled)}
    var busy by remember {mutableStateOf(false)}
    var result by remember {mutableStateOf("")}
    var forget by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    Scaffold(topBar={TopAppBar(title={Text("Conectar IA")},navigationIcon={IconButton(onClick=onBack,enabled=!busy){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver")}})}) {padding->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("IA en la nube + voz de Android",style=MaterialTheme.typography.headlineSmall)
            Text(if(config.enabled)"Conexión habilitada · pendiente de disponibilidad del servidor" else "Modo local activo · la IA externa está apagada",color=MaterialTheme.colorScheme.primary)
            Card {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Pensado para gastar poco",style=MaterialTheme.typography.titleMedium)
                Text("Las órdenes reconocidas se resuelven en tu teléfono. La IA en la nube solo recibe las demás preguntas. La voz utiliza el motor instalado de Android, sin contratar otra API.")
                Text("El servidor incluido en el proyecto (carpeta backend-ia) usa Gemini de Google y, si no responde, la capa gratuita de Cloudflare Workers AI. Se activa una vez con ACTIVAR_IA.bat; ninguna clave de proveedor va en el teléfono.",style=MaterialTheme.typography.bodySmall)
            }}
            OutlinedTextField(endpoint,{endpoint=it;consent=false;result=""},label={Text("URL HTTPS de tu servidor")},placeholder={Text("https://tu-companero.workers.dev")},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(token,{if(it.length<=128)token=it.trim()},label={Text(if(config.hasToken)"Nuevo código de acceso (opcional)" else "Código personal de acceso")},supportingText={Text("Código del servidor (43 a 128 caracteres). Nunca pegues aquí la clave de un proveedor.")},visualTransformation=PasswordVisualTransformation(),enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth())
            Row {
                Checkbox(checked=consent,onCheckedChange={consent=it},enabled=!busy)
                Text("Permito enviar mi petición, fecha y zona horaria a este servidor, que usa Gemini de Google o Cloudflare Workers AI. No se envían automáticamente mi agenda, audio, perfil ni historial. Las respuestas de IA pueden tener errores.",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
            }
            Text("Cada petición externa es independiente. Para continuar una idea, incluye el contexto en tu mensaje. Los cambios en la agenda siempre se revisan antes de confirmarse.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={busy=true;result="";scope.launch {try{withContext(Dispatchers.IO){store.save(endpoint,token,consent)};if(store.state.value.error.isBlank()){token="";result=if(consent)"Conexión guardada. Comprueba el servidor antes de conversar." else "Configuración guardada; modo local activo."}}finally{busy=false}}},enabled=!busy && endpoint.isNotBlank() && (config.hasToken || token.length>=43),modifier=Modifier.fillMaxWidth()) {Text(if(consent)"Guardar y habilitar" else "Guardar sin activar")}
            OutlinedButton(onClick={busy=true;result="";scope.launch {try{result=GeminiProxyClient(store).check()}catch(e:CancellationException){throw e}catch(_:Exception){result="No se pudo verificar el servidor. Revisa HTTPS, despliegue y código de acceso. No se hizo una consulta a la IA."}finally{busy=false}}},enabled=!busy && config.hasToken && endpoint.trim().trimEnd('/')==config.endpoint,modifier=Modifier.fillMaxWidth()){Text("Comprobar servidor (sin consulta a la IA)")}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(result.isNotBlank())Text(result)
            if(config.error.isNotBlank())Text(config.error,color=MaterialTheme.colorScheme.error)
            if(config.enabled)OutlinedButton(onClick={store.disable();consent=false;result="Modo local activado."},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Usar solo comandos locales")}
            if(config.hasToken)TextButton(onClick={forget=true},enabled=!busy){Text("Olvidar conexión y código")}
        }
    }
    if(forget)AlertDialog(onDismissRequest={forget=false},title={Text("Olvidar conexión")},text={Text("Se elimina el código de acceso de este dispositivo. Tu agenda y perfil se conservan.")},confirmButton={TextButton(onClick={store.forget();endpoint="";token="";consent=false;result="";forget=false}){Text("Olvidar")}},dismissButton={TextButton(onClick={forget=false}){Text("Cancelar")}})
}
