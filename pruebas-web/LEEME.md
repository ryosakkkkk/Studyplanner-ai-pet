# Pruebas de la página del camino

Desde esta carpeta (`pruebas-web`), en PowerShell.

## Sin instalar nada (solo Node)

```powershell
npm run datos          # generadores (40 semillas x 3 dificultades), datos y plan, repaso espaciado
```

Esperado: `total 4320 fallas 0`, `fallas 0`, `repaso: … fallas 0`.

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
| `repaso_test.js` | (Node) Cajas de Leitner 1-3-7-14-30 días, temas vencidos y repaso intercalado. |
| `puente_test.js` | Llamadas a `AndroidCamino` con los tipos que espera `CaminoBridge` (Kotlin): guardar, eventos, voz (`hablar`, `escuchar`, `caminoOido`) y el resumen para la tarjeta. |
| `nuevos_test.js` | Los 18 temas de 2025–2026: respuesta inmediata al fallar y las falladas otra vez al final. |
| `ui1.js`, `ui2.js`, `ui3.js` | Recorrido general: grupo, diagnóstico, camino, curso, tema, práctica y fin. |
| `pto_debug.js` | Que ningún signo de puntuación quede solo en una línea después de una fórmula. |

En la versión 0.25.0 las pruebas de pantallas se actualizaron al nuevo método, pero en la PC desktop-8sqpasg no se
corrieron (Playwright no está instalado allí). La página se revisó a mano en el navegador a tamaño de teléfono.
