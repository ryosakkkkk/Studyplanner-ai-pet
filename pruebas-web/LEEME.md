# Pruebas de la página del camino

Desde esta carpeta (`pruebas-web`), en PowerShell.

## Sin instalar nada (solo Node)

```powershell
npm run datos          # generadores (40 semillas x 3 dificultades), datos y plan, guía socrática
```

Esperado: `fallas 0`, `fallas 0`, `guía bien, fallas 0`.

## Pantallas (Playwright, a tamaño de teléfono)

La primera vez hay que instalar Playwright y su Chromium (unos 150 MB; pedir permiso al usuario antes):

```powershell
npm install
npx playwright install chromium
```

Luego, en una ventana `npm run servidor` (deja la página en http://127.0.0.1:8771/camino/index.html) y en otra
`npm run pantallas`. Cada prueba acepta `claro` u `oscuro` y deja capturas en `cap\`. Todas terminan en
`fallas 0` y `errores de página 0`.

| Prueba | Qué revisa |
|---|---|
| `socratico_test.js` | Guía sin revelar la respuesta, pasos, reintentos, «No lo sé», «Ver la solución», eventos, conversación con el tutor, sin IA, resumen. |
| `externa_test.js` | Pregunta que llega desde la práctica nativa (`pendiente()`), datos dañados, texto con etiquetas, aviso de demora. |
| `puente_test.js` | Llamadas a `AndroidCamino` con los tipos que espera `CaminoBridge` (Kotlin) y el resumen para la tarjeta. |
| `nuevos_test.js` | Los 18 temas de 2025–2026 y los cursos de cada grupo. |
| `ui1.js`, `ui2.js`, `ui3.js` | Recorrido general: grupo, diagnóstico, camino, curso, tema, práctica y fin. |
| `pto_debug.js` | Que ningún signo de puntuación quede solo en una línea después de una fórmula. |
| `peticiones_tutor.js` | Saca las peticiones reales que la app manda al tutor (para `PROBAR_TUTOR_IA.bat`). |
