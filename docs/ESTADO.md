# StudyPlanner AI Pet — estado del proyecto (11 nov 2026, 18:40)

Entrega FERCYT categoría E (artículo + diseño). Presentación con la APK: 13 nov. PC actual: usuario `USER`, equipo `desktop-1n2njj8`. La PC anterior tenía el proyecto en `C:\Users\Usuario\Documents\ChatGPT\FERCYT 2`.

## Versión 0.24.2 (11 nov, tarde): método socrático, tutor con IA y exámenes 2025–2026
- **Estado:** compilada en la PC nueva (versionCode 41): `BUILD SUCCESSFUL`, 563 pruebas con 0 fallas, lint sin errores. APK en `Descargas\StudyPlanner-AI-Pet-0.24.2-debug.apk` y en `Descargas\examenes\studyplanner_0230`. La 0.24.1 (versionCode 40) dio `BUILD SUCCESSFUL`, 563 pruebas con 0 fallas y lint con 0 errores y 58 avisos. **No se probó en teléfono ni en emulador** (esta PC no tiene emulador).
- Versiones del día en esta PC: 0.23.1 (558 pruebas, 0 fallas), 0.24.0 (socrático y tutor), 0.24.1 (exámenes 2025–2026 y lint limpio) y 0.24.2 (espera más larga para el tutor). La 0.23.0 se compiló en la PC anterior y se vio en su emulador (tarjeta en Estudiar, elección de grupo y diagnóstico).
- **Método socrático en Camino UNAMBA** (`assets/web/camino/app.js`, `guia.js`):
  - El primer intento es el que cuenta para el dominio del tema. Si falla o marca «No lo sé», no se muestra la respuesta: aparece «Tu tutor pregunta» con una pregunta por cada paso de la solución («Ya lo pensé: ver este paso»), la alternativa fallada queda descartada y puede volver a responder. «Ver la solución» siempre está. Al tercer error o al agotar la guía se muestra la respuesta.
  - Guía escrita para los 36 temas generados (por tipo de enunciado) y dos preguntas de guía para cada uno de los 30 temas de preguntas fijas. Funciona sin Internet.
  - El resumen separa «N de 5 a la primera» de las que salieron con la guía. Eventos nuevos hacia la app: `duda` y `guiada`.
- **Tutor con IA (API propia):** conversación dentro de cada pregunta («Conversar con el tutor»), tutor libre desde el camino («Preguntarle al tutor») y por tema. La página arma la petición (pregunta, alternativas, respuesta correcta, solución de referencia, últimos 6 turnos y reglas: no decir la respuesta, una sola pregunta por turno, máximo 70 a 90 palabras) y la app solo la reenvía (`CaminoBridge.tutorIA` → `AiTransport.chat` → `caminoTutor(id, ok, texto)`).
  - Usa el servidor que ya tenía la app (Cloudflare Worker `studyplanner-ia`, hoy con Gemini; respaldo Workers AI). Límites del servidor: 12 peticiones por minuto y 500 por día. Necesita Internet y la IA activa en Conectar IA. La clave del proveedor sigue solo en el servidor.
  - Aviso fijo bajo la conversación: «Responde una IA y puede equivocarse. Lo que escribes se envía al servidor de la app.»
  - Si el servidor devuelve una orden de agenda, el tutor responde «Aquí solo te ayudo a estudiar…». Los fallos se explican sin hablar de comandos (`CaminoAiPrompt.failureText`).
  - **Probado contra el servidor real (11 nov, 17:50, desde la PC con `PROBAR_TUTOR_IA.bat`):** 5 peticiones tal como las arma la app, todas HTTP 200 y `kind=answer`. El tutor no reveló la respuesta en ninguna de las tres que lo prohibían («No has intentado resolver el problema. ¿Cuál es el valor de x en el vértice de la parábola?»). Una respuesta llegó cortada a media frase y otra fue confusa.
  - **El servidor tarda mucho:** 22 a 24 s por respuesta del tutor y 15 a 22 s incluso con un mensaje de una línea (`PROBAR_SERVIDOR_IA.bat`); `/health` y `/check` responden en menos de medio segundo y `/health` dice `provider: gemini`. La demora está entre el servidor y el proveedor de IA (probablemente Gemini no contesta a tiempo y responde el respaldo). No se pudo ver el registro del servidor desde aquí.
  - Por eso la 0.24.2 espera hasta 45 s la respuesta (antes 25 s), a los 9 s muestra «Sigo pensando… A veces el tutor tarda cerca de medio minuto.» y pide a la IA un solo párrafo sin comillas dobles.
- **Los 52 temas de la app** ya guiaban con pistas sin dar la respuesta. Ahora, con la IA activa, la práctica guiada muestra «Preguntarle al tutor»: abre la conversación sobre el ejercicio abierto (`TutorSession.tutorBrief()` → `CaminoHandoff` → `AndroidCamino.pendiente()`), y «Volver a mi práctica» regresa al mismo ejercicio. En la comprobación sin ayuda no aparece.
- **«examenes 2»** (Word con 611 fotos de cuadernillos 2022–2026: CPU, ordinario I-2025, extraordinarios): leído con OCR solo para catalogar (`exam2/ocr`, `exam2/preguntas.json`, unas 590 preguntas con curso).
  - Hallazgo: en los exámenes 2025 y 2026 los tres grupos tienen 10 bloques. A: RM 10, RV 10, Comunicación 5, Aritmética 5, Álgebra 5, Geometría 5, Trigonometría 5, Física 5, Química 5, Ecología 5 (igual que antes). **B:** RM 10, RV 10, Comunicación 5, Aritmética 5, Álgebra 5, Física 5, Química 5, Biología 5, Zoología 5, Ecología 5. **C:** RM 10, RV 10, Comunicación 5, Aritmética 5, Álgebra 5, Ecología 5, Economía 5, Historia 5, Cívica 5, Geografía 5.
  - El camino de B y C se actualizó a esa distribución (`datos2.js`). Curso nuevo Zoología. Literatura y Psicología/Filosofía ya no entran en ningún grupo (sus temas siguen en los datos, sin mostrarse).
  - 18 temas nuevos con 150 preguntas propias (ninguna copiada), cada una con explicación: Ecología 3 (niveles y relaciones; cadenas y ciclos; suelo, clima y ozono), Lenguaje 2 (lengua, habla y variedades; fonemas), Razonamiento verbal 1 (semántica y etimología), Cívica 2 (familia y sucesión; derechos humanos, nación y defensa civil), Historia 4 (fuentes y prehistoria; origen de la cultura andina; mundo antiguo; virreinato y República), Geografía 2 (Tierra y principios; Perú), Economía 2 (necesidades y bienes; dinero, PBI y ciclo), Zoología 2 (invertebrados; vertebrados).
  - Total del camino: 118 temas (52 de la app, 36 generados, 30 con 230 preguntas fijas). Grupo A 79 temas, B 76, C 74. El diagnóstico queda en 12 preguntas para los tres grupos.
- **Probado aquí (navegador a tamaño de teléfono, claro y oscuro):** `socratico_test.js` (guía, reintentos, eventos, tutor, sin IA, resumen), `externa_test.js` (pregunta que llega de la app, datos dañados, texto con etiquetas), `nuevos_test.js` (los 18 temas, 90 preguntas), `puente_test.js`, `guia_test.js` (4320 preguntas, ninguna con guía genérica), `plan_test.js`, `gen_test.js`, `pto_debug.js` (180 preguntas, 0 con puntuación suelta). En Kotlin: `CaminoStoreTest` y `TutorSessionTest` (55 pruebas) con kotlinc, y las 563 del proyecto en la PC.
- **Lint:** corregidos los 2 errores que tenía la 0.23.1 (`local.properties` con `C\:` y el puente del WebView con una fábrica con tipo).
- En `studyplanner_0230` quedaron `PROBAR_TUTOR_IA.bat` y `PROBAR_SERVIDOR_IA.bat` (leen la conexión del proyecto sin mostrarla y dejan `prueba_tutor_ia.txt` y `prueba_servidor_ia.txt`; cada corrida gasta 5 y 3 peticiones del límite diario) y `fuentes\camino_herramientas_0242.zip` (página, pruebas, herramientas y catálogo de «examenes 2»).
- **Cómo compilar otra vez:** dejar un `parche.zip` (rutas desde `app/`) junto a `CONSTRUIR_APK.bat` y abrirlo con doble clic; tarda de 1 a 3 minutos.
- **No incluye:** simulacro con tiempo, copia en la nube del avance del camino, conversación con voz en el tutor, ni guía escrita pregunta por pregunta en los temas de preguntas fijas (llevan dos preguntas de guía por tema).

## Versión 0.23.0 (11 nov): Camino UNAMBA y uniones 3D terminadas
- **Estado:** compilada en la PC anterior (11 nov, `BUILD SUCCESSFUL`) e instalada en su emulador; allí se vieron la tarjeta, la elección de grupo y una pregunta del diagnóstico. En la PC nueva se compiló como 0.23.1 (558 pruebas, 0 fallas).
- **Cómo compilar en esta PC:** carpeta `Descargas\examenes\studyplanner_0230` con `CONSTRUIR_APK.bat`, `proyecto_00.zip` a `proyecto_05.zip` y `LEEME.txt`.
  - El .bat copia el proyecto a `C:\Users\USER\studyplanner-build\android-app`, descarga la primera vez JDK 17 (adoptium.net), Gradle 9.6.0 (gradle.org) y las herramientas de Android (dl.google.com, con suma SHA-256 comprobada), pide aceptar las licencias de Android (lo hace el usuario escribiendo `y`), instala la plataforma 36, compila `:app:assembleDebug`, copia la APK junto al .bat y corre pruebas y lint.
  - Deja junto al .bat: `construir_log.txt`, `errores_compilacion.txt`, `resumen_pruebas.txt`, `pruebas_lint_log.txt`, `lint_0.23.0.txt`.
  - Correcciones: un `parche.zip` junto al .bat se extrae encima del proyecto en la siguiente corrida.
  - La clave de pruebas de esta PC es otra: en el teléfono hay que desinstalar la versión anterior antes de instalar esta APK.
  - Paso de archivos a la PC: por partes de menos de 7 MB (un zip de 28 MB fue rechazado). Tras reemplazar un archivo recién escrito hay que comprobar el tamaño en la PC: una vez llegó la copia anterior.
- **Camino UNAMBA (pedido del 11 nov):** en Estudiar, arriba, una tarjeta lleva a elegir el grupo (A Ingenierías, B Biomédicas, C Sociales). Luego un diagnóstico corto (12 o 13 preguntas, una o dos por curso del examen del grupo, con «No lo sé») y un camino ordenado por lo que más pesa en el examen del grupo y lo que más falta.
  - Pesos por grupo sacados de los cuadernillos: A (CPU 2023) RM 10, RV 10, Lenguaje 5, Aritmética 5, Álgebra 5, Geometría 5, Trigonometría 5, Física 5, Química 5, Ecología 5. B y C según los exámenes 2013–2014 que había.
  - 100 temas: los 52 de la app (se abren con su lección, pistas y comprobación de siempre) y 48 nuevos (36 con preguntas generadas y 12 con 80 preguntas fijas).
  - Los 36 generadores hacen preguntas del mismo tipo y dificultad que las de los exámenes, con datos distintos cada vez, respuesta calculada y explicación paso a paso con notación de libro (KaTeX) y figuras (planos, rectas, Venn, triángulos, vectores, tablas). Cada pregunta dice qué examen sirvió de modelo. **Los enunciados de los exámenes no se copiaron.**
  - Funciona sin Internet. Si la IA en la nube está activa aparece «Otra explicación».
  - La práctica del camino cuenta para la racha y la meta diaria de Hoy. La mascota reacciona a aciertos, errores y logros.
  - Archivos: `assets/web/camino/` (página, 34 archivos, 0,9 MB), `ui/tutor/CaminoScreen.kt`, `data/tutor/CaminoStore.kt`, cambios en `HomeScreen.kt`, `TodayScreen.kt`, `StudyPlannerApp.kt` y `PetWebView.kt` (tipos css, woff2 y svg).
  - Probado aquí: generadores (4320 casos, 0 fallas; toda fórmula compila en KaTeX), orden del camino, recorrido de pantallas en claro y oscuro a tamaño de teléfono (0 errores), puente con la app simulado (`test/puente_test.js`), 9 pruebas de `CaminoStoreTest` con kotlinc. `CaminoScreen.kt` compila contra réplicas mínimas de Android y Compose; con las librerías reales todavía no.
  - No incluye: simulacro con tiempo, copia en la nube del avance del camino, ni preguntas nuevas para todos los temas de los exámenes.
- **Exámenes:** 14 PDF, 553 páginas escaneadas. Se leyeron con OCR solo para catalogar (unas 980 preguntas por curso, `exam/cat3.json`) y sacar los pesos por grupo.
- **3D (uniones de brazos y alas):** terminado e integrado en `assets/web/pets` (seis GLB, máscaras de zonas y visor). Sin cambios de código.
  - Medido con el visor real, celebrar y saludar, cada 2 cuadros, 24 vistas, píxeles de huecos visibles frente a 0.22.2: zorro −94 y −93 %, pingüino −98 y −98 %, panda −90 y −84 %, coneja −86 y −87 %, gato −85 y −77 %, búho −80 y −81 %. Piezas sueltas: panda 1752 → 0 px, pingüino 2209 → 3 px. Tabla: `comparacion_final_celebrar_saludar.csv`.
  - Las 17 animaciones de las seis (102 combinaciones, 8 vistas, cada 4 cuadros): ninguna peor que en 0.22.2. Reducción de la peor a la mejor animación: pingüino 96 a 98 %, zorro 93 a 95 %, coneja 87 a 92 %, panda 77 a 91 %, gato 74 a 90 %, búho 69 a 97 %. Tabla: `comparacion_todas_las_animaciones.csv`.
  - Compatibilidad (`compat23.py`): las seis dan «compatible» (nombres, huesos, jerarquía, escala, 17 clips con sus duraciones y canales, MouthOpen, materiales y atributos iguales a 0.22.2). Solo cambia, como se esperaba, el reposo de los huesos del brazo (el pivote va al arranque del brazo).
  - Sin transparencia, sin doble cara general, sin suavizado ni más polígonos como arreglo automático. Forro solo donde hay borde abierto; en la coneja además donde el barrido mostró el reverso.
  - Queda a la vista: parche claro bajo el ala levantada del búho y bajo el brazo del panda (la pared del cuerpo es de un solo color). En gato y coneja hay triángulos largos ocultos entre mano y cuerpo que no se ven en las imágenes.
  - Triángulos frente a 0.22.2: pingüino +1 %, gato +3 %, zorro +4 %, panda +6 %, coneja +19 % (forro del reverso), búho −17 %.
  - Entregado: `v023_final_mascotas.zip` (57 MB: GLB finales, fuentes de Blender, scripts, pruebas, tablas, hojas de antes y después y `LEEME_FINAL_v023.md`). Va encima de `v023_mascotas.zip`.
  - En la PC, `studyplanner_0230\fuentes\camino_herramientas.zip`: página del camino, pruebas, herramientas, el JSONL y el catálogo de los exámenes.
- **Modelo abierto dentro de la app (pedido del 10 nov):** no se entrenó ni se integró. Aquí no hay GPU ni acceso a Hugging Face, y no se puede probar en teléfono antes del 13.
  - Lo que explica hoy dentro de la APK, gratis y sin Internet, son las explicaciones calculadas paso a paso.
  - Investigado (nov 2026): LiteRT-LM tiene API Kotlin estable y modelos `.litertlm` listos (Gemma 3 1B, 1,0 GB; Gemma 4 E2B, 2,6 GB; Qwen 0.5B, 0,5 GB). El modelo se descargaría aparte de la APK. Un modelo de 0,5 a 1 GB sin ajustar se equivoca en matemática.
  - Dejado para después de la feria: `camino/datos/explicaciones_unamba.jsonl` (3555 preguntas con respuesta y explicación) como datos para ajustar un modelo en un equipo con GPU.
- **No encontrado:** «Integración 3D Blender para APK». En el proyecto de Claude solo está este documento.

### Causas comprobadas de las fallas 3D (para referencia)
- No había transparencia de material ni de textura: se veía el fondo a través de huecos reales.
- El cuerpo no tenía superficie bajo el brazo y el brazo giraba alrededor de un punto dentro del torso.
- El recorte del brazo arrastraba piezas ajenas.
- La malla de origen viene partida en parches sin unir (24 a 38 m de borde abierto por mascota) y se abría al moverse.
- Correas, cuellos y el interior de la mochila no tenían cara interior.
- Corrección (`rig4/`): `zip23.py` (costuras), `limpia23.py` y `fair23.py` (recorte), `walls23.py` (paredes), `paridad23.py` (pesos del cuerpo como en 0.22.2, brazos en tubo para gato y coneja), `forro23.py` (forro donde hay borde abierto), `anim22.py` (animaciones sin cambios).

## Versión 0.22.2 (9 nov, noche): brazos sin deformaciones
- **Pedido:** al moverse, las seis mascotas mostraban bordes irregulares y blancos en los hombros con los brazos abiertos.
- **Causa:** los brazos del modelo eran un relieve pegado al costado, sin cara interior. (1) Lo que estaba entre brazo y torso (correa, costado de la casaca, borde negro del hombro del panda) se iba con el brazo y colgaba como aleta. (2) El brazo quedaba hueco por debajo y su «falda» salía como picos. (3) En gato, coneja, zorro y búho el costado tenía huecos junto al hombro: por ahí se veía el fondo (los bordes blancos). (4) Correas cerca del borde seguían al ayudante del brazo y se estiraban. (5) En el cuello, aristas de menos de 1 mm con pesos distintos hacían picos al mover la cabeza.
- **Corrección (`blender_avatares\v0222`, armfix.py):** lo que mira hacia el brazo desde el torso se queda en el cuerpo; el brazo y el costado se cierran con paredes suaves (mapa armónico de un disco, escondidas en reposo) con el color de la manga y del cuerpo; el parche cubre toda la huella del brazo y su borde toma el color exacto de alrededor; las piezas que sobresalen no siguen al borde y el ayudante gira como máximo 5°; puntos a menos de 3 mm toman el mismo peso (sin cola ni orejas).
- **Compatibilidad:** nombres, nodos, huesos (incluidos ArmRimL/R), jerarquía, escala, medidas, los 17 clips, MouthOpen, materiales, atributos y zonas iguales a 0.22.1 (comparado archivo contra archivo). Misma exportación. Sin cambios de código.
- **Prueba (exportar, reimportar y medir):** peor alargamiento de una arista: zorro 142→27 mm, gato 244→62, panda 149→36, búho 260→66, pingüino 154→52, coneja 82→55. Ciclos cierran sin salto. Triángulos: bajan en 4 mascotas, panda +2 %, pingüino +4 %. Cambio de color de ropa probado con los brazos levantados. Comparación: `blender_avatares\v0222\antes_despues_brazos.png`.
- **App:** 0.22.2 (versionCode 36): modelos, zonas, visor e imágenes fijas.
- **Queda:** de muy cerca y desde abajo, el borde inferior de la manga del gato y la coneja no es perfectamente liso; la mochila del pingüino de perfil ya era irregular en el modelo original.

## Versión 0.22.1 (9 nov, noche)
- **Problema de 0.21:** al girar la mascota se veía un hueco en el costado (panda, búho, pingüino, gato y coneja): el paso que cerraba el costado después de quitar el brazo no creaba ninguna cara. En 0.22.0 se cerró, pero al moverse los brazos se veían rendijas en la unión («rompecabezas») y el escudo de la ropa salía borroso.
- **Brazos (Blender 5.2, `blender_avatares\v022`):**
  - Se usa el brazo original de cada modelo (forma y textura), recortado del cuerpo; quieta, la mascota se ve como el modelo de referencia.
  - El brazo se separa por lo que sobresale del cuerpo. Se dejan en el cuerpo las piezas que no son del brazo, por color: bufanda del panda, audífonos y correas del pingüino, correa del búho. Gato y coneja: corte exacto con un plano en el hombro.
  - El costado se cierra con un parche que sigue la curva del cuerpo y toma el color de la piel de alrededor.
  - Unión sin rendijas: el borde del cuerpo sigue a un hueso ayudante (ArmRimL/R) que copia el giro del brazo cuando es chico (respirar, hablar). Normales de la unión iguales a las del modelo entero (sin línea de luz). El brazo tiene cara interior: al levantarlo se ve la manga por dentro.
  - En reposo los brazos casi no se mueven; saludar y celebrar suben menos el hombro y doblan más el antebrazo.
- **Escudo nítido:** cada escudo (pecho, mochila, bolso, bufanda) usa el escudo real de la UNAMBA en una textura propia de 1024×512 (segundo material `<mascota>_escudo`). El escudo viejo se borra rellenando con la tela de alrededor. El visor no cambia el color del escudo al cambiar la ropa; sí lo esconde con su pieza (mochila).
- **App:** modelos, máscaras de zonas, visor (excepción del escudo) e imágenes fijas nuevas; versión 0.22.1 (versionCode 35). Sin cambios de código Kotlin. Los modelos pesan lo mismo (se quitó la textura de rugosidad y metal, que el visor no usa).
- **Verificado aquí (visor con el mismo motor de la app, sin teléfono):** costados cerrados en las seis mascotas de frente, lado y espalda; unión de los brazos sin rendijas en reposo y al hablar; colores de ropa y mangas funcionan y el escudo no cambia de color. Imágenes `entregables\costados_antes_ahora_0.22.png` y `entregables\escudos_0.22.png`, video `entregables\mascotas_3d_giro_0.22.1.mp4`.
- **0.22.0 en la PC:** compilación correcta, 549 pruebas en 50 clases con 0 fallas, lint con 0 errores y 46 advertencias.
- **Lo que queda:** al saludar o celebrar, el borde del brazo levantado se ve algo irregular en el búho, el pingüino y el zorro (pequeñas aletas de tela).

## Versión 0.21.0 (8 nov, noche)
- **Mascotas 3D de alta calidad (Blender 5.2, `blender_avatares\v021`).**
  - Se reconstruyeron las seis desde las fuentes de alta calidad: unos 30 000 triángulos y textura base de 2048 px.
  - Brazos macizos (cápsulas con la tela de la manga y la piel de la mano), sin transparencias.
  - Material mate: sin brillo de espejo (rugosidad 0,9, metalicidad 0, tono neutro).
  - **Coneja 3D nueva**, hecha a partir del gato: orejas largas, cola de pompón, pelaje rosado, ojos rubí, polera azul, mochila y zapatillas rosadas. Tiene las 17 animaciones, párpados con gestos, ropa por zonas y accesorios.
  - **Colores sin fugas:** cada mascota trae una máscara de zonas (`<mascota>_zonas.png`), un canal por prenda. El color pinta solo esa prenda.
  - El búho cambia de color el chaleco (ya no el plumaje).
- **Visor 0.21.**
  - Se gira con el dedo, con inercia, y a los 2 segundos vuelve de frente.
  - Cada parte responde al toque: cabeza (risa), mano (saludo), panza (feliz), pies (salto), cola (sorpresa); tres toques = baile. En Hoy la mascota dice una frase según la parte.
  - Gestos de vida cada 8 a 15 s.
  - Al cambiar de mascota, la anterior se oculta enseguida: nunca se ven dos.
  - La imagen fija ya no reemplaza a la 3D por tardar: mientras carga se ve la imagen del mismo personaje respirando. «Reintentar» solo aparece si la 3D falla dos veces o tarda más de 25 s. Las imágenes fijas son cuadros de la 3D de frente.
- **Personalización.** Vista previa grande (232 dp) con el degradado del logo y el aviso «Gírala con el dedo». Los nombres de las fichas se achican solos: no se salen de su casilla.
- **IA integrada.** La app trae la dirección y el código del servidor al compilar y deja la IA siempre encendida. Se quitaron la fila «IA externa» de Perfil, «Conectar IA» y la tarjeta «Activar / Ahora no».
- **Agendar por voz.** «Agéndame…», «anótame…» o «apúntame…» crean la tarea. Una tarea o un examen nuevos se guardan sin preguntar, la mascota lo dice y se abre la Agenda en ese día con lo nuevo marcado y el aviso «lo agendó» con «Deshacer». Borrar o cambiar sigue pidiendo «sí».
- **Estudiar.** Banco nuevo `banco_preguntas_v2.json`: 23 temas y 184 preguntas propias en el formato de admisión, alineadas al temario de la UNAMBA. No se copiaron preguntas de exámenes reales. Total: 38 temas de banco y 274 preguntas, más los temas de matemática.
  - Física: dinámica, trabajo y energía, fluidos, corriente y circuitos.
  - Química: tabla periódica, nomenclatura, estequiometría, soluciones.
  - Biología: célula, fisiología celular, biomoléculas. Zoología: mamíferos.
  - Historia: altas culturas, independencia, primer civilismo y Guerra del Pacífico.
  - Economía: producción y costos, sector público. Cívica: derechos y garantías.
  - Comunicación: categorías gramaticales, sintaxis. Razonamiento verbal: analogías, conectores, antonimia.
  - Cada pregunta tiene 5 alternativas, 3 pistas, retroalimentación por alternativa y fuentes verificadas el 8 nov. La voz lee los espacios en blanco como «espacio».
- **Investigación de IA y voz:** sección «IA y voz: precios y recomendación» en el doc de referentes.
- **Verificado el 8 nov (primera ronda, `claude_v021.bat`):**
  - Compilación correcta e instalación en el emulador. La app abrió 2 de 2 veces sin cerrar el emulador.
  - Recorrido en `tmp\capturas_v021`: la 3D aparece en Hoy (ya no la imagen quieta), gira con el dedo y vuelve de frente, cosquillas con frase «¡Jaja, cosquillas!», vista previa grande en Personalización, coneja 3D girando, cambio rápido Gato→Panda→Búho sin ver dos mascotas, las seis mascotas, tema oscuro. Video: `video_hoy_girar_y_tocar.mp4`.
  - En el emulador el renderizador del WebView se cerró una vez al salir de Hoy (`Renderer process crash detected`); la app lo recuperó sola y la 3D volvió a cargar.
  - En esa ronda el aviso de Android «Try out your stylus» tapó el teclado y no dejó probar agendar.
- **Verificado el 8 nov (segunda ronda, `claude_v021b.bat`):** el recorrido cierra ese aviso y apaga la escritura con lápiz del emulador. Incluye el ajuste de agendar (confirma solo cuando la propuesta terminó de prepararse) y las máscaras de color corregidas de coneja, gato, panda y búho (espalda y mangas).
  - Compilación correcta; 549 pruebas en 50 clases, 0 fallas.
  - Agendar escrito en el asistente («agendame Repasar quimica para manana»): se guardó sin preguntar, se abrió la Agenda en el lunes 9 con la tarea marcada «Nuevo» y el aviso «Kiro lo agendó: Repasar quimica». «Deshacer» la borró («Actividad eliminada»). Capturas en `tmp\capturas_v021b` y resumen en `entregables\recorrido_0.21.0.png`.
  - Estudiar muestra «Ver los 52» temas: el banco nuevo cargó. El recorrido no llegó a abrir el tema «Dinámica» (no buscó dentro de la lista completa).
- **Compilación final (`claude_v021c.bat`, 8 nov 19:40):** corrige el único error de lint (`PetAvatar.kt`). Compilación e instalación correctas, 549 pruebas en 50 clases con 0 fallas, lint con 0 errores y 46 advertencias de estilo. El servidor respondió `/health` (Gemini configurado) y `/chat`.
- **Entregables:** `entregables\StudyPlanner-AI-Pet-0.21.0-debug.apk` (41 MB), `recorrido_0.21.0.png` (8 capturas del emulador), `mascotas_3d_0.21.0.png` (las seis), `mascotas_3d_giro_0.21.0.mp4` (giro de las seis). Blender: `blender_avatares\v021` (6 `*_anim.blend`, 6 `*_extras.blend` y los scripts).
- **No probado:** un teléfono real (fluidez de la 3D de alta calidad, voz real), agendar por voz con micrófono (el emulador no tiene audio), y los colores en la espalda del pingüino: su textura original trae amarillo pintado en la parte baja de la espalda.

## Versión 0.20.0 (8 nov, tarde)
- **Mascotas 3D con esqueleto nuevo (Blender 5.2, `blender_avatares\v020`).**
  - Huesos: Root, Hips, Chest, Head, brazos con mano, piernas con pie, orejas y cola en cadena.
  - Los brazos se modelaron aparte del cuerpo: el saludo ya no estira la ropa ni deja la mano «pixelada».
  - Pesos limpios por zona y 17 animaciones suaves, horneadas a 30 fps en pistas NLA.
  - En el visor, la simulación (respiración, parpadeo, mirada, risa) se suma y se deshace en cada cuadro, así ya no se acumula ni deforma la cabeza.
- **Ropa y accesorios.**
  - Un botón de color por prenda abre la paleta de 25 colores.
  - Prendas por mascota:
    - Zorro: chaqueta, audífonos, mochila y botas.
    - Gato: polera, mochila y zapatillas.
    - Panda: bufanda y mochila.
    - Búho: plumaje, bolso y birrete.
    - Pingüino: gorra, corbatín y mochila.
  - Interruptor para quitar la mochila (zorro, panda y pingüino; la del gato está unida al cuerpo).
  - Accesorios 3D: lentes, corona y moño (`<mascota>_extras.glb`).
  - Las zonas se marcan con el atributo de color «zmask» en el GLB.
- **Sombreador de colores sin bucle ni `discard`.** La versión con bucle cerraba el emulador de Android al abrir la app. El error de Windows era APPCRASH de qemu, código c0000005.
  - Se probó apagando partes del dibujo (`modo` del visor, solo en la versión de prueba, con `files/visor.txt`).
  - Con el sombreador nuevo, la app abrió 2 de 2 veces y el recorrido terminó.
  - La mochila se esconde desde el vértice.
- **Sin láminas junto a la mascota 3D.** Las láminas se usan como íconos de Perfil (nube = Cuenta, ajustes, lista, seguridad, idea, holograma), en Cuenta y en los avisos de la Agenda.
- **Colores del logo** (azul #0A84FF → índigo #5E5CE6, naranja #FF9F0A). Acción principal #3A5BF0, con contraste 5,3:1.
  - Hoy: fondo de color, mascota sobre un círculo, una frase corta y tres fichas de color (Practicar 0/5, Comprobar, Repasar).
  - La racha es una llama con el anillo de la meta. «Hablar» es un botón grande con degradado.
  - Al cumplir la meta cae confeti.
  - Barra inferior con una píldora de degradado en la pestaña elegida.
- **Cuentas y copia en la nube.**
  - Entrada con «Crear cuenta», «Ya tengo cuenta» y «Seguir sin cuenta». En Perfil, la fila «Cuenta» permite guardar ahora, recuperar la copia y cerrar sesión.
  - Servidor: Durable Object por cuenta, en el mismo worker `studyplanner-ia`.
  - La contraseña no viaja: va como PBKDF2-SHA256 de 100 000 vueltas, y el servidor guarda un hash con sal.
  - Tras 5 fallos la cuenta se bloquea 5 minutos. Se aceptan hasta 200 cuentas nuevas por día.
  - La copia incluye la agenda (Room), el perfil, la práctica, los colores y la voz. No incluye la sesión ni la conexión de IA.
  - La copia se sube al salir de la app si algo cambió. Al recuperarla, la app se reinicia.
- **IA gratuita en cadena:** Gemini → Groq (opcional, `PONER_CLAVE_GROQ.bat`) → Cloudflare. La tabla de opciones está en el doc de referentes.
- **Verificado el 8 nov:**
  - Servidor publicado (versión e15cb606): `/health` = gemini, `/cuenta/entrar` sin datos = 400, `/copia` sin sesión = 401, `/chat` responde.
  - 25 pruebas del servidor.
  - App: 548 pruebas en 49 clases, 0 fallas, también después de los últimos ajustes. Lint: 0 errores, 46 advertencias de estilo.
  - Recorrido en el emulador, en `tmp\capturas_v020` y `capturas_v020g`: entrada, formularios de cuenta, Hoy claro y oscuro, cosquillas, práctica, Perfil, Cuenta, Personalización (paleta, chaqueta roja, sin mochila, lentes y corona), Reacciones, las 5 mascotas, Asistente, Estudiar y Agenda.
  - Video del visor: `mascotas_3d_0.20.0.mp4`.
- **No probado:** crear una cuenta real y recuperar la copia en un teléfono, porque no se escribieron credenciales en la app. Tampoco se probó la fluidez en un teléfono real.
- **Scripts:**
  - `claude_v020.bat`: publica el servidor, compila, prueba y hace lint.
  - `claude_v020f.bat`: compila, comprueba que el emulador aguante y hace el recorrido.
  - `claude_v020g.bat`: recorrido de Personalización.
- APK: `entregables\StudyPlanner-AI-Pet-0.20.0-debug.apk`. Capturas: `entregables\capturas_0.20.0.png`. Video: `entregables\mascotas_3d_0.20.0.mp4`.

## Versión 0.19.1 (8 nov, madrugada)
- **Párpados v2 hechos en Blender** (`blender_avatares\v019_esqueleto_y_emociones\lids2.py`). El párpado sigue una superficie suavizada de la cara: ya no tiene bultos, la línea de pestañas no se quiebra y el ojo no se asoma por debajo. Malla de 22 x 22 por párpado. Se exporta igual que antes, como `assets/web/pets/<mascota>_cara.glb`.
- **Escenas de Blender por mascota**, en `blender_avatares\v019_esqueleto_y_emociones\<mascota>_esqueleto_y_emociones.blend`. Cada una trae:
  - el esqueleto (Root, Hips, Head, ArmL, ArmR, LegL, LegR, Tail) con las 17 animaciones;
  - los párpados pegados al hueso Head;
  - la boca MouthOpen;
  - la pista NLA «Emociones», con marcadores Reposo, Feliz, Risa, Triste, Enojado, Pensando, Sorpresa, Dormido, Celebra y Saluda.

  `emociones_blender.png` es el render de Cycles de las 10 emociones de las 5 mascotas.
- **Hoy.** La misión Repaso ahora explica su estado: «Aparece después de comprobar un tema» o «Al día: no hay repasos pendientes».
- **Video:** `mascota_viva_0.19.1.mp4`, 20 s, con estas escenas: reposo, cosquillas, feliz, triste, pensando, enojado, dormido y celebra.
- **Verificado en la PC el 8 nov:**
  - 543 pruebas en 48 clases, 0 fallas.
  - Lint: 0 errores, 37 advertencias.
  - APK: `entregables\StudyPlanner-AI-Pet-0.19.1-debug.apk`.
  - Recorridos en el emulador (`tmp\capturas_v019d`, `capturas_v0191`, `capturas_v0191b`):
    - Hoy, con cosquillas: ríe con ojos ^ ^;
    - práctica con pistas, comprobación, repaso propuesto en Agenda;
    - Asistente;
    - colores de ropa y mochila;
    - galería de reacciones;
    - cambio entre las 6 mascotas;
    - ícono nuevo.
- **Emulador de la PC.** El emulador usa GPU por software y en 3 recorridos se cerró solo al cambiar de pantalla; no quedó un error de la app en el registro. Al abrir recién el emulador, Android mostró «System UI isn't responding»; el recorrido ahora toca «Wait». La mascota flotante no se volvió a comprobar en esta ronda.
- Scripts: `android-app\claude_v0191.bat`, que compila, instala, hace el recorrido y corre pruebas y lint, y `claude_v0191b_recorrido.bat`, que hace el recorrido corto.

## Versión 0.19.0 (7–8 nov)
- **Cara hecha en Blender 5.2 (bpy).** `w/blend/lids.py` mide los ojos de cada GLB y crea párpados con el color de la piel y una línea de pestañas. Trae cinco gestos (Blink, Happy ^ ^, Sad, Angry, Half) y se exporta como `assets/web/pets/<mascota>_cara.glb`, pegado al hueso Head.
- **Simulación en `pet-viewer.html` (`simulate`).** Encima de cada clip se suman respiración, parpadeo cada 2–6 s, mirada que pasea o sigue el toque, inclinación al pensar, rebote al estar feliz, cola y brazos. Cada clip tiene su cara (`EXPR`). Al tocar la cabeza, ríe (`poke`).
- **Láminas.** 47 expresiones y poses por mascota (`assets/stickers/<mascota>/`), recortadas de las láminas del usuario. Se usan como globo de actividad, en los avisos, en la mascota flotante y en la galería «Reacciones».
- **Colores.** Ropa, mochila y gorra por mascota, con la paleta de Apple, guardados en el teléfono (`PetStyleStore`). El visor los aplica con un sombreador por zonas de tono.
- **Hoy.** Anillo de la meta con la racha, globo con lámina y misiones del día (Practica con pistas x de 5, Comprueba sin ayuda, Repaso) con círculo de avance.
- **Estilo Apple.** Azul #007AFF sobre fondo #F2F2F7, barra inferior translúcida, listas agrupadas en Estudiar y Temas. Ícono nuevo hecho con el render 3D del zorro.
- **Análisis de referentes.** Duolingo, Finch, Apple Actividad y Forest, en el doc «StudyPlanner AI Pet: referentes de UI/UX y mascota animada».
- **Verificado.** 0.18: 541 pruebas en 48 clases, 0 fallas. El recorrido 0.19 está en `tmp\capturas_v019` y `tmp\capturas_v019b`.

## Versión 0.17.0 (7 nov)
- Mascotas 3D nuevas (paquete `mascotas_3d_v017`): Zorro, Gato, Panda, Búho y Pingüino en GLB (versión lite, 12 000 triángulos), con 17 animaciones y la boca `MouthOpen`. Se ven con three.js local en un WebView (`assets/web/pet-viewer.html`), sin dependencias nuevas y sin Internet. El Conejo sigue con el motor anterior porque aún no tiene GLB.
- Reacciones: `domain/pet/PetClips.kt` traduce el estado a un clip (correcta: happy; error: sad; comprobación aprobada: celebrate; pensar: think; descanso: sleep). Mientras habla usa `talk_body` y mueve la boca.
- Mascota flotante: usa la animación WebP del mismo modelo (`assets/sprites`).
- Interfaz 0.17, con pestañas Hoy · Estudiar · Agenda:
  - Hoy: saludo, racha y meta del día, mascota grande, un globo con una frase, Practicar / Comprobar / Repaso y el botón «Hablar», que abre el Asistente escuchando.
  - Práctica: se elige una alternativa y se confirma con «Responder». «Otra pista» está abajo y el contador «Pistas x/3» arriba.
  - «Comprobar» entra directo a la comprobación sin ayuda.
  - Agenda: tarjeta «Repaso propuesto» con Aceptar y Cambiar día; solo se guarda al aceptar.
- Colores: azul `#1F3FA6` y ámbar para la racha.
- Verificado en la PC el 7 nov:
  - 534 pruebas en 46 clases, 0 fallas.
  - Lint: 0 errores, 31 advertencias.
  - Recorrido en el emulador con capturas en `tmp\capturas_v017`.
- APK: `entregables\StudyPlanner-AI-Pet-0.17.0-debug.apk`. Script: `android-app\claude_v017.bat`.

## Rediseño 0.16.0 (6 nov)
- Navegación con tres pestañas fijas (Estudiar, Agenda, Asistente) y perfil desde el avatar. Se eliminó «Más opciones».
- Estudiar: saludo, «Continuar» o «Elegir tema», buscador, temas con estado y filas de Progreso, Exámenes y Modo estudio.
- Agenda: vista «Hoy» con siete días, tareas sin fecha y próximos. El calendario completo se abre con «Ver calendario». Incluye Recordatorios, Hábitos y Organizar semana, y el botón «Añadir tarea».
- Asistente:
  - Abrirlo no enciende el micrófono, y la escucha automática tras responder se quitó.
  - Cinco estados de voz con su propio botón: Hablar, Detener escucha, Cancelar, Detener audio, y Reintentar o Escribir.
  - Con el teclado abierto, la mascota se reduce y el campo y Enviar quedan visibles.
  - Las propuestas muestran tipo, título, fecha y aviso, con Guardar, Editar y Cancelar. Ya no se ven ID ni fechas ISO.
- Mascota 3D: mientras carga, o si falla, se muestra la imagen fija del mismo personaje y «Reintentar». Las seis imágenes están en `res/drawable-nodpi/pet_still_*.webp`.
- Mascota flotante: solo la mascota, sin barra de botones. Se oculta dentro de la app, no recibe toques allí y se cierra manteniéndola presionada.
- Textos: el temario va en «Detalles y fuentes», «Ir a practicar», editor de tareas en dos pasos con detalles plegados, Modo estudio con Iniciar/Pausar/Terminar.
- Maquetas: `entregables`/outputs `maquetas_estudiar_agenda_asistente.png`.
- Compilación y recorrido: `android-app\claude_redisenio.bat`, que aplica `tmp\parche.zip`, compila, instala, ejecuta `claude_recorrido_redisenio.ps1` (capturas en `tmp\capturas_redisenio`), y luego las pruebas y el lint.

## Verificado (6 nov, 17:42)
- APK 0.16.0 compilada en la PC (`entregables\StudyPlanner-AI-Pet-0.16.0-debug.apk`), con 522 pruebas en 44 clases, 0 fallas. Lint: 0 errores y 30 advertencias (20 UseKtx, 4 recursos sin usar).
- Recorrido automático en el emulador (Android 15): entrada, Estudiar, Temas, lección, Agenda, nueva tarea, Asistente, teclado, Perfil y Personalización. Capturas en `tmp\capturas_redisenio`.
- Hallazgos del recorrido, corregidos en los parches 2 a 4 (cada ronda: 522 pruebas, 0 fallas; lint sin errores):
  - Sin motor de voz, la app mostraba «No te escuché bien». Ahora avisa una vez y no lo repite.
  - Enter no enviaba el mensaje.
  - El comprobante de aviso mostraba un ID y NEEDS_EXACT. Ahora dice «Recordatorio guardado. Te avisaré a esa hora.».
- Mascota flotante comprobada: sobre la pantalla de inicio de Android se ve solo la mascota en el borde; dentro de la app se oculta.
- En el emulador no hay voz de lectura en español y el dictado falla por el audio del emulador. Por eso los estados «Escuchando» y «Reproduciendo» se verán en un teléfono real.
- Artículo actualizado: `entregables\IA_PET_ULTIMO_0.16.0.docx`.
  - Figura 3: capturas reales de la interfaz y del flujo de voz.
  - Figura 5 (nueva): mascotas 3D.
  - Resúmenes, Tabla 4, limitaciones y conclusiones pasan a la versión 0.16.0.

## Pendiente
- Crear una cuenta en un teléfono real y comprobar que la copia se recupera en otro teléfono.
- Clave de Groq (opcional, `backend-ia\PONER_CLAVE_GROQ.bat`) para el segundo respaldo de IA; clave de GPT-5 mini solo si se quiere pagar.
- Al pasar Gemini a pago: en `backend-ia\wrangler.toml` cambiar `GEMINI_MODEL` de `gemini-flash-latest` a `gemini-2.5-flash-lite` (el alias apunta al Flash más caro).
- Prueba en teléfono físico y con la placa BLE; fluidez de la 3D de alta calidad, voz real (agendar por voz con micrófono) y mascota flotante en un teléfono real.
- Comprobar la comprensión con usuarios: la usabilidad no está validada.
- Pequeños detalles de color vistos en las vistas previas: manchas rojas pequeñas en la mochila del panda y amarillo de la textura original en la espalda baja del pingüino.
- Abrir un tema nuevo del banco (por ejemplo «Dinámica») en el emulador o el teléfono y leerlo con voz.
- Simulacro de admisión que mezcle preguntas de varias áreas con tiempo (hoy se practica por tema o por el Camino UNAMBA).
- **Servidor de IA lento (15 a 24 s por respuesta):** revisar en la PC que tiene `backend-ia` qué proveedor responde (`wrangler tail`) y, si es Gemini, cambiar `GEMINI_MODEL` a un modelo rápido o poner la clave nueva si se cambió.
- Probar la 0.24.2 en un teléfono: Camino UNAMBA, guía socrática, conversación con el tutor (con Internet) y «Preguntarle al tutor» desde un tema de la app. Las APK de esta PC y de la anterior tienen firmas distintas: hay que desinstalar antes de instalar la otra.
- Copia en la nube del avance del Camino UNAMBA (hoy se guarda solo en el teléfono).
- Más preguntas para Biología y Química teórica (en «examenes 2» hay pocas páginas del grupo B) y guía escrita pregunta por pregunta en los temas de preguntas fijas.
- Actualizar el artículo a la versión 0.24.2 (método socrático y tutor con IA, distribución de cursos 2025–2026, 118 temas; mascotas 3D de alta calidad, coneja, giro y toques, IA integrada, agendar por voz, banco de 274 preguntas, Camino UNAMBA por grupo con diagnóstico y explicaciones paso a paso).
- Las pruebas instrumentadas (androidTest) aún usan los textos de la versión 0.15, por ejemplo «Crear mi espacio» y `home_pet`; con servidor, la entrada dice «Seguir sin cuenta» en vez de «Empezar».

## Preferencias del usuario
Sin emojis en la interfaz, sin marcas de IA, textos simples, sin opciones que no pidió.
