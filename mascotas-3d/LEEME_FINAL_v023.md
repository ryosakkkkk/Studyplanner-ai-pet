# Mascotas 3D — versión 0.23 terminada (11 de noviembre de 2026)

Estas son las seis mascotas que van en la app 0.23.0 (`app/src/main/assets/web/pets`). Este paquete va encima de
`v023_mascotas.zip` (que trae las texturas y los modelos de origen).

## Qué hay aquí
- Los GLB finales, las máscaras de zonas y el visor ya están en `app/src/main/assets/web/` (no se repiten aquí).
- `blender/`: fuentes de Blender 5.2 de cada mascota (`<mascota>_rig4.blend` con el esqueleto y `<mascota>_anim.blend` con las 17 animaciones).
- `pipeline/rig4/`: los scripts que las construyen (`build22.sh <mascota>`).
- `pruebas/`: barrido con el visor (`v23test/scan.js`), hojas de antes y después (`v23test/cmp.py`), medición de aristas (`qa/qa23.py`) y compatibilidad (`qa/compat23.py`).
- `resultados/`: tablas, registros y las hojas de antes y después.

## Resultado medido
Barrido con el visor real: se cuentan los píxeles de caras traseras visibles, que en la app son huecos por donde se ve el fondo.

Celebrar y saludar (las 12 combinaciones, cada 2 cuadros, 24 vistas) — `resultados/comparacion_final_celebrar_saludar.csv`:

| Mascota | Celebrar | Saludar | Piezas sueltas (máx. px) |
|---|---|---|---|
| Pingüino | −98 % | −98 % | 2209 → 3 |
| Zorro | −94 % | −93 % | 0 → 0 |
| Panda | −90 % | −84 % | 1752 → 0 |
| Coneja | −86 % | −87 % | 3 → 1 |
| Gato | −85 % | −77 % | 3 → 0 |
| Búho | −80 % | −81 % | 1 → 0 |

Las 17 animaciones de las seis (102 combinaciones, cada 4 cuadros, 8 vistas) — `resultados/comparacion_todas_las_animaciones.csv`:
ninguna queda peor que en 0.22.2.

| Mascota | Reducción de huecos (de la peor a la mejor animación) |
|---|---|
| Pingüino | 96 a 98 % |
| Zorro | 93 a 95 % |
| Coneja | 87 a 92 % |
| Panda | 77 a 91 % |
| Gato | 74 a 90 % |
| Búho | 69 a 97 % |

Compatibilidad con la app (`pruebas/qa/compat_<mascota>_p9.log`): las seis dan «compatible». Nombres de nodos, jerarquía,
articulaciones, los 17 clips con su duración y sus canales, materiales, atributos, MouthOpen, escala y orientación son
iguales a 0.22.2. Lo único que cambia es el reposo de los huesos del brazo, porque el pivote pasó al arranque del brazo.

## Qué se hizo y qué no
- Sin transparencia, sin dibujar por ambas caras todo el material, sin suavizado ni más polígonos como arreglo automático.
- Costuras cosidas, recorte limpio del brazo, paredes del brazo y del cuerpo con el color de alrededor, pivote en el
  arranque del brazo (gato y coneja con manga en tubo), pesos del cuerpo iguales a 0.22.2 y forro solo donde hay borde
  abierto (en la coneja, además, donde el barrido mostró el reverso).
- Triángulos frente a 0.22.2: pingüino +1 %, gato +3 %, zorro +4 %, panda +6 %, coneja +19 % (por el forro del reverso) y búho −17 %.

## Lo que queda a la vista
- Búho: parche claro en el cuerpo bajo el ala levantada (la pared es de un solo color y ahí el chaleco es azul).
- Panda: parche claro bajo el brazo.
- Gato y coneja: hay triángulos largos escondidos entre la mano y el cuerpo. No se ven en las imágenes desde ningún
  ángulo probado, pero la medición de aristas los marca.
- No se probó en un teléfono: todo se midió con el mismo visor de la app en un navegador.
