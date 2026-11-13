# StudyPlanner AI Pet

App Android para postulantes a la Universidad Nacional Micaela Bastidas de Apurímac (UNAMBA): agenda de estudio,
mascota 3D que acompaña, práctica por temas con método socrático y **Camino UNAMBA**, una ruta personalizada según
el grupo de carreras (A Ingenierías, B Biomédicas, C Sociales). Proyecto para la feria FERCYT 2026.

Versión: **0.24.2** (11 de noviembre de 2026).

## Qué hay en el repositorio

| Carpeta | Contenido |
|---|---|
| `app/` | La app (Kotlin, Jetpack Compose, Room). La página del camino está en `app/src/main/assets/web/camino`. |
| `docs/` | `ESTADO.md` (historial, qué se probó y qué falta) y `CAMINO.md` (cómo está hecho el camino). |
| `pruebas-web/` | Pruebas de la página del camino (Node y Playwright). |
| `backend-ia/` | Servidor de IA propio (Cloudflare Worker). La app nunca guarda claves de proveedores. |
| `mascotas-3d/` | Fuentes de Blender de las seis mascotas, scripts, pruebas y resultados. |
| `herramientas/` | Compilación en Windows con un doble clic y pruebas del servidor de IA. |
| `CLAUDE.md` | Guía para trabajar el proyecto con Claude Code. |

## Compilar

Requisitos: JDK 17, Android SDK 36 y Gradle 9.6. En Windows, `herramientas/CONSTRUIR_APK.bat` instala lo necesario
en `%USERPROFILE%\studyplanner-build`, compila y deja la APK en Descargas. A mano:

```
gradle :app:assembleDebug
gradle :app:testDebugUnitTest :app:lintDebug
```

Para usar el tutor con IA hace falta `ia_local.properties` en la raíz (`endpoint=` y `token=` del servidor propio).
Ese archivo es privado y no se sube.

## Preguntas de admisión

Todas las preguntas son propias: los exámenes de la universidad se usaron solo para saber qué tipo de pregunta
sale y cuánto pesa cada curso. No se copia ningún enunciado.
