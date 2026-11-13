// Cuentas y copia en la nube de StudyPlanner AI Pet.
//
// Cada cuenta es un Durable Object (con SQLite) identificado por su correo. La contraseña nunca viaja ni se guarda:
// el teléfono la convierte con PBKDF2-SHA256 (100 000 vueltas, sal = correo) y envía solo esa clave derivada; aquí se
// guarda un SHA-256 de la clave con una sal aleatoria propia. Las sesiones son «<id de la cuenta>.<secreto>»; del
// secreto solo se guarda su hash. La copia es un archivo comprimido con los datos de la app, en trozos de 1 MB.

const CORREO = /^[^\s@]{1,64}@[^\s@]{1,63}\.[^\s@]{2,24}$/;
const CLAVE = /^[A-Za-z0-9_-]{43}$/;
const SECRETO = /^[A-Za-z0-9_-]{43}$/;
const ID = /^[0-9a-f]{64}$/;
const BASE64 = /^[A-Za-z0-9+/]*={0,2}$/;
const HASH = /^[0-9a-f]{64}$/;
export const COPIA_MAX = 6_000_000;   // caracteres en base64 (unos 4,5 MB de datos comprimidos)
const TROZO = 1_000_000;
const SESIONES_MAX = 10;
const FALLOS_MAX = 5;
const BLOQUEO_MS = 5 * 60_000;
const encoder = new TextEncoder();

function json(body, status = 200, extra = {}) {
  return new Response(JSON.stringify(body), { status, headers: {
    "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff", ...extra,
  } });
}
function b64url(bytes) {
  let s = ""; for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
function fromB64url(text) {
  const s = atob(text.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - text.length % 4) % 4));
  return Uint8Array.from(s, c => c.charCodeAt(0));
}
async function sha256Hex(...parts) {
  const total = parts.reduce((n, p) => n + p.length, 0);
  const all = new Uint8Array(total); let o = 0;
  for (const p of parts) { all.set(p, o); o += p.length; }
  return [...new Uint8Array(await crypto.subtle.digest("SHA-256", all))].map(b => b.toString(16).padStart(2, "0")).join("");
}
function sameHex(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let d = 0; for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}
export function normalizarCorreo(value) {
  const correo = typeof value === "string" ? value.trim().toLowerCase() : "";
  return CORREO.test(correo) && correo.length <= 120 ? correo : null;
}
function nombreValido(value) {
  const nombre = typeof value === "string" ? value.trim().replace(/\s+/g, " ") : "";
  return nombre.length >= 1 && nombre.length <= 40 && !/[\u0000-\u001f<>]/.test(nombre) ? nombre : null;
}

/** Valida lo que llega del teléfono antes de tocar una cuenta. */
export function validarAcceso(body, registro) {
  const correo = normalizarCorreo(body?.correo);
  if (!correo) return { error: "Escribe un correo válido." };
  if (typeof body?.clave !== "string" || !CLAVE.test(body.clave)) return { error: "La contraseña no llegó bien. Actualiza la app." };
  if (registro) {
    const nombre = nombreValido(body?.nombre);
    if (!nombre) return { error: "Escribe tu nombre (hasta 40 letras)." };
    return { correo, clave: body.clave, nombre };
  }
  return { correo, clave: body.clave };
}

export function validarCopia(body) {
  if (typeof body?.datos !== "string" || body.datos.length === 0 || body.datos.length > COPIA_MAX || !BASE64.test(body.datos)) {
    return { error: "La copia no tiene un formato válido o es demasiado grande." };
  }
  if (typeof body?.hash !== "string" || !HASH.test(body.hash)) return { error: "Falta el resumen de la copia." };
  return { datos: body.datos, hash: body.hash };
}

/** Separa «<id>.<secreto>» de la cabecera X-Sesion. */
export function leerSesion(request) {
  const raw = request.headers.get("X-Sesion") ?? "";
  const [id, secreto, extra] = raw.split(".");
  if (extra !== undefined || !ID.test(id ?? "") || !SECRETO.test(secreto ?? "")) return null;
  return { id, secreto };
}

/** Una cuenta: datos de acceso, sesiones abiertas y la copia de la app. */
export class Cuenta {
  constructor(state, env) { this.state = state; this.env = env; }

  async fetch(request) {
    const url = new URL(request.url);
    const body = request.method === "GET" ? null : await request.json().catch(() => null);
    const s = this.state.storage;
    switch (`${request.method} ${url.pathname}`) {
      case "POST /registro": {
        if (await s.get("acceso")) return json({ error: "existe", message: "Ese correo ya tiene una cuenta. Entra con tu contraseña." }, 409);
        const sal = crypto.getRandomValues(new Uint8Array(16));
        await s.put("acceso", { correo: body.correo, nombre: body.nombre, sal: b64url(sal),
          hash: await sha256Hex(sal, fromB64url(body.clave)), creado: Date.now(), fallos: 0, bloqueo: 0 });
        return json({ ...await this.nuevaSesion(), nombre: body.nombre, correo: body.correo, copia: null }, 201);
      }
      case "POST /entrar": {
        const acceso = await s.get("acceso");
        const ahora = Date.now();
        if (!acceso || acceso.correo !== body.correo) return json({ error: "datos", message: "Correo o contraseña incorrectos." }, 401);
        if (acceso.bloqueo > ahora) return json({ error: "espera", message: "Demasiados intentos. Espera unos minutos." }, 429,
          { "Retry-After": String(Math.ceil((acceso.bloqueo - ahora) / 1000)) });
        if (!sameHex(await sha256Hex(fromB64url(acceso.sal), fromB64url(body.clave)), acceso.hash)) {
          acceso.fallos = (acceso.fallos ?? 0) + 1;
          if (acceso.fallos >= FALLOS_MAX) { acceso.fallos = 0; acceso.bloqueo = ahora + BLOQUEO_MS; }
          await s.put("acceso", acceso);
          return json({ error: "datos", message: "Correo o contraseña incorrectos." }, 401);
        }
        if (acceso.fallos || acceso.bloqueo) { acceso.fallos = 0; acceso.bloqueo = 0; await s.put("acceso", acceso); }
        const meta = await s.get("copia");
        return json({ ...await this.nuevaSesion(), nombre: acceso.nombre, correo: acceso.correo,
          copia: meta ? { fecha: meta.fecha, bytes: meta.largo } : null });
      }
    }
    // Lo demás necesita una sesión abierta.
    const secreto = request.headers.get("X-Secreto") ?? "";
    const sesiones = (await s.get("sesiones")) ?? {};
    const clave = SECRETO.test(secreto) ? await sha256Hex(encoder.encode(secreto)) : "";
    if (!clave || !Object.keys(sesiones).some(k => sameHex(k, clave))) return json({ error: "sesion", message: "Tu sesión terminó. Vuelve a entrar." }, 401);
    switch (`${request.method} ${url.pathname}`) {
      case "GET /copia": {
        const meta = await s.get("copia");
        if (!meta) return json({ error: "sin_copia", message: "Todavía no hay una copia en la nube." }, 404);
        const trozos = await s.get(Array.from({ length: meta.trozos }, (_, i) => `copia:${i}`));
        let datos = "";
        for (let i = 0; i < meta.trozos; i++) datos += trozos.get(`copia:${i}`) ?? "";
        if (datos.length !== meta.largo) return json({ error: "copia_danada", message: "La copia está incompleta." }, 500);
        return json({ fecha: meta.fecha, hash: meta.hash, datos });
      }
      case "PUT /copia": {
        const antes = await s.get("copia");
        const trozos = Math.ceil(body.datos.length / TROZO);
        const fecha = Date.now();
        const entradas = {};
        for (let i = 0; i < trozos; i++) entradas[`copia:${i}`] = body.datos.slice(i * TROZO, (i + 1) * TROZO);
        await s.put(entradas);
        if (antes && antes.trozos > trozos) await s.delete(Array.from({ length: antes.trozos - trozos }, (_, i) => `copia:${trozos + i}`));
        await s.put("copia", { fecha, hash: body.hash, largo: body.datos.length, trozos });
        return json({ fecha });
      }
      case "POST /salir": {
        for (const k of Object.keys(sesiones)) if (sameHex(k, clave)) delete sesiones[k];
        await s.put("sesiones", sesiones);
        return json({ ok: true });
      }
      case "GET /yo": {
        const acceso = await s.get("acceso");
        const meta = await s.get("copia");
        return json({ nombre: acceso?.nombre, correo: acceso?.correo, copia: meta ? { fecha: meta.fecha, bytes: meta.largo } : null });
      }
    }
    return json({ error: "not_found" }, 404);
  }

  async nuevaSesion() {
    const secreto = b64url(crypto.getRandomValues(new Uint8Array(32)));
    const sesiones = (await this.state.storage.get("sesiones")) ?? {};
    sesiones[await sha256Hex(encoder.encode(secreto))] = Date.now();
    // Solo las 10 sesiones más recientes (teléfonos) quedan abiertas.
    const recientes = Object.entries(sesiones).sort((a, b) => b[1] - a[1]).slice(0, SESIONES_MAX);
    await this.state.storage.put("sesiones", Object.fromEntries(recientes));
    return { sesion: `${this.state.id.toString()}.${secreto}` };
  }
}
