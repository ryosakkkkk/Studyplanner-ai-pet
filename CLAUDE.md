# StudyPlanner AI Pet

App Android para postulantes a la UNAMBA (Abancay, Perú): agenda de estudio, mascota 3D con voz, práctica por temas
y «Camino UNAMBA» (ruta personalizada por grupo A, B o C). Proyecto de feria FERCYT, categoría E (artículo + diseño).
**Presentación con la APK: 13 de noviembre de 2026.** Versión actual: 0.24.2 (versionCode 41).

Historial, qué está probado y qué falta: `docs/ESTADO.md`. Detalle del módulo web del camino: `docs/CAMINO.md`.

## Dónde está cada cosa (esta PC, usuario USER)

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
& $g --no-daemon --console=plain :app:testDebugUnitTest :app:lintDebug   # 563 pruebas + lint (1,5 min)
& $g --no-daemon --console=plain :app:testDebugUnitTest --tests "pe.studyplanner.aipet.data.tutor.CaminoStoreTest"
```

- APK: `app\build\outputs\apk\debug\app-debug.apk`. Informes: `app\build\reports\` y `app\build\test-results\`.
- El lint corta la compilación si hay errores (`abortOnError = true`). Hoy: 0 errores, 58 avisos de estilo.
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
  (máquina de estados: aprender → práctica guiada con 3 pistas → comprobación sin ayuda → resumen), generadores de
  ejercicios, banco de preguntas, `MasteryPolicy`. También agenda, planificador, agente, estados de la mascota y
  `json/MiniJson` (JSON sin `org.json`).
- `data/` — Room (`academic`), cuenta en la nube (`account`), IA (`ai/GeminiProxyProvider.kt`: `AiTransport`,
  `GeminiProxyClient`), tutor (`tutor/CaminoStore.kt`: estado del camino, `CaminoAiPrompt`, `CaminoHandoff`).
- `platform/` — servicios Android: alarmas, mascota flotante (`overlay`), visor 3D (`pet3d`), voz, BLE (`companion`).
- `ui/` — Compose. `StudyPlannerApp.kt` tiene el `NavHost` (rutas `camino`, `tutor/{topicId}`, `calendar`…).
  `ui/tutor`: `TutorScreen` + `TutorCoordinator` (práctica de los 52 temas de la app), `CaminoScreen` (WebView del
  camino y su puente), `TutorTopicsScreen`. Tres pestañas: Hoy, Estudiar, Agenda.
- `app/src/main/assets/web/camino` — Camino UNAMBA (HTML + JS + KaTeX). Ver `docs/CAMINO.md`.
- `app/src/main/assets/web/pets` + `pet-viewer.html` + `three/` — seis mascotas GLB (búho, conejo, gato, panda,
  pingüino, zorro) con 17 animaciones, cara, accesorios y máscara de zonas; visor con three.js.
- `app/src/test` — 57 archivos, 563 pruebas de JVM. `app/src/androidTest` — 22 archivos, **desactualizados**
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
- **Pendiente importante:** el servidor responde bien pero tarda de 15 a 24 s por mensaje (medido el 11 nov;
  `/health` y `/check` tardan menos de medio segundo). La causa está entre el Worker y el proveedor; hay que mirar
  su registro (`wrangler tail`) en la PC que tiene `backend-ia`. La app espera hasta 45 s.
- Tutor del camino: la página arma la petición completa (pregunta, alternativas, respuesta correcta, solución,
  últimos turnos y reglas socráticas) y Kotlin solo la reenvía (`CaminoBridge.tutorIA` → `AiTransport.chat`).
- `PROBAR_TUTOR_IA.bat` y `PROBAR_SERVIDOR_IA.bat` (en `studyplanner_0230`) prueban el servidor real sin mostrar
  la conexión; gastan 5 y 3 peticiones del día.

## Reglas del proyecto (las pidió el usuario; no negociables)

1. **Claves:** ninguna clave de proveedor va en la app, en el código ni en el chat. Nunca muestres, copies ni
   subas el contenido de `ia_local.properties`. No escribas claves en formularios: las pone el usuario con los
   `PONER_CLAVE_*.bat` del servidor.
2. **Interfaz:** español sencillo del Perú, sin emojis, sin marcas de «hecho con IA», sin opciones que el usuario
   no pidió. Todo texto generado por IA en pantalla lleva su aviso («Responde una IA y puede equivocarse…»).
3. **Preguntas de admisión:** siempre propias. Los exámenes se leen solo para saber qué tipo de pregunta sale y
   cuánto pesa cada curso; no se copian enunciados (son de la universidad y los «TOMO» son de un tercero). Mismo
   tipo y dificultad, datos distintos, respuesta calculada o verificada y explicación.
4. **Método socrático en toda la práctica:** si el estudiante falla o no sabe, no se le da la respuesta de frente;
   primero preguntas o pistas, y «Ver la solución» siempre disponible.
5. **Honestidad al informar:** no digas que algo se probó en teléfono o emulador si no se hizo. Di qué se verificó,
   cómo y qué quedó sin probar.
6. **3D (Blender):** no arreglar con transparencia, doble cara, suavizado ni más polígonos; conservar nombres,
   jerarquía de huesos, las 17 animaciones, escala y duraciones.
7. **Descargas e instalaciones** en la PC: pedir permiso antes, diciendo archivo, origen y tamaño.
8. No crear cuentas de prueba en el servidor de producción.
9. **El repositorio es público.** Nunca subir `ia_local.properties`, `local.properties`, APK (llevan la conexión
   dentro), claves ni los exámenes o su texto. `.gitignore` ya los excluye; revisa `git status` antes de cada commit.

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

- Probar la 0.24.2 en un teléfono: camino, guía socrática, tutor con IA y «Preguntarle al tutor» desde un tema.
- Averiguar y corregir la demora del servidor de IA.
- Actualizar el artículo de la feria a la versión 0.24.2.
- Después de la feria: simulacro con tiempo, copia en la nube del avance del camino, más preguntas de Biología y
  Química teórica, poner al día `androidTest`.
