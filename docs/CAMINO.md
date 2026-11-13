# Camino UNAMBA (módulo web)

Carpeta: `app/src/main/assets/web/camino`. Es una página local (HTML + JavaScript sin dependencias de red) que la
app muestra en `ui/tutor/CaminoScreen.kt`. Se hizo como página porque las explicaciones llevan notación matemática
(KaTeX) y figuras (SVG).

## Qué hace

1. El postulante elige su grupo: A Ingenierías, B Biomédicas, C Sociales.
2. Diagnóstico de 12 preguntas: una por curso del examen y dos en los cursos que pesan 9 o más.
3. Camino ordenado por lo que más pesa en el examen del grupo y lo que más le falta.
4. Práctica de 5 preguntas por tema con respuesta inmediata (ver «Método»); el tema queda dominado con 4 aciertos
   **a la primera** en las últimas 5 respuestas.
5. «Repaso de hoy»: cada tema practicado vuelve cuando toca (cajas de Leitner) en una sesión mezclada.
6. La mascota lee la pregunta y la explicación y se puede responder diciendo «la B». Todo funciona sin Internet.

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
| `plan.js` | Lógica sin interfaz: `Plan.diagnostico`, `dominio`, `camino`, `avance`, `anotar`, `pregunta` y el repaso espaciado (`programar`, `vencidos`, `repaso`, `diasParaRepaso`). |
| `app.js` | Pantallas, navegación, práctica con respuesta inmediata, repaso, voz y puente con la app. |
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
2. Correr `plan_test` y `nuevos_test` (agregar el id a su lista) y volver a generar la base de «Hablar»
   (`node herramientas/conocimiento/genera_base.js`).

### Agregar un generador

1. En `gen4.js` (o un `gen5.js` nuevo, cargado en `index.html`): `G['id'] = (R, dif) => armar(...)`. La respuesta se
   calcula; los distractores salen de errores típicos. Ver un generador vecino como modelo.
2. En `datos2.js`: añadirlo a la lista con su título e idea clave.
3. Correr `gen_test` (120 semillas por dificultad).

## Método: práctica de recuperación con respuesta inmediata (en `app.js`, `V.practica`)

Reemplaza al método socrático desde la versión 0.25.0 (pedido del usuario el 13 nov). Se apoya en la evidencia sobre
práctica de recuperación, retroalimentación explicativa, ejemplos resueltos, repaso espaciado e intercalado
(Dunlosky 2013; Butler y Roediger 2008; van Gog 2011; Rohrer 2020; Cepeda 2008).

- El estudiante responde primero («Comprobar»). El primer intento es el que se anota para el dominio (`Plan.anotar`).
- Acierta: «¡Bien!» en verde y «Ver por qué» (explicación plegada). A partir de 3 seguidas se muestra la racha.
- Falla: «Era la C» en rojo, la alternativa correcta marcada y la solución paso a paso a la vista (ejemplo resuelto).
  La pregunta vuelve al final de la lección: con datos nuevos si el tema tiene generador; la misma, con las
  alternativas en otro orden, si es de preguntas fijas. «No lo sé» muestra «Así se resuelve» y también vuelve.
- Lo que vuelve al final no cuenta para el dominio (ya contó el primer intento); el resumen dice «Corregiste N».
- Al terminar, `Plan.programar` mueve el tema de caja: 4 o 5 de 5 sube (1, 3, 7, 14 y 30 días), 3 de 5 se queda y
  2 o menos vuelve a mañana. «Repaso de hoy» junta los temas vencidos y pregunta uno de cada tema, en rueda.
- La mascota dice el veredicto corto (si la voz no está silenciada en Perfil); «Escuchar» lee la pregunta,
  «Explicar con voz» lee la solución y «Responder con voz» marca la alternativa dictada (no responde sola).

## Puente con Kotlin

`window.AndroidCamino` (clase `CaminoBridge`):

| Método | Uso |
|---|---|
| `cargar()` / `guardar(estado, resumen)` | Estado del camino en `SharedPreferences` (`CaminoStore`). `resumen` es `{g,n,h,t,s,c,d}` para la tarjeta de Estudiar. |
| `dominio()` | JSON con el dominio de los temas de la app, por id. |
| `cabecera(título, fracción)` | Título y barra de avance de la pantalla (-1 la oculta). |
| `evento(tipo)` | `correcta`, `incorrecta`, `duda`, `corregida`, `racha`, `logro`: mueven la mascota; las tres primeras cuentan para la racha y la meta del día. |
| `abrirTema(id)` | Abre un tema de la app en su pantalla nativa. |
| `vozDisponible()`, `escuchaDisponible()`, `silencio()` | Si hay motor de lectura, si hay reconocimiento de voz y si el estudiante silenció la voz en Perfil. |
| `hablar(texto)`, `callar()` | Lee en voz alta (texto simple; la página convierte el LaTeX con `paraVoz`) o corta la lectura. |
| `escuchar()` | Dicta una alternativa; vuelve con `caminoOido(índice, texto)` (índice −1 si no se entendió). |

Kotlin llama a la página con `caminoAtras()` (botón atrás; devuelve `false` cuando hay que salir), `caminoVolver()`
(al regresar de un tema nativo) y `caminoOido(...)`.

En un navegador, sin `AndroidCamino`, la página guarda en `localStorage` y no muestra los botones de voz: así se
prueba.

La conversación con un tutor con IA dentro del camino se quitó en la 0.25.0: la IA en línea queda solo en «Hablar»,
como respaldo de lo que la mascota sabe sin Internet.

## Pruebas

En `pruebas-web/` (ver su `LEEME.md`). `gen_test`, `plan_test` y `repaso_test` solo necesitan Node. Las demás usan
Playwright contra la página servida en el puerto 8771 y dejan capturas en `pruebas-web/cap`.
