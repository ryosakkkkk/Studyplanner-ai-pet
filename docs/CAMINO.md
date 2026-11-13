# Camino UNAMBA (módulo web)

Carpeta: `app/src/main/assets/web/camino`. Es una página local (HTML + JavaScript sin dependencias de red) que la
app muestra en `ui/tutor/CaminoScreen.kt`. Se hizo como página porque las explicaciones llevan notación matemática
(KaTeX) y figuras (SVG).

## Qué hace

1. El postulante elige su grupo: A Ingenierías, B Biomédicas, C Sociales.
2. Diagnóstico de 12 preguntas: una por curso del examen y dos en los cursos que pesan 9 o más.
3. Camino ordenado por lo que más pesa en el examen del grupo y lo que más le falta.
4. Práctica de 5 preguntas por tema con método socrático; el tema queda dominado con 4 aciertos **a la primera**
   en las últimas 5 respuestas.
5. Con Internet y la IA activa: conversación con el tutor.

Distribución de cursos (60 preguntas), tomada de los exámenes CPU y ordinario de 2025 y 2026:

| Grupo | Cursos (preguntas) |
|---|---|
| A | RM 10, RV 10, Comunicación 5, Aritmética 5, Álgebra 5, Geometría 5, Trigonometría 5, Física 5, Química 5, Ecología 5 |
| B | RM 10, RV 10, Comunicación 5, Aritmética 5, Álgebra 5, Física 5, Química 5, Biología 5, Zoología 5, Ecología 5 |
| C | RM 10, RV 10, Comunicación 5, Aritmética 5, Álgebra 5, Ecología 5, Economía 5, Historia 5, Cívica 5, Geografía 5 |

Hay 118 temas: 52 que ya tenía la app (se abren en su pantalla nativa), 36 con preguntas generadas y 30 con 230
preguntas fijas.

## Archivos (en el orden en que los carga `index.html`)

| Archivo | Contenido |
|---|---|
| `katex/` | KaTeX y sus fuentes (no tocar). |
| `fig.js` | Figuras SVG: `Fig.plano`, `recta`, `venn2`, `tri`, `vectores`, `circulo`, `paralelas`, `segmento`, `tabla`. |
| `gen.js` … `gen4.js` | 36 generadores. `Gen.G[id](R, dificultad)` devuelve `{ e, o[5], c, pasos[], ref }`. `Gen.crear(id, semilla, dif)` para pruebas. |
| `nativo.js` | Títulos de los 52 temas de la app. Lo genera `tools/nativo.py`; no editar a mano. |
| `datos.js` | Cursos, grupos, lista de temas, idea clave y preguntas fijas (primera tanda). |
| `datos2.js` | Distribución 2025–2026, curso Zoología y los 18 temas agregados con «examenes 2». |
| `plan.js` | Lógica sin interfaz: `Plan.diagnostico`, `dominio`, `camino`, `avance`, `anotar`, `pregunta`. |
| `guia.js` | Preguntas de guía del método socrático: `Guia.de(id, pregunta)`. |
| `app.js` | Pantallas, navegación, puente con la app y conversación con el tutor. |
| `camino.css` | Estilos, tema claro y oscuro (`?tema=oscuro`). |

## Formatos

Tema en la lista: `[id, curso, peso, título]`. Sin título es un tema de la app (se abre con `abrirTema`).

Pregunta lista para mostrar: `e` enunciado (HTML; fórmulas entre `$…$`), `o` cinco alternativas, `c` índice de la
correcta, `pasos` (un texto si es explicación única, varios si es paso a paso; el último da la respuesta), `ref`
(qué tipo de examen sirvió de modelo).

Pregunta fija en `datos2.js`: `[enunciado, correcta, mal1, mal2, mal3, mal4, explicación]`. La correcta va primero;
la página baraja las alternativas al mostrar.

### Agregar un tema de preguntas fijas

1. En `datos2.js`: `tema(id, curso, peso, título, R('Curso'), [idea clave…], [preguntas…])`. Mínimo 6 preguntas,
   5 alternativas distintas, explicación de 20 caracteres o más.
2. En `guia.js`, objeto `F`: dos preguntas de guía que sirvan para todas las preguntas del tema.
3. Correr `plan_test`, `guia_test` y `nuevos_test` (agregar el id a su lista).

### Agregar un generador

1. En `gen4.js` (o un `gen5.js` nuevo, cargado en `index.html`): `G['id'] = (R, dif) => armar(...)`. La respuesta se
   calcula; los distractores salen de errores típicos. Ver un generador vecino como modelo.
2. En `datos2.js`: añadirlo a la lista con su título e idea clave.
3. En `guia.js`, objeto `R`: una pregunta de guía por cada paso menos el último, con una regla por tipo de
   enunciado (`[expresión regular | null, [preguntas…]]`).
4. Correr `gen_test` (120 semillas por dificultad) y `guia_test` (ninguna pregunta puede caer en la guía genérica).

## Método socrático (en `app.js`, `V.practica`)

- El primer intento es el que se anota para el dominio (`Plan.anotar`).
- Si falla o marca «No lo sé»: no se muestra la respuesta; se descarta la alternativa fallada y aparece «Tu tutor
  pregunta». En temas con pasos, cada paso se revela con «Ya lo pensé: ver este paso». En temas de explicación
  única salen hasta dos preguntas de guía, una por intento.
- Al tercer error o al agotar la guía se muestra la respuesta. «Ver la solución» está siempre.
- El resumen separa «N de 5 a la primera» de las que salieron con la guía.

## Puente con Kotlin

`window.AndroidCamino` (clase `CaminoBridge`):

| Método | Uso |
|---|---|
| `cargar()` / `guardar(estado, resumen)` | Estado del camino en `SharedPreferences` (`CaminoStore`). `resumen` es `{g,n,h,t,s,c,d}` para la tarjeta de Estudiar. |
| `dominio()` | JSON con el dominio de los temas de la app, por id. |
| `cabecera(título, fracción)` | Título y barra de avance de la pantalla (-1 la oculta). |
| `evento(tipo)` | `correcta`, `incorrecta`, `duda`, `guiada`, `logro`: mueven la mascota y cuentan para la racha. |
| `abrirTema(id)` | Abre un tema de la app en su pantalla nativa. |
| `iaDisponible()` | Si la IA está activa. Sin ella no aparece la conversación. |
| `tutorIA(id, petición)` | Envía la petición al servidor. La respuesta vuelve con `caminoTutor(id, ok, texto)`. |
| `pendiente()` | Pregunta que llega desde la práctica nativa (JSON de `TutorBrief.toJson()`), una sola vez. |
| `cerrar()` | «Volver a mi práctica». |

Kotlin llama a la página con `caminoAtras()` (botón atrás; devuelve `false` cuando hay que salir), `caminoVolver()`
(al regresar de un tema nativo) y `caminoTutor(...)`.

En un navegador, sin `AndroidCamino`, la página guarda en `localStorage`: así se prueba.

## Tutor con IA

`app.js` arma una sola línea de hasta 3400 caracteres: quién es el tutor, curso y tema, pregunta, alternativas,
respuesta correcta, solución de referencia, los últimos 6 turnos, lo que dice el estudiante y las reglas. Hay dos
juegos de reglas: `R_ABIERTA` (todavía no resuelve: no decir la respuesta ni la letra, una sola pregunta por turno,
máximo 70 palabras) y `R_VISTA` (ya vio la solución: explicar su duda y terminar con una pregunta).
Kotlin limpia la petición (`CaminoAiPrompt.relay`), la envía y traduce los fallos (`failureText`).
A los 9 s sin respuesta la página muestra «Sigo pensando…» y a los 60 s da por perdida la petición.

## Pruebas

En `pruebas-web/` (ver su `LEEME.md`). `gen_test`, `plan_test` y `guia_test` solo necesitan Node. Las demás usan
Playwright contra la página servida en el puerto 8771 y dejan capturas en `pruebas-web/cap`.
