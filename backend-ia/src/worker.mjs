import { SYSTEM_PROMPT, RESPONSE_SCHEMA } from "./prompt.mjs";
import { Cuenta, COPIA_MAX, leerSesion, validarAcceso, validarCopia } from "./cuentas.mjs";
export { Cuenta };

const INPUT_BYTES = 20_000;
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43,128}$/;
const MODEL_PATTERN = /^@cf\/[a-z0-9._-]{1,40}\/[a-z0-9._-]{1,80}$/;
const DEFAULT_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast";
// GPT-5 mini (OpenAI) cuando el servidor tiene el secreto OPENAI_API_KEY; si no, la IA gratuita de Cloudflare.
const OPENAI_URL = "https://api.openai.com/v1/responses";
const OPENAI_DEFAULT_MODEL = "gpt-5-mini";
const OPENAI_MODEL_PATTERN = /^gpt-[a-z0-9.-]{1,60}$/;
const OPENAI_KEY_PATTERN = /^sk-[A-Za-z0-9_-]{20,300}$/;
const EFFORTS = ["none", "minimal", "low", "medium", "high"];
// Gemini (Google AI Studio, nivel gratuito) cuando el servidor tiene el secreto GEMINI_API_KEY.
// Si Gemini llega a su límite o falla, la pregunta pasa sola a la IA gratuita de Cloudflare.
const GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
const GEMINI_DEFAULT_MODEL = "gemini-flash-latest";
const GEMINI_MODEL_PATTERN = /^gemini-[a-z0-9.-]{1,60}$/;
const GEMINI_KEY_PATTERN = /^(AIza[A-Za-z0-9_-]{30,60}|AQ\.[A-Za-z0-9._-]{20,300})$/;
// Groq (nivel gratuito, sin tarjeta) cuando el servidor tiene el secreto GROQ_API_KEY: segundo respaldo si Gemini falla.
const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const GROQ_DEFAULT_MODEL = "openai/gpt-oss-120b";
const GROQ_MODEL_PATTERN = /^[a-z0-9._/-]{3,80}$/;
const GROQ_KEY_PATTERN = /^gsk_[A-Za-z0-9]{20,120}$/;
const encoder = new TextEncoder();

class HttpFailure extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}

function json(body, status = 200, extra = {}) {
  return new Response(JSON.stringify(body), { status, headers: {
    "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff", ...extra,
  } });
}

export function provider(env) {
  if (OPENAI_KEY_PATTERN.test(env.OPENAI_API_KEY ?? "") && OPENAI_MODEL_PATTERN.test(env.OPENAI_MODEL ?? OPENAI_DEFAULT_MODEL)) return "openai";
  if (GEMINI_KEY_PATTERN.test((env.GEMINI_API_KEY ?? "").trim()) && GEMINI_MODEL_PATTERN.test(env.GEMINI_MODEL ?? GEMINI_DEFAULT_MODEL)) return "gemini";
  if (hasGroq(env)) return "groq";
  if (typeof env.AI?.run === "function" && MODEL_PATTERN.test(env.AI_MODEL ?? DEFAULT_MODEL)) return "workers-ai";
  return null;
}

function configured(env) {
  return provider(env) !== null && TOKEN_PATTERN.test(env.APP_TOKEN ?? "") &&
    typeof env.BUDGET?.idFromName === "function" && typeof env.BUDGET?.get === "function";
}

async function authorized(request, secret) {
  const header = request.headers.get("Authorization") ?? "";
  const supplied = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!TOKEN_PATTERN.test(supplied)) return false;
  // Compare fixed-size hashes without an early exit; do not log either token.
  const [a, b] = await Promise.all([supplied, secret].map(async value =>
    new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(value)))));
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
  return difference === 0;
}

async function boundedJson(body, maxBytes, timeoutMs = 5_000) {
  if (!body) throw new HttpFailure(400, "invalid_json", "Envía un objeto JSON.");
  const reader = body.getReader();
  const chunks = [];
  let length = 0;
  let timer;
  const timeout = new Promise((_, reject) => { timer = setTimeout(() => {
    void reader.cancel().catch(() => {});
    reject(new HttpFailure(408, "body_timeout", "La lectura tardó demasiado. Intenta de nuevo."));
  }, timeoutMs); });
  try {
    while (true) {
      const next = await Promise.race([reader.read(), timeout]);
      if (next.done) break;
      length += next.value.byteLength;
      if (length > maxBytes) {
        void reader.cancel().catch(() => {});
        throw new HttpFailure(413, "too_large", "El mensaje es demasiado largo.");
      }
      chunks.push(next.value);
    }
    const bytes = new Uint8Array(length);
    let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
    try { return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes)); }
    catch { throw new HttpFailure(400, "invalid_json", "El mensaje no tiene un formato válido."); }
  } finally { clearTimeout(timer); reader.releaseLock(); }
}

function validateInput(value) {
  if (!value || typeof value !== "object" || Array.isArray(value) ||
    Object.keys(value).some(k => !["message", "zoneId", "now"].includes(k)) ||
    typeof value.message !== "string" || !value.message.trim() || value.message.length > 4000 ||
    typeof value.zoneId !== "string" || value.zoneId.length > 80 ||
    typeof value.now !== "string" || value.now.length > 40 ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.test(value.now) ||
    !Number.isFinite(Date.parse(value.now))) {
    throw new HttpFailure(400, "invalid_request", "Envía solo message, zoneId y now; máximo 4000 caracteres.");
  }
  try { new Intl.DateTimeFormat("es-PE", { timeZone: value.zoneId }).format(0); }
  catch { throw new HttpFailure(400, "invalid_zone", "Selecciona una zona horaria válida."); }
  return { message: value.message.trim(), zoneId: value.zoneId, now: value.now };
}

export function validateOutput(value) {
  if (!value || typeof value !== "object" || Array.isArray(value) ||
    Object.keys(value).some(k => !["kind", "text"].includes(k)) ||
    !["answer", "command", "clarify"].includes(value.kind) ||
    typeof value.text !== "string" || !value.text.trim() || value.text.length > 4000 ||
    (value.kind === "command" && /[\u0000-\u001f\u007f-\u009f\u2028\u2029]/.test(value.text))) {
    throw new HttpFailure(502, "invalid_response", "La IA devolvió una respuesta no válida. No se aplicó ningún cambio.");
  }
  // This is transport validation only. Android's local parser validates every command.
  return { kind: value.kind, text: value.text.trim() };
}

function parseModelJson(raw) {
  if (raw && typeof raw === "object" && !Array.isArray(raw)) return raw;
  if (typeof raw !== "string") return null;
  const text = raw.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
  try { return JSON.parse(text); } catch { /* try the first JSON object inside the text */ }
  const first = text.indexOf("{");
  const last = text.lastIndexOf("}");
  if (first < 0 || last <= first) return null;
  try { return JSON.parse(text.slice(first, last + 1)); } catch { return null; }
}

export function extractModelOutput(result) {
  // Workers AI returns { response } for chat models; keep other documented shapes as fallbacks.
  if (result == null) return null;
  if (typeof result === "string") return parseModelJson(result);
  if (result.response !== undefined) return parseModelJson(result.response);
  const choice = result.choices?.[0]?.message?.content;
  if (choice !== undefined) return parseModelJson(choice);
  if (typeof result.output_text === "string") return parseModelJson(result.output_text);
  if (Array.isArray(result.output)) {
    const text = result.output.filter(o => o?.type === "message").flatMap(o => o.content ?? [])
      .filter(c => typeof c?.text === "string").map(c => c.text).join("");
    return parseModelJson(text);
  }
  return null;
}

async function askWorkersAi(input, env) {
  const model = env.AI_MODEL ?? DEFAULT_MODEL;
  let timer;
  const timeout = new Promise((_, reject) => { timer = setTimeout(() =>
    reject(new HttpFailure(504, "provider_unavailable", "La IA no respondió a tiempo. Puedes seguir usando el modo local.")), 20_000); });
  try {
    const result = await Promise.race([env.AI.run(model, {
      messages: [
        { role: "system", content: SYSTEM_PROMPT },
        { role: "user", content: JSON.stringify(input) },
      ],
      response_format: { type: "json_schema", json_schema: RESPONSE_SCHEMA },
      max_tokens: 400,
      temperature: 0.2,
    }), timeout]);
    const parsed = extractModelOutput(result);
    if (!parsed) throw new HttpFailure(502, "invalid_response", "La IA no devolvió un resultado válido. No se aplicó ningún cambio.");
    return validateOutput(parsed);
  } catch (error) {
    if (error instanceof HttpFailure) throw error;
    const message = String(error?.message ?? "");
    if (/429|limit|quota|neuron|capacity/i.test(message)) {
      throw new HttpFailure(429, "provider_quota", "La IA gratuita llegó a su límite de hoy. Sigue usando los comandos locales.");
    }
    throw new HttpFailure(502, "provider_unavailable", "No se pudo obtener una respuesta de la IA. Los datos locales se conservan.");
  } finally { clearTimeout(timer); }
}

function openAiBody(input, env, withReasoning) {
  const effort = EFFORTS.includes(env.OPENAI_REASONING) ? env.OPENAI_REASONING : "minimal";
  const body = {
    model: env.OPENAI_MODEL ?? OPENAI_DEFAULT_MODEL,
    instructions: SYSTEM_PROMPT,
    input: JSON.stringify(input),
    text: { verbosity: "low", format: { type: "json_schema", name: "respuesta_mascota", strict: true,
      schema: { ...RESPONSE_SCHEMA, additionalProperties: false } } },
    max_output_tokens: 900,
    store: false,
  };
  if (withReasoning) body.reasoning = { effort };
  return body;
}

async function askOpenAi(input, env, fetcher = fetch) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 25_000);
  try {
    let withReasoning = true;
    for (let attempt = 0; attempt < 2; attempt++) {
      let response;
      try {
        response = await fetcher(OPENAI_URL, { method: "POST", signal: controller.signal, headers: {
          Authorization: `Bearer ${env.OPENAI_API_KEY}`, "Content-Type": "application/json" },
          body: JSON.stringify(openAiBody(input, env, withReasoning)) });
      } catch (error) {
        if (error?.name === "AbortError") throw new HttpFailure(504, "provider_unavailable", "La IA no respondió a tiempo. Puedes seguir usando el modo local.");
        throw new HttpFailure(502, "provider_unavailable", "No se pudo conectar con la IA. Los datos locales se conservan.");
      }
      if (response.ok) {
        const parsed = extractModelOutput(await response.json());
        if (!parsed) throw new HttpFailure(502, "invalid_response", "La IA no devolvió un resultado válido. No se aplicó ningún cambio.");
        return validateOutput(parsed);
      }
      const detail = await response.text().catch(() => "");
      // Algunos modelos no aceptan el nivel de razonamiento elegido: se reintenta una vez sin ese parámetro.
      if (response.status === 400 && withReasoning && /reasoning|effort/i.test(detail)) { withReasoning = false; continue; }
      if (response.status === 429) throw new HttpFailure(429, "provider_quota", "La IA llegó a su límite por ahora. Sigue usando los comandos locales.");
      if (response.status === 401 || response.status === 403) throw new HttpFailure(503, "provider_key", "La clave de OpenAI del servidor no es válida. Vuelve a ponerla con PONER_CLAVE_OPENAI.bat.");
      throw new HttpFailure(502, "provider_unavailable", "No se pudo obtener una respuesta de la IA. Los datos locales se conservan.");
    }
    throw new HttpFailure(502, "provider_unavailable", "No se pudo obtener una respuesta de la IA. Los datos locales se conservan.");
  } finally { clearTimeout(timer); }
}

function geminiBody(input, withThinkingOff) {
  const body = {
    systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
    contents: [{ role: "user", parts: [{ text: JSON.stringify(input) }] }],
    generationConfig: {
      responseMimeType: "application/json",
      responseSchema: { type: "OBJECT", properties: { kind: { type: "STRING", enum: ["answer", "command", "clarify"] }, text: { type: "STRING" } },
        required: ["kind", "text"] },
      temperature: 0.2,
      maxOutputTokens: 1024,
    },
  };
  if (withThinkingOff) body.generationConfig.thinkingConfig = { thinkingBudget: 0 };
  return body;
}

export function extractGeminiOutput(result) {
  const parts = result?.candidates?.[0]?.content?.parts;
  if (!Array.isArray(parts)) return null;
  return parseModelJson(parts.filter(p => typeof p?.text === "string" && !p.thought).map(p => p.text).join(""));
}

async function askGemini(input, env, fetcher = fetch) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 20_000);
  const model = env.GEMINI_MODEL ?? GEMINI_DEFAULT_MODEL;
  try {
    let thinkingOff = true;
    for (let attempt = 0; attempt < 2; attempt++) {
      let response;
      try {
        response = await fetcher(`${GEMINI_BASE}${model}:generateContent`, { method: "POST", signal: controller.signal, headers: {
          "x-goog-api-key": env.GEMINI_API_KEY.trim(), "Content-Type": "application/json" },
          body: JSON.stringify(geminiBody(input, thinkingOff)) });
      } catch (error) {
        if (error?.name === "AbortError") throw new HttpFailure(504, "provider_unavailable", "La IA no respondió a tiempo. Puedes seguir usando el modo local.");
        throw new HttpFailure(502, "provider_unavailable", "No se pudo conectar con la IA. Los datos locales se conservan.");
      }
      if (response.ok) {
        const parsed = extractGeminiOutput(await response.json());
        if (!parsed) throw new HttpFailure(502, "invalid_response", "La IA no devolvió un resultado válido. No se aplicó ningún cambio.");
        return validateOutput(parsed);
      }
      const detail = await response.text().catch(() => "");
      // Algunos modelos no aceptan apagar el razonamiento: se reintenta una vez sin ese ajuste.
      if (response.status === 400 && thinkingOff && /thinking/i.test(detail)) { thinkingOff = false; continue; }
      if (response.status === 429) throw new HttpFailure(429, "provider_quota", "La IA llegó a su límite por ahora. Sigue usando los comandos locales.");
      if (response.status === 401 || response.status === 403) throw new HttpFailure(503, "provider_key", "La clave de Gemini del servidor no es válida. Vuelve a ponerla con PONER_CLAVE_GEMINI.bat.");
      throw new HttpFailure(502, "provider_unavailable", "No se pudo obtener una respuesta de la IA. Los datos locales se conservan.");
    }
    throw new HttpFailure(502, "provider_unavailable", "No se pudo obtener una respuesta de la IA. Los datos locales se conservan.");
  } finally { clearTimeout(timer); }
}

function hasGroq(env) {
  return GROQ_KEY_PATTERN.test((env.GROQ_API_KEY ?? "").trim()) && GROQ_MODEL_PATTERN.test(env.GROQ_MODEL ?? GROQ_DEFAULT_MODEL);
}

async function askGroq(input, env, fetcher = fetch) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 20_000);
  try {
    let response;
    try {
      response = await fetcher(GROQ_URL, { method: "POST", signal: controller.signal, headers: {
        Authorization: `Bearer ${env.GROQ_API_KEY.trim()}`, "Content-Type": "application/json" },
        body: JSON.stringify({ model: env.GROQ_MODEL ?? GROQ_DEFAULT_MODEL, temperature: 0.2, max_completion_tokens: 1024,
          response_format: { type: "json_object" },
          messages: [{ role: "system", content: SYSTEM_PROMPT }, { role: "user", content: JSON.stringify(input) }] }) });
    } catch (error) {
      if (error?.name === "AbortError") throw new HttpFailure(504, "provider_unavailable", "La IA no respondió a tiempo. Puedes seguir usando el modo local.");
      throw new HttpFailure(502, "provider_unavailable", "No se pudo conectar con la IA. Los datos locales se conservan.");
    }
    if (response.ok) {
      const parsed = extractModelOutput(await response.json());
      if (!parsed) throw new HttpFailure(502, "invalid_response", "La IA no devolvió un resultado válido. No se aplicó ningún cambio.");
      return validateOutput(parsed);
    }
    if (response.status === 429) throw new HttpFailure(429, "provider_quota", "La IA llegó a su límite por ahora. Sigue usando los comandos locales.");
    if (response.status === 401 || response.status === 403) throw new HttpFailure(503, "provider_key", "La clave de Groq del servidor no es válida. Vuelve a ponerla con PONER_CLAVE_GROQ.bat.");
    throw new HttpFailure(502, "provider_unavailable", "No se pudo obtener una respuesta de la IA. Los datos locales se conservan.");
  } finally { clearTimeout(timer); }
}

/**
 * Cadena de IA gratuita: Gemini → Groq → Cloudflare. Si una falla (límite, clave o caída), contesta la siguiente que
 * esté configurada. OpenAI, si tiene clave, va solo.
 */
async function askProvider(input, env) {
  const chosen = provider(env);
  const fetcher = env.FETCH ?? fetch;
  if (chosen === "openai") return askOpenAi(input, env, fetcher);
  const chain = [];
  if (chosen === "gemini") chain.push(() => askGemini(input, env, fetcher));
  if (hasGroq(env)) chain.push(() => askGroq(input, env, fetcher));
  if (typeof env.AI?.run === "function" && MODEL_PATTERN.test(env.AI_MODEL ?? DEFAULT_MODEL)) chain.push(() => askWorkersAi(input, env));
  let last = new HttpFailure(503, "not_configured", "El servidor todavía no está configurado.");
  for (const step of chain) {
    try { return await step(); } catch (error) { last = error; }
  }
  throw last;
}

export async function handleRequest(request, env) {
  try {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") return json({ configured: configured(env), provider: configured(env) ? provider(env) : null });
    if (CUENTA_RUTAS.has(url.pathname)) return await handleAccount(request, env, url);
    if (!["/check", "/chat"].includes(url.pathname)) return json({ error: "not_found" }, 404);
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405, { Allow: "POST" });
    if (!configured(env)) return json({ error: "not_configured", message: "El servidor todavía no está configurado." }, 503);
    if (!await authorized(request, env.APP_TOKEN)) return json({ error: "unauthorized", message: "Revisa el token de acceso al servidor." }, 401);
    if (url.pathname === "/check") return json({ configured: true });
    if (!(request.headers.get("Content-Type") ?? "").toLowerCase().startsWith("application/json")) {
      return json({ error: "content_type", message: "Utiliza application/json." }, 415);
    }
    if (Number(request.headers.get("Content-Length")) > INPUT_BYTES) return json({ error: "too_large" }, 413);
    const input = validateInput(await boundedJson(request.body, INPUT_BYTES));
    // One durable counter for this installation, including after token rotation or isolate restarts.
    const budget = env.BUDGET.get(env.BUDGET.idFromName("ia-budget-v1"));
    const allowance = await budget.fetch("https://budget.internal/consume", { method: "POST" });
    if (!allowance.ok) {
      if (allowance.status === 429) return json({ error: "budget_exceeded", message: "Se alcanzó el límite de consultas. Usa el modo local o inténtalo más tarde." }, 429,
        { "Retry-After": allowance.headers.get("Retry-After") ?? "60" });
      return json({ error: "budget_unavailable", message: "No se pudo comprobar el límite de uso. Intenta de nuevo." }, 503);
    }
    // Failed provider requests also consume a slot, preventing retry loops from creating unbounded cost.
    return json(await askProvider(input, env));
  } catch (error) {
    if (error instanceof HttpFailure) return json({ error: error.code, message: error.message }, error.status);
    return json({ error: "unavailable", message: "El servicio no está disponible. No se aplicó ningún cambio." }, 503);
  }
}

// ---- Cuentas y copia en la nube (ver cuentas.mjs). Usan el mismo token de la app que /chat.
const CUENTA_RUTAS = new Set(["/cuenta/registro", "/cuenta/entrar", "/cuenta/salir", "/cuenta/yo", "/copia"]);
const CUENTA_METODOS = { "/cuenta/registro": ["POST"], "/cuenta/entrar": ["POST"], "/cuenta/salir": ["POST"], "/cuenta/yo": ["GET"], "/copia": ["GET", "PUT"] };

export async function handleAccount(request, env, url) {
  const allowed = CUENTA_METODOS[url.pathname];
  if (!allowed.includes(request.method)) return json({ error: "method_not_allowed" }, 405, { Allow: allowed.join(", ") });
  if (!TOKEN_PATTERN.test(env.APP_TOKEN ?? "") || typeof env.CUENTAS?.idFromName !== "function") {
    return json({ error: "not_configured", message: "Las cuentas todavía no están activas en el servidor." }, 503);
  }
  if (!await authorized(request, env.APP_TOKEN)) return json({ error: "unauthorized", message: "Revisa el token de acceso al servidor." }, 401);
  let body = null;
  if (request.method !== "GET") {
    if (!(request.headers.get("Content-Type") ?? "").toLowerCase().startsWith("application/json")) {
      return json({ error: "content_type", message: "Utiliza application/json." }, 415);
    }
    const max = url.pathname === "/copia" ? COPIA_MAX + 1_000 : 4_000;
    if (Number(request.headers.get("Content-Length")) > max) return json({ error: "too_large", message: "La copia es demasiado grande." }, 413);
    body = await boundedJson(request.body, max, url.pathname === "/copia" ? 30_000 : 5_000);
  }
  if (url.pathname === "/cuenta/registro" || url.pathname === "/cuenta/entrar") {
    const registro = url.pathname === "/cuenta/registro";
    const valid = validarAcceso(body, registro);
    if (valid.error) return json({ error: "invalid_input", message: valid.error }, 400);
    if (registro) {
      // Tope de cuentas nuevas por día y por minuto para todo el servidor.
      const budget = env.BUDGET.get(env.BUDGET.idFromName("registros-v1"));
      const allowance = await budget.fetch("https://budget.internal/consume-registro", { method: "POST" });
      if (!allowance.ok) return json({ error: "budget_exceeded", message: "Se crearon muchas cuentas hoy. Intenta más tarde." }, 429,
        { "Retry-After": allowance.headers.get("Retry-After") ?? "60" });
    }
    const stub = env.CUENTAS.get(env.CUENTAS.idFromName(`cuenta:${valid.correo}`));
    return await stub.fetch(`https://cuenta.internal/${registro ? "registro" : "entrar"}`, { method: "POST",
      headers: { "Content-Type": "application/json" }, body: JSON.stringify(valid) });
  }
  const sesion = leerSesion(request);
  if (!sesion) return json({ error: "sesion", message: "Tu sesión terminó. Vuelve a entrar." }, 401);
  let id;
  try { id = env.CUENTAS.idFromString(sesion.id); } catch { return json({ error: "sesion", message: "Tu sesión terminó. Vuelve a entrar." }, 401); }
  const stub = env.CUENTAS.get(id);
  const target = url.pathname === "/copia" ? "copia" : url.pathname.slice("/cuenta/".length);
  if (url.pathname === "/copia" && request.method === "PUT") {
    const valid = validarCopia(body);
    if (valid.error) return json({ error: "invalid_input", message: valid.error }, 400);
    body = valid;
  }
  return await stub.fetch(`https://cuenta.internal/${target}`, { method: request.method,
    headers: { "X-Secreto": sesion.secreto, "Content-Type": "application/json" },
    body: body === null ? undefined : JSON.stringify(body) });
}

function limit(raw, fallback, maximum) {
  const number = Number(raw);
  return Number.isInteger(number) && number > 0 ? Math.min(number, maximum) : fallback;
}

export class RequestBudget {
  constructor(state, env) { this.state = state; this.env = env; }
  async fetch(request) {
    const path = new URL(request.url).pathname;
    if (request.method !== "POST" || !["/consume", "/consume-registro"].includes(path)) return json({ error: "not_found" }, 404);
    const now = Date.now();
    const day = Math.floor(now / 86_400_000);
    const minute = Math.floor(now / 60_000);
    // Cuentas nuevas: hasta 200 por día y 10 por minuto en todo el servidor.
    const dailyLimit = path === "/consume-registro" ? 200 : limit(this.env.DAILY_LIMIT, 30, 2000);
    const minuteLimit = path === "/consume-registro" ? 10 : limit(this.env.MINUTE_LIMIT, 5, 30);
    const result = await this.state.storage.transaction(async txn => {
      const stored = await txn.get("budget");
      const record = { day, minute, dayCount: stored?.day === day ? stored.dayCount : 0,
        minuteCount: stored?.minute === minute ? stored.minuteCount : 0 };
      if (!Number.isInteger(record.dayCount) || record.dayCount < 0 || !Number.isInteger(record.minuteCount) || record.minuteCount < 0) {
        return { status: 503, retry: 60 }; // Corrupted counters fail closed.
      }
      if (record.dayCount >= dailyLimit) return { status: 429, retry: Math.ceil(((day + 1) * 86_400_000 - now) / 1000) };
      if (record.minuteCount >= minuteLimit) return { status: 429, retry: Math.ceil(((minute + 1) * 60_000 - now) / 1000) };
      record.dayCount++; record.minuteCount++;
      await txn.put("budget", record);
      return { status: 200 };
    });
    return json({ allowed: result.status === 200 }, result.status,
      result.retry ? { "Retry-After": String(Math.max(1, result.retry)) } : {});
  }
}

export default { fetch(request, env) { return handleRequest(request, env); } };
