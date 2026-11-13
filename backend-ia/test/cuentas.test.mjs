import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto, createHash, pbkdf2Sync } from "node:crypto";
import { handleRequest, RequestBudget, Cuenta } from "../src/worker.mjs";
globalThis.crypto ??= webcrypto;

const token = "a".repeat(64);
// Lo que hace el teléfono: PBKDF2-SHA256, 100 000 vueltas, sal = «studyplanner:<correo>».
const derivar = (correo, clave) => pbkdf2Sync(clave, `studyplanner:${correo}`, 100_000, 32, "sha256").toString("base64url");

function kv() {
  const data = new Map(); let tail = Promise.resolve();
  const api = {
    get: async key => Array.isArray(key) ? new Map(key.filter(k => data.has(k)).map(k => [k, structuredClone(data.get(k))])) : structuredClone(data.get(key)),
    put: async (key, value) => { if (typeof key === "object") for (const [k, v] of Object.entries(key)) data.set(k, structuredClone(v)); else data.set(key, structuredClone(value)); },
    delete: async key => { for (const k of [key].flat()) data.delete(k); },
  };
  return { ...api, data, transaction(fn) { const run = tail.then(() => fn(api)); tail = run.catch(() => {}); return run; } };
}
function namespace(Klass, env) {
  const objects = new Map();
  const make = hex => ({ toString: () => hex });
  return {
    objects,
    idFromName: name => make(createHash("sha256").update(name).digest("hex")),
    idFromString: hex => { if (!/^[0-9a-f]{64}$/.test(hex)) throw new TypeError("id inválido"); return make(hex); },
    get(id) {
      const hex = id.toString();
      if (!objects.has(hex)) objects.set(hex, new Klass({ id, storage: kv() }, env));
      const o = objects.get(hex);
      return { fetch: (url, options) => o.fetch(new Request(url, options)) };
    },
  };
}
function setup() {
  const env = { APP_TOKEN: token, AI: { run: async () => ({ response: { kind: "answer", text: "Hola" } }) } };
  env.BUDGET = namespace(RequestBudget, env);
  env.CUENTAS = namespace(Cuenta, env);
  return env;
}
function call(env, method, path, body, sesion, secret = token) {
  const headers = { Authorization: `Bearer ${secret}` };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (sesion) headers["X-Sesion"] = sesion;
  return handleRequest(new Request(`https://example.test${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) }), env);
}
const ana = { correo: "Ana@Ejemplo.pe", nombre: "Ana", clave: derivar("ana@ejemplo.pe", "estudio-2026") };

test("crear cuenta, entrar en otro teléfono, guardar y recuperar la copia", async () => {
  const env = setup();
  const r1 = await call(env, "POST", "/cuenta/registro", ana);
  assert.equal(r1.status, 201);
  const a = await r1.json();
  assert.equal(a.correo, "ana@ejemplo.pe");
  assert.match(a.sesion, /^[0-9a-f]{64}\.[A-Za-z0-9_-]{43}$/);
  // El mismo correo no se puede registrar dos veces.
  assert.equal((await call(env, "POST", "/cuenta/registro", ana)).status, 409);
  // Sin copia todavía.
  assert.equal((await call(env, "GET", "/copia", undefined, a.sesion)).status, 404);
  const datos = Buffer.from("x".repeat(2_500_000)).toString("base64");
  const hash = createHash("sha256").update(datos).digest("hex");
  const put = await call(env, "PUT", "/copia", { datos, hash }, a.sesion);
  assert.equal(put.status, 200);
  // Otro teléfono: entra con la misma contraseña y ve que hay copia.
  const r2 = await call(env, "POST", "/cuenta/entrar", { correo: "ana@ejemplo.pe", clave: ana.clave });
  assert.equal(r2.status, 200);
  const b = await r2.json();
  assert.equal(b.nombre, "Ana");
  assert.equal(b.copia.bytes, datos.length);
  const got = await (await call(env, "GET", "/copia", undefined, b.sesion)).json();
  assert.equal(got.datos, datos);
  assert.equal(got.hash, hash);
  // Una copia más chica borra los trozos que sobran.
  await call(env, "PUT", "/copia", { datos: "aGVsbG8=", hash }, b.sesion);
  assert.equal((await (await call(env, "GET", "/copia", undefined, a.sesion)).json()).datos, "aGVsbG8=");
  // La contraseña no se guarda: solo un hash con sal propia.
  const guardado = [...env.CUENTAS.objects.values()][0].state.storage.data;
  assert.ok(!JSON.stringify([...guardado.get("acceso") ? [guardado.get("acceso")] : []]).includes(ana.clave));
});

test("contraseña equivocada, bloqueo tras cinco intentos y cierre de sesión", async () => {
  const env = setup();
  const a = await (await call(env, "POST", "/cuenta/registro", ana)).json();
  const mala = { correo: "ana@ejemplo.pe", clave: derivar("ana@ejemplo.pe", "otra") };
  for (let i = 0; i < 4; i++) assert.equal((await call(env, "POST", "/cuenta/entrar", mala)).status, 401);
  assert.equal((await call(env, "POST", "/cuenta/entrar", mala)).status, 401);
  // Quinto fallo: bloqueada unos minutos, incluso con la contraseña correcta.
  const bloqueada = await call(env, "POST", "/cuenta/entrar", { correo: "ana@ejemplo.pe", clave: ana.clave });
  assert.equal(bloqueada.status, 429);
  assert.ok(Number(bloqueada.headers.get("Retry-After")) > 0);
  // Un correo sin cuenta responde igual que una contraseña equivocada.
  const nadie = await call(env, "POST", "/cuenta/entrar", { correo: "nadie@ejemplo.pe", clave: ana.clave });
  assert.equal(nadie.status, 401);
  assert.equal((await nadie.json()).message, "Correo o contraseña incorrectos.");
  // Cerrar sesión invalida ese token.
  assert.equal((await call(env, "POST", "/cuenta/salir", {}, a.sesion)).status, 200);
  assert.equal((await call(env, "GET", "/copia", undefined, a.sesion)).status, 401);
});

test("se rechazan tokens, sesiones y datos inválidos sin tocar cuentas", async () => {
  const env = setup();
  assert.equal((await call(env, "POST", "/cuenta/registro", ana, undefined, "b".repeat(64))).status, 401);
  assert.equal((await call(env, "POST", "/cuenta/registro", { ...ana, correo: "sin-arroba" })).status, 400);
  assert.equal((await call(env, "POST", "/cuenta/registro", { ...ana, clave: "corta" })).status, 400);
  assert.equal((await call(env, "POST", "/cuenta/registro", { ...ana, nombre: "" })).status, 400);
  assert.equal((await call(env, "GET", "/cuenta/registro")).status, 405);
  assert.equal((await call(env, "GET", "/copia", undefined, "no-es-sesion")).status, 401);
  assert.equal((await call(env, "GET", "/copia", undefined, `${"0".repeat(64)}.${"A".repeat(43)}`)).status, 401);
  const a = await (await call(env, "POST", "/cuenta/registro", ana)).json();
  const [id] = a.sesion.split(".");
  assert.equal((await call(env, "GET", "/copia", undefined, `${id}.${"B".repeat(43)}`)).status, 401);
  assert.equal((await call(env, "PUT", "/copia", { datos: "@@@", hash: "0".repeat(64) }, a.sesion)).status, 400);
  assert.equal((await call(env, "PUT", "/copia", { datos: "aGVsbG8=", hash: "x" }, a.sesion)).status, 400);
  // Sin el enlace de cuentas el servidor responde que no está activo.
  const sin = { ...env, CUENTAS: undefined };
  assert.equal((await call(sin, "POST", "/cuenta/entrar", ana)).status, 503);
});

test("el tope de cuentas nuevas por minuto se aplica", async () => {
  const env = setup();
  let creadas = 0, frenadas = 0;
  for (let i = 0; i < 12; i++) {
    const correo = `alumno${i}@ejemplo.pe`;
    const r = await call(env, "POST", "/cuenta/registro", { correo, nombre: `A${i}`, clave: derivar(correo, "clave-segura") });
    if (r.status === 201) creadas++; else if (r.status === 429) frenadas++;
  }
  assert.equal(creadas, 10);
  assert.equal(frenadas, 2);
});
