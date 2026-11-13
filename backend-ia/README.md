# Servidor de StudyPlanner: IA (Gemini, Groq y Cloudflare gratis, o GPT-5 mini) y cuentas con copia en la nube

Servidor pequeño que responde las preguntas libres de la mascota (por ejemplo, «explícame la fotosíntesis»). Lo demás (agenda, tareas, recordatorios, práctica) funciona en el teléfono sin Internet.

- **Con tu clave de OpenAI:** responde con **GPT-5 mini** (`gpt-5-mini`, API Responses, razonamiento mínimo para que conteste rápido y salida JSON estricta).
- **Con tu clave de Gemini (gratis, sin tarjeta):** responde con Gemini Flash (`gemini-flash-latest`). Si Gemini llega a su límite o falla, responde el siguiente de la cadena gratuita.
- **Con tu clave de Groq (gratis, sin tarjeta, opcional):** segundo respaldo (`openai/gpt-oss-120b`, unas 1 000 preguntas al día en el plan gratuito). Cadena: Gemini → Groq → Cloudflare.
- **Sin clave:** usa la IA gratuita de Cloudflare (Llama 3.3 70B), como antes.
- Límite del servidor: 500 preguntas por día y 12 por minuto (`DAILY_LIMIT`, `MINUTE_LIMIT` en `wrangler.toml`).

La clave de OpenAI **nunca** va dentro de la app: se guarda como secreto del servidor. La app solo conoce la dirección del servidor y un código de acceso propio (`APP_TOKEN`).

## Activar (una sola vez)

1. Ten instalado Node.js 22 o superior.
2. Doble clic en `ACTIVAR_IA.bat`.
   - Se abre el navegador: entra a tu cuenta de Cloudflare (o crea una gratis) y acepta.
   - Si pregunta por un subdominio `workers.dev`, escribe uno (por ejemplo, tu nombre).
   - Te pedirá tu clave de Gemini (de https://aistudio.google.com). Pégala con clic derecho, o pulsa Enter para usar solo la IA gratuita de Cloudflare.
   - Al final muestra una respuesta de prueba y guarda la conexión en `android-app\ia_local.properties` (no se sube a git).
3. Vuelve a compilar la app (`android-app\claude_compilar_rapido.bat`). En la pantalla **Hablar** aparecerá «¿Activar respuestas con IA?»: un toque y listo.

## Poner o cambiar la clave de Gemini después

Doble clic en `PONER_CLAVE_GEMINI.bat` y pégala. No hace falta volver a compilar la app.

## Poner la clave de Groq (opcional)

1. Crea la clave gratis en https://console.groq.com/keys.
2. Doble clic en `PONER_CLAVE_GROQ.bat` y pégala. No hace falta volver a compilar la app.

## Cuentas y copia en la nube

- `POST /cuenta/registro` `{nombre, correo, clave}` y `POST /cuenta/entrar` `{correo, clave}` → `{sesion, nombre, correo, copia}`.
- `GET /copia` y `PUT /copia` `{datos, hash}` con la cabecera `X-Sesion`; `POST /cuenta/salir`; `GET /cuenta/yo`.
- La contraseña nunca viaja: el teléfono envía una clave derivada con PBKDF2-SHA256 (100 000 vueltas) y el servidor guarda solo un hash con sal propia. Cinco intentos fallidos bloquean la cuenta 5 minutos. Hasta 200 cuentas nuevas por día.
- Cada cuenta es un Durable Object con SQLite (plan gratuito de Cloudflare). La copia (agenda, perfil, práctica, colores de la mascota) se guarda en trozos de 1 MB; máximo unos 4,5 MB comprimidos.
- Se activa con el mismo `npx wrangler deploy` (ya está en `wrangler.toml`, migración `v2`).

## Poner o cambiar la clave de OpenAI después

1. Crea la clave en https://platform.openai.com/api-keys.
2. Doble clic en `PONER_CLAVE_OPENAI.bat` y pégala (clic derecho). No hace falta volver a compilar la app.

## Modelo y costo

- `OPENAI_MODEL = "gpt-5-mini"` en `wrangler.toml`. Precio publicado por OpenAI: 0,25 US$ por millón de tokens de entrada y 2,00 US$ por millón de salida (consultado el 05/11/2026). Una pregunta típica (≈1 300 tokens de entrada con las instrucciones y ≈150 de salida) cuesta del orden de 0,0006 US$.
- **Aviso:** OpenAI anunció que `gpt-5-mini` se retira de la API el **11/12/2026** y recomienda `gpt-5.6-terra`. Desde esa fecha basta cambiar la línea `OPENAI_MODEL` y ejecutar `npx wrangler deploy`. Si el nuevo modelo no admite el nivel de razonamiento «minimal», el servidor reintenta solo sin ese parámetro.
- Límite de uso del servidor: 500 preguntas por día y 12 por minuto (`DAILY_LIMIT`, `MINUTE_LIMIT`), para que no haya gastos sorpresa.
- La voz de las respuestas usa el motor de voz de Android (gratis y sin conexión).

## Privacidad y seguridad

- Solo se envía la pregunta actual, la zona horaria y la fecha/hora. No se envían nombre, agenda, audio ni historial. Sin memoria entre preguntas. Con OpenAI se pide `store: false`.
- Las instrucciones del modelo piden lenguaje adecuado para menores, no pedir datos personales y, ante angustia o peligro, recomendar hablar con un adulto de confianza y la Línea 113 (opción 5).
- Toda orden que modifique la agenda vuelve al teléfono, se valida allí y espera la confirmación del estudiante.
- Revisa las condiciones de uso de OpenAI sobre edad de las personas usuarias antes de un uso con estudiantes menores de edad.

## Contrato

`POST /chat` con `Authorization: Bearer APP_TOKEN` y JSON `{ "message": "…", "zoneId": "America/Lima", "now": "2026-11-05T18:00:00Z" }` → `{ "kind": "answer|command|clarify", "text": "…" }`. `POST /check` comprueba el acceso sin usar la IA. `GET /health` → `{ "configured": true|false, "provider": "openai|gemini|groq|workers-ai|null" }`.

## Pruebas

`npm test` (Node 22): 25 pruebas con la IA simulada (validación, límites, JSON, GPT-5 mini, Gemini, Groq, cadena de respaldo, errores de clave y cuota) y con cuentas simuladas (crear, entrar, copia en trozos, bloqueo, cierre de sesión, topes).
