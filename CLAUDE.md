# StudyPlanner AI Pet

App Android para postulantes a la UNAMBA (Abancay, Perú): agenda de estudio, mascota 3D con voz, práctica por temas
y «Camino UNAMBA» (ruta personalizada por grupo A, B o C). Proyecto de feria FERCYT, categoría E (artículo + diseño).
**Presentación con la APK: 13 de noviembre de 2026.** Versión actual: 0.26.0 (versionCode 43).

Historial, qué está probado y qué falta: `docs/ESTADO.md`. Detalle del módulo web del camino: `docs/CAMINO.md`.

## PC desktop-8sqpasg (usuario Usuario)

| Qué | Dónde |
|---|---|
| El proyecto (clon de GitHub) | `D:\Documentos\studyplanner-ai-pet` (desde el 15 nov; trabajar siempre aquí; `D:\Documentos\ChatGPT\FERCYT 2\android-app` es la copia vieja 0.23.0) |
| JDK 17 | `C:\Program Files\Java\jdk-17` |
| Android SDK (adb, emulador) | `D:\StudyPlanner-Android-Toolchain\sdk` (`local.properties`: `sdk.dir=D\:/StudyPlanner-Android-Toolchain/sdk`) |
| Emulador | AVD `StudyPlanner35` en `D:\StudyPlanner-Android-Toolchain\avd`; arranca mejor sin ventana: `-no-window -gpu auto` |
| Caché de Gradle | `D:\StudyPlanner-Android-Toolchain\gradle-home` (compila con `--offline`) |
| Servidor de IA desplegado | `D:\Documentos\ChatGPT\FERCYT 2\backend-ia` (con `wrangler`) |
| Node | `D:\Universidad Nacional Micaela Batidas\node.exe` (no está en el PATH). Python: Anaconda (`-I` al leer archivos ajenos). |
| Exámenes UNAMBA (fuera del repositorio) | Originales en `C:\Users\Usuario\Downloads\examenes`. Trabajo (páginas, OCR, banco `.jsonl`, figuras) en `D:\StudyPlanner-Android-Toolchain\examenes_trabajo`. `herramientas/examenes/arma_examenes.js` arma `examenes.js` y `exfig/` en el camino (ambos en `.gitignore`). |

Aquí sí hay `gradlew` (Gradle 9.6.0). El disco C: tiene poco espacio: no descargar nada grande sin preguntar.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "D:\StudyPlanner-Android-Toolchain\sdk"; $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_AVD_HOME = "D:\StudyPlanner-Android-Toolchain\avd"
$env:GRADLE_USER_HOME = "D:\StudyPlanner-Android-Toolchain\gradle-home"
.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## Dónde está cada cosa (PC desktop-1n2njj8, usuario USER)

| Carpeta | Qué es |
|---|---|
| `C:\Users\USER\studyplanner-build\android-app` | **El proyecto** (esta carpeta). |
| github.com/ryosakkkkk/studyplanner-ai-pet | Repositorio **público** con todo: este proyecto, `backend-ia/`, `mascotas-3d/` y `herramientas/`. |
| `C:\Users\USER\studyplanner-build\herramientas` | `jdk` (JDK 17), `gradle-9.6.0`, `sdk` (Android 36, platform-tools). |
| `C:\Users\USER\studyplanner-build\gradle-home` | Caché de Gradle (`GRADLE_USER_HOME`). |
| `C:\Users\USER\Downloads\examenes\studyplanner_0230` | `CONSTRUIR_APK.bat`, registros de cada compilación, APK y `fuentes\`. |
| `C:\Users\USER\Downloads` | Copia de cada APK: `StudyPlanner-AI-Pet-<versión>-debug.apk`. |

No hay `gradlew` en el proyecto: se usa el Gradle de `herramientas`. No hay emulador en esta PC.
En la PC anterior (usuario `Usuario`) quedó `Documents\ChatGPT\FERCYT 2` con `android-app`, el emulador y
`backend-ia` desplegado. En el repositorio, `backend-ia/` es la copia del 8 nov (Gemini → Groq → Workers AI, con
sus .bat de despliegue); puede diferir un poco de lo que está publicado.

Otras carpetas del repositorio: `herramientas/` (`CONSTRUIR_APK.bat`, `PROBAR_TUTOR_IA.bat`,
`PROBAR_SERVIDOR_IA.bat` y `camino/` con utilidades de la página) y `mascotas-3d/` (fuentes de Blender 5.2 de las
seis mascotas, scripts del esqueleto y animaciones, pruebas y resultados; los GLB finales ya están en `app/`).

## Compilar y probar

PowerShell, desde esta carpeta:

```powershell
$b = "$env:USERPROFILE\studyplanner-build"
$env:JAVA_HOME = "$b\herramientas\jdk"; $env:ANDROID_HOME = "$b\herramientas\sdk"; $env:GRADLE_USER_HOME = "$b\gradle-home"
$g = "$b\herramientas\gradle-9.6.0\bin\gradle.bat"
& $g --no-daemon --console=plain :app:assembleDebug                      # APK (unos 40 s)
& $g --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug   # 554 pruebas + lint (1,5 min)
& $g --no-daemon --console=plain :app:testDebugUnitTest --tests "pe.studyplanner.aipet.data.tutor.CaminoStoreTest"
```

- APK: `app\build\outputs\apk\debug\app-debug.apk`. Informes: `app\build\reports\` y `app\build\test-results\`.
- El lint corta la compilación si hay errores (`abortOnError = true`). Hoy (0.26.0): 0 errores, 48 avisos de estilo.
- `local.properties` debe decir `sdk.dir=C\:/Users/USER/studyplanner-build/herramientas/sdk` (con `C\:`).
- Otra forma: doble clic en `CONSTRUIR_APK.bat`. Compila, copia la APK a Descargas y deja `construir_log.txt`,
  `errores_compilacion.txt`, `resumen_pruebas.txt` y `lint_<versión>.txt`. Si hay un `parche.zip` a su lado, antes
  lo extrae encima del proyecto (rutas desde la raíz, por ejemplo `app/src/...`).
- Teléfono: `herramientas\sdk\platform-tools\adb.exe install -r <apk>`. Cada PC firma con su propia clave de
  pruebas: si el teléfono tiene una APK de la otra PC, hay que desinstalarla antes.
- Pruebas de la página del camino (Node): ver `pruebas-web\LEEME.md`.

## Tecnología

Kotlin 2.2.10, Compose Material 3, Navigation Compose, Room (KSP 2.3.12), DataStore, AGP 9.4.0, Gradle 9.6.0, JDK 17,
compileSdk 36, minSdk 26. Paquete `pe.studyplanner.aipet`. Versiones en `gradle/libs.versions.toml`.
Las pantallas con matemática o 3D son páginas locales en un WebView, servidas por `PetAssetServer` desde
`assets/web` en `https://appassets.androidplatform.net/web/` (sin Internet).

## Código (`app/src/main/java/pe/studyplanner/aipet`)

- `domain/` — Kotlin puro, sin Android, todo con pruebas. `domain/tutor` es el motor de práctica: `TutorSession`
  (máquina de estados: aprender → práctica con respuesta inmediata → comprobación sin ayuda → resumen), generadores de
  ejercicios, banco de preguntas, `MasteryPolicy`. `domain/knowledge`: `StudyKnowledge` (lo que la mascota sabe sin
  Internet, búsqueda BM25) y `QuickMath` (cuentas rápidas). También agenda, planificador, agente, estados de la mascota
  y `json/MiniJson` (JSON sin `org.json`).
- `data/` — Room (`academic`), cuenta en la nube (`account`), IA (`ai/GeminiProxyProvider.kt`: orden de respuesta de
  «Hablar», `AiTransport`, `GeminiProxyClient`, `OfflineAnswers`), tutor (`tutor/CaminoStore.kt`: estado del camino).
- `platform/` — servicios Android: alarmas, mascota flotante (`overlay`), visor 3D (`pet3d`), voz, BLE (`companion`).
- `ui/` — Compose. `StudyPlannerApp.kt` tiene el `NavHost` (rutas `camino`, `tutor/{topicId}`, `calendar`…).
  `ui/tutor`: `TutorScreen` + `TutorCoordinator` (práctica de los 52 temas de la app), `CaminoScreen` (WebView del
  camino, su puente y la voz de la mascota), `TutorTopicsScreen`. Tres pestañas: Hoy, Estudiar, Agenda.
- `app/src/main/assets/conocimiento/base.json` — lo que sabe la mascota sin Internet (776 fichas). Se genera con
  `node herramientas/conocimiento/genera_base.js` desde `herramientas/conocimiento/glosario.json` (glosario propio),
  la idea clave y las preguntas fijas del Camino y los bancos de `assets/tutor`. Volver a generarlo si cambian.
- `app/src/main/assets/web/camino` — Camino UNAMBA (HTML + JS + KaTeX). Ver `docs/CAMINO.md`.
- `app/src/main/assets/web/pets` + `pet-viewer.html` + `three/` — seis mascotas GLB (búho, conejo, gato, panda,
  pingüino, zorro) con 17 animaciones, cara, accesorios y máscara de zonas; visor con three.js.
- `app/src/test` — 57 archivos, 554 pruebas de JVM (0.25.0). `app/src/androidTest` — 22 archivos, **desactualizados**
  (textos de la versión 0.15); no se ejecutan en la compilación.

Algunos archivos antiguos (`StudyPlannerApp.kt`, `GeminiProxyProvider.kt`) están escritos muy compactos: al tocarlos,
respeta el estilo del archivo. Comentarios y KDoc en español.

## IA: servidor propio (API)

La app no llama a ningún proveedor de IA directamente. Habla con un Cloudflare Worker del usuario
(`studyplanner-ia`): `POST /chat` con `{message, zoneId, now}` y cabecera `Bearer` → `{kind, text}` con `kind` =
`answer`, `command` o `clarify`. También `/health`, `/check`, `/cuenta/*` y `/copia`. El mensaje admite hasta 4000
caracteres. Límites: 12 peticiones por minuto y 500 por día. Proveedores en cadena: Gemini → Groq (opcional) →
Workers AI.

- La dirección y el código de acceso están en `ia_local.properties` (claves `endpoint` y `token`) y pasan a
  `BuildConfig.AI_ENDPOINT` y `AI_TOKEN` al compilar.
- **Sin Internet primero (desde la 0.25.0):** en «Hablar» responden, en este orden, las órdenes de agenda, los saludos
  y cuentas rápidas (`QuickMath`), lo que la mascota sabe (`StudyKnowledge` con `base.json`) y, solo si nada de eso
  alcanza y hay conexión, el servidor (Gemini). Si el servidor falla, se responde con lo más cercano que haya.
  La IA ya no está en ningún otro lugar de la app (se quitaron «Otra explicación (IA)», «Preguntarle al tutor», la
  conversación del Camino y la pantalla «Conectar IA»).
- **Pendiente:** el servidor responde bien pero tarda de 15 a 24 s por mensaje (medido el 11 nov). En el código,
  `askGemini` espera 20 s antes de pasar a Workers AI y ya pide `thinkingBudget: 0`: lo más probable es que el modelo
  `gemini-flash-latest` agote esos 20 s o no acepte apagar el razonamiento. Mirar `wrangler tail` y probar un modelo
  Flash-Lite y una espera menor (8 s). Desplegar solo con permiso del usuario.
- `PROBAR_SERVIDOR_IA.bat` prueba el servidor real sin mostrar la conexión; gasta 3 peticiones del día.

## Reglas del proyecto (las pidió el usuario; no negociables)

1. **Claves:** ninguna clave de proveedor va en la app, en el código ni en el chat. Nunca muestres, copies ni
   subas el contenido de `ia_local.properties`. No escribas claves en formularios: las pone el usuario con los
   `PONER_CLAVE_*.bat` del servidor.
2. **Interfaz:** español sencillo del Perú, sin emojis, sin marcas de «hecho con IA», sin opciones que el usuario
   no pidió. Todo texto generado por IA en pantalla lleva su aviso («Responde una IA y puede equivocarse…»).
3. **Preguntas de admisión:** las de las lecciones del camino son propias (mismo tipo y dificultad, datos distintos,
   respuesta calculada o verificada y explicación). Desde el 14 nov el usuario informó que la UNAMBA lo autorizó a usar
   sus exámenes en la app: la sección «Exámenes UNAMBA» trae las preguntas reales transcritas y resueltas por el
   equipo (los cuadernillos no traen clave). Van **dentro de la APK**, pero **nunca en el repositorio**: `examenes.js`
   y `exfig/` están en `.gitignore`, y tampoco se suben los PDF, las páginas, el OCR ni el banco `.jsonl`.
4. **Método de práctica (desde el 13 nov reemplaza al socrático, por pedido del usuario):** práctica de recuperación con
   respuesta inmediata. El estudiante responde primero; al comprobar ve al instante si acertó y la explicación paso a
   paso (al fallar, como ejemplo resuelto); lo que falla vuelve al final de la lección con datos nuevos o «uno
   parecido»; cada tema regresa en el repaso espaciado (1, 3, 7, 14 y 30 días) mezclado con otros. «Ver la solución»
   siempre disponible; las pistas solo si las pide. Ver «Método» en `docs/CAMINO.md`.
5. **Honestidad al informar:** no digas que algo se probó en teléfono o emulador si no se hizo. Di qué se verificó,
   cómo y qué quedó sin probar.
6. **3D (Blender):** no arreglar con transparencia, doble cara, suavizado ni más polígonos; conservar nombres,
   jerarquía de huesos, las 17 animaciones, escala y duraciones.
7. **Descargas e instalaciones** en la PC: pedir permiso antes, diciendo archivo, origen y tamaño.
8. No crear cuentas de prueba en el servidor de producción.
9. **El repositorio es público.** Nunca subir `ia_local.properties`, `local.properties`, APK (llevan la conexión
   dentro), claves ni los exámenes o su texto (`examenes.js`, `exfig/`). `.gitignore` ya los excluye; revisa
   `git status` antes de cada commit.

## Forma de trabajar

1. Leer el código que se va a tocar y sus pruebas. Cambios pequeños y con prueba (JVM para Kotlin; Playwright para
   la página del camino).
2. Subir `versionCode` y `versionName` en `app/build.gradle.kts` en cada APK que se entrega.
3. Compilar, correr pruebas y lint, y leer el resultado completo antes de decir que funciona.
4. Anotar en `docs/ESTADO.md`: qué cambió, qué se verificó y qué queda pendiente.
5. Commit en español, pequeño y con lo que se verificó; subir a GitHub cuando compile y pasen las pruebas.
6. Si se cambia la página del camino, correr `pruebas-web` (claro y oscuro) y revisar capturas a tamaño de teléfono.

Hasta el 11 nov el trabajo se hizo desde una sesión de Claude en la nube: se editaba una copia, se probaba la página
con Playwright y el Kotlin con kotlinc, se mandaba un `parche.zip` a esta PC y se lanzaba `CONSTRUIR_APK.bat`.
Con Claude Code ya no hace falta el parche: se edita y se compila directamente en el proyecto.

## Pendiente (lo más urgente primero)

- Probar la 0.26.0 en un teléfono real: «Exámenes UNAMBA» (figuras, voz en preguntas reales), voz en el Camino
  (leer y responder «la B»), «Hablar» sin Internet y con Internet, repaso de hoy.
- Revisar a mano las 195 preguntas de examen no verificadas (`descartes.txt` del banco); faltan exámenes de los
  grupos B y C.
- Averiguar y corregir la demora del servidor de IA (ver arriba).
- Actualizar el artículo de la feria a la versión 0.26.0 (método, voz, conocimiento sin Internet, exámenes reales).
- Después de la feria: simulacro con tiempo (15–20 preguntas mezcladas y luego 60 con el tiempo oficial), copia en la
  nube del avance del camino, más preguntas de Biología y Química teórica, correr las pruebas de pantallas de
  `pruebas-web` (Playwright), poner al día `androidTest`.
