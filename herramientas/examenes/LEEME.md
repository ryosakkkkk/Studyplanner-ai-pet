# Exámenes UNAMBA en el Camino

El usuario informó que la UNAMBA lo autorizó a usar sus exámenes dentro de la app. Las preguntas van **dentro de la
APK**, pero **nunca en este repositorio público**: aquí solo está la herramienta, sin ninguna pregunta.

## Flujo

1. Los cuadernillos (PDF o Word) se pasan a imágenes por página y a texto OCR con el motor de Windows (sin descargas).
2. Cada examen se transcribe y se **resuelve** (los cuadernillos no traen clave) a un archivo `.jsonl`, una pregunta
   por línea, en la carpeta de trabajo (fuera del repositorio).
3. `node herramientas/examenes/arma_examenes.js [carpeta_del_banco]` valida, quita repetidas y escribe:
   - `app/src/main/assets/web/camino/examenes.js` (`window.EXAMENES = {lista, preg}`)
   - `app/src/main/assets/web/camino/exfig/` (figuras recortadas)
   - `descartes.txt` en la carpeta del banco, con cada pregunta que no entró y por qué.

   Los dos primeros están en `.gitignore`. Si faltan, el Camino funciona igual y la sección «Exámenes UNAMBA» no aparece.

## Formato de cada línea

```json
{"id":"<clave>-<n>","examen":"I Examen CPU 2023 (enero–marzo), grupo A","anio":2023,"grupo":"A","n":12,
 "curso":"ARI","tema":"mcd y mcm","e":"enunciado con $\\frac{3}{4}$","o":["…","…","…","…","…"],"c":3,
 "x":["paso 1","Respuesta: …"],"fig":"fig/<id>.jpg","verificada":true,"nota":""}
```

- `curso`: RM, RV, LEN, LIT, ARI, ALG, GEO, TRI, FIS, QUI, BIO, ZOO, ECO, HIS, GEG, CIV, ECN, PSF o EST.
- Fórmulas en LaTeX de KaTeX entre `$…$` (sin `$$`). Soles: `S/ 20`. Nunca el signo `$` para dólares.
- `o`: 5 alternativas distintas en el orden del cuadernillo. `c`: índice 0–4 según la resolución del equipo.
- `verificada: false` deja la pregunta fuera de la app (ilegible, faltan datos, dos respuestas posibles).

## Qué valida el armador

5 alternativas distintas, respuesta en rango, curso conocido, explicación presente, cada fórmula compila con KaTeX, la
figura existe. Las repetidas (mismo enunciado y alternativas: tronco común entre grupos o copias en las
compilaciones) entran una sola vez.
