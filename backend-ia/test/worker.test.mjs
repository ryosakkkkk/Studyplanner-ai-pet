import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import worker, { handleRequest, RequestBudget, validateOutput, extractModelOutput } from "../src/worker.mjs";
import { SYSTEM_PROMPT } from "../src/prompt.mjs";
globalThis.crypto ??= webcrypto;

const token = "a".repeat(64);
const input = { message: "Hola", zoneId: "America/Lima", now: "2026-10-26T18:00:00Z" };
function request(path = "/chat", body = input, secret = token) {
  return new Request(`https://example.test${path}`, { method: "POST",
    headers: { Authorization: `Bearer ${secret}`, "Content-Type": "application/json" }, body: JSON.stringify(body) });
}
function storage() {
  const data = new Map(); let tail = Promise.resolve();
  const txn = { get: async key => structuredClone(data.get(key)), put: async (key, value) => data.set(key, structuredClone(value)) };
  return { transaction(fn) { const run = tail.then(() => fn(txn)); tail = run.catch(() => {}); return run; } };
}
function setup(vars = {}, run = async () => ({ response: { kind: "answer", text: "Hola, ¿qué quieres estudiar?" } })) {
  let aiCalls = 0;
  const seen = [];
  const env = { APP_TOKEN: token, AI: { run: async (model, options) => { aiCalls++; seen.push({ model, options }); return run(model, options); } }, ...vars };
  const state = { storage: storage() };
  const counter = new RequestBudget(state, env);
  let calls = 0;
  env.BUDGET = { idFromName: value => value, get: () => ({ fetch: (url, options) => { calls++; return counter.fetch(new Request(url, options)); } }) };
  return { env, state, counter, budgetCalls: () => calls, aiCalls: () => aiCalls, seen };
}

test("health exposes only configuration and check authenticates without AI or quota", async () => {
  const { env, budgetCalls, aiCalls } = setup();
  assert.deepEqual(await (await handleRequest(new Request("https://example.test/health"), env)).json(), { configured: true, provider: "workers-ai" });
  assert.equal((await handleRequest(request("/check"), env)).status, 200);
  assert.equal((await handleRequest(request("/check", {}, "b".repeat(64)), env)).status, 401);
  assert.equal((await handleRequest(request(), {})).status, 503);
  assert.equal(budgetCalls(), 0);
  assert.equal(aiCalls(), 0);
});

test("token rejection, unknown routes and malformed input never consume budget", async () => {
  const { env, budgetCalls } = setup();
  assert.equal((await handleRequest(request("/chat", input, "wrong"), env)).status, 401);
  assert.equal((await handleRequest(request("/unknown"), env)).status, 404);
  assert.equal((await handleRequest(request("/chat", { ...input, history: [] }), env)).status, 400);
  assert.equal((await handleRequest(request("/chat", { ...input, message: "x".repeat(4001) }), env)).status, 400);
  assert.equal((await handleRequest(request("/chat", { ...input, zoneId: "not-a-zone" }), env)).status, 400);
  assert.equal(budgetCalls(), 0);
});

test("stream size limit works without Content-Length", async () => {
  const { env, budgetCalls } = setup();
  const stream = new ReadableStream({ start(controller) { controller.enqueue(new Uint8Array(20_001)); controller.close(); } });
  const req = new Request("https://example.test/chat", { method: "POST", duplex: "half", body: stream,
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" } });
  assert.equal((await handleRequest(req, env)).status, 413);
  assert.equal(budgetCalls(), 0);
});

test("the model receives only the agreed request data with JSON mode and returns a validated command", async () => {
  const { env, seen } = setup({}, async () => ({ response: { kind: "command", text: 'crea una tarea "Lectura" para mañana' } }));
  const response = await handleRequest(request(), env);
  assert.equal(response.status, 200);
  assert.equal(seen[0].model, "@cf/meta/llama-3.3-70b-instruct-fp8-fast");
  assert.equal(seen[0].options.messages[0].content, SYSTEM_PROMPT);
  assert.deepEqual(JSON.parse(seen[0].options.messages[1].content), input);
  assert.equal(seen[0].options.response_format.type, "json_schema");
  assert.equal(seen[0].options.max_tokens, 400);
  assert.deepEqual(await response.json(), { kind: "command", text: 'crea una tarea "Lectura" para mañana' });
});

test("string, fenced and chat-completion outputs are understood", () => {
  assert.deepEqual(extractModelOutput({ response: '{"kind":"answer","text":"Hola"}' }), { kind: "answer", text: "Hola" });
  assert.deepEqual(extractModelOutput({ response: '```json\n{"kind":"answer","text":"Hola"}\n```' }), { kind: "answer", text: "Hola" });
  assert.deepEqual(extractModelOutput({ response: 'Claro: {"kind":"clarify","text":"¿Para qué día?"}' }), { kind: "clarify", text: "¿Para qué día?" });
  assert.deepEqual(extractModelOutput({ choices: [{ message: { content: '{"kind":"answer","text":"Hola"}' } }] }), { kind: "answer", text: "Hola" });
  assert.deepEqual(extractModelOutput({ output: [{ type: "reasoning" }, { type: "message", content: [{ type: "output_text", text: '{"kind":"answer","text":"Hola"}' }] }] }), { kind: "answer", text: "Hola" });
  assert.equal(extractModelOutput({ response: "sin json" }), null);
  assert.equal(extractModelOutput(null), null);
});

test("provider failures fail closed while consuming a request", async () => {
  const quota = setup({}, async () => { throw new Error("4006: you have used up your daily free allocation of 10,000 neurons"); });
  assert.equal((await handleRequest(request(), quota.env)).status, 429);
  const broken = setup({}, async () => { throw new Error("internal error"); });
  assert.equal((await handleRequest(request(), broken.env)).status, 502);
  const invalid = setup({}, async () => ({ response: "no es json" }));
  const res = await handleRequest(request(), invalid.env);
  assert.equal(res.status, 502);
  assert.match((await res.json()).message, /No se aplicó ningún cambio/);
  assert.equal(quota.budgetCalls() + broken.budgetCalls() + invalid.budgetCalls(), 3);
});

test("response schema rejects unexpected fields and multi-line commands", async () => {
  const extra = setup({}, async () => ({ response: { kind: "answer", text: "x", tool: "delete" } }));
  assert.equal((await handleRequest(request(), extra.env)).status, 502);
  assert.throws(() => validateOutput({ kind: "command", text: "borra todo\notra orden" }));
  assert.throws(() => validateOutput({ kind: "execute", text: "x" }));
  assert.throws(() => validateOutput({ kind: "answer", text: "x", tool: "delete" }));
});

test("durable daily budget is atomic and survives object recreation", async () => {
  const { env, state, counter } = setup({ DAILY_LIMIT: "3", MINUTE_LIMIT: "10" });
  const results = await Promise.all(Array.from({ length: 8 }, () => counter.fetch(new Request("https://budget.internal/consume", { method: "POST" }))));
  assert.equal(results.filter(r => r.status === 200).length, 3);
  assert.equal(results.filter(r => r.status === 429).length, 5);
  const restored = new RequestBudget(state, { ...env, APP_TOKEN: "b".repeat(64) });
  const result = await restored.fetch(new Request("https://budget.internal/consume", { method: "POST" }));
  assert.equal(result.status, 429);
  assert.ok(Number(result.headers.get("Retry-After")) > 0);
});

test("minute limit blocks the model before the daily limit and configurable caps are bounded", async () => {
  const { env, aiCalls } = setup({ DAILY_LIMIT: "30", MINUTE_LIMIT: "1" });
  assert.equal((await handleRequest(request(), env)).status, 200);
  assert.equal((await handleRequest(request(), env)).status, 429);
  assert.equal(aiCalls(), 1);
  const { counter } = setup({ DAILY_LIMIT: "999999", MINUTE_LIMIT: "999999" });
  const statuses = [];
  for (let i = 0; i < 31; i++) statuses.push((await counter.fetch(new Request("https://budget.internal/consume", { method: "POST" }))).status);
  assert.equal(statuses.filter(s => s === 200).length, 30);
});

test("an invalid model name leaves the server unconfigured", async () => {
  const { env } = setup({ AI_MODEL: "gpt-4o" });
  assert.equal((await handleRequest(request(), env)).status, 503);
});

test("Cloudflare default fetch signature works with the execution context", async () => {
  const { env } = setup();
  assert.equal((await worker.fetch(request(), env, { waitUntil() {} })).status, 200);
});

const openAiKey = "sk-proj-" + "x".repeat(40);
function openAiSetup(replies, vars = {}) {
  const calls = [];
  const fetcher = async (url, options) => {
    calls.push({ url, body: JSON.parse(options.body), auth: options.headers.Authorization });
    const next = replies[Math.min(calls.length - 1, replies.length - 1)];
    return new Response(typeof next.body === "string" ? next.body : JSON.stringify(next.body), { status: next.status ?? 200 });
  };
  const base = setup({ OPENAI_API_KEY: openAiKey, FETCH: fetcher, ...vars }, async () => { throw new Error("Workers AI no debe usarse"); });
  return { ...base, calls };
}
const okOutput = text => ({ output: [{ type: "reasoning" }, { type: "message", content: [{ type: "output_text", text }] }] });

test("with OPENAI_API_KEY the server uses GPT-5 mini through the Responses API with strict JSON", async () => {
  const { env, calls, aiCalls } = openAiSetup([{ body: okOutput('{"kind":"answer","text":"La fotosíntesis convierte luz en energía."}') }]);
  assert.deepEqual(await (await handleRequest(new Request("https://example.test/health"), env)).json(), { configured: true, provider: "openai" });
  const response = await handleRequest(request(), env);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { kind: "answer", text: "La fotosíntesis convierte luz en energía." });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://api.openai.com/v1/responses");
  assert.equal(calls[0].auth, `Bearer ${openAiKey}`);
  assert.equal(calls[0].body.model, "gpt-5-mini");
  assert.equal(calls[0].body.instructions, SYSTEM_PROMPT);
  assert.deepEqual(JSON.parse(calls[0].body.input), input);
  assert.equal(calls[0].body.store, false);
  assert.equal(calls[0].body.reasoning.effort, "minimal");
  assert.equal(calls[0].body.text.format.type, "json_schema");
  assert.equal(calls[0].body.text.format.strict, true);
  assert.equal(calls[0].body.text.format.schema.additionalProperties, false);
  assert.equal(aiCalls(), 0);
});

test("the model name can change in wrangler.toml and an unsupported reasoning level is retried without it", async () => {
  const { env, calls } = openAiSetup([
    { status: 400, body: { error: { message: "Unsupported value: 'reasoning.effort' does not support 'minimal'" } } },
    { body: okOutput('{"kind":"clarify","text":"¿Para qué día?"}') },
  ], { OPENAI_MODEL: "gpt-5.6-terra" });
  const response = await handleRequest(request(), env);
  assert.equal(response.status, 200);
  assert.equal(calls.length, 2);
  assert.equal(calls[0].body.model, "gpt-5.6-terra");
  assert.equal(calls[1].body.reasoning, undefined);
});

test("OpenAI quota and key errors become simple Spanish messages and never leak the key", async () => {
  const quota = openAiSetup([{ status: 429, body: { error: { message: "Rate limit" } } }]);
  const r1 = await handleRequest(request(), quota.env);
  assert.equal(r1.status, 429);
  const bad = openAiSetup([{ status: 401, body: { error: { message: "Incorrect API key " + openAiKey } } }]);
  const r2 = await handleRequest(request(), bad.env);
  assert.equal(r2.status, 503);
  const text = await r2.text();
  assert.ok(!text.includes(openAiKey));
  assert.match(text, /PONER_CLAVE_OPENAI/);
});

test("an invalid OpenAI key format falls back to the free Cloudflare model", async () => {
  const { env, aiCalls } = setup({ OPENAI_API_KEY: "no-es-una-clave" });
  assert.equal((await handleRequest(request(), env)).status, 200);
  assert.equal(aiCalls(), 1);
});

const geminiKey = "AQ." + "x".repeat(40);
function geminiReply(text) {
  return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text }] } }] }), { status: 200, headers: { "Content-Type": "application/json" } });
}

test("with a Gemini key the server answers with Gemini and never sends the key to the app", async () => {
  const calls = [];
  const { env, aiCalls } = setup({ GEMINI_API_KEY: geminiKey, FETCH: async (url, options) => { calls.push({ url, options }); return geminiReply('{"kind":"answer","text":"La fotosíntesis convierte luz en energía."}'); } });
  assert.deepEqual(await (await handleRequest(new Request("https://example.test/health"), env)).json(), { configured: true, provider: "gemini" });
  const response = await handleRequest(request(), env);
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.deepEqual(body, { kind: "answer", text: "La fotosíntesis convierte luz en energía." });
  assert.equal(calls.length, 1);
  assert.match(calls[0].url, /gemini-flash-latest:generateContent$/);
  assert.equal(calls[0].options.headers["x-goog-api-key"], geminiKey);
  assert.ok(!JSON.stringify(body).includes(geminiKey));
  assert.equal(aiCalls(), 0);
});

test("when Gemini hits its limit the free Cloudflare model answers instead", async () => {
  const { env, aiCalls } = setup({ GEMINI_API_KEY: geminiKey, FETCH: async () => new Response("quota", { status: 429 }) });
  const response = await handleRequest(request(), env);
  assert.equal(response.status, 200);
  assert.equal((await response.json()).kind, "answer");
  assert.equal(aiCalls(), 1);
});

test("Gemini retries once without the thinking setting when the model rejects it", async () => {
  const bodies = [];
  const { env } = setup({ GEMINI_API_KEY: geminiKey, FETCH: async (url, options) => {
    bodies.push(JSON.parse(options.body));
    return bodies.length === 1 ? new Response("thinking budget is not supported", { status: 400 }) : geminiReply('{"kind":"clarify","text":"¿Para qué día?"}');
  } });
  const response = await handleRequest(request(), env);
  assert.equal(response.status, 200);
  assert.ok(bodies[0].generationConfig.thinkingConfig);
  assert.equal(bodies[1].generationConfig.thinkingConfig, undefined);
});

test("a malformed Gemini key is ignored", async () => {
  const { env } = setup({ GEMINI_API_KEY: "not-a-key" });
  assert.deepEqual(await (await handleRequest(new Request("https://example.test/health"), env)).json(), { configured: true, provider: "workers-ai" });
});

test("with a Groq key, Groq backs up Gemini before the Cloudflare model and the key never reaches the app", async () => {
  const groqKey = "gsk_" + "Z".repeat(40);
  const calls = [];
  const fetcher = async (url, options) => {
    calls.push({ url, auth: options.headers.Authorization ?? options.headers["x-goog-api-key"], body: JSON.parse(options.body) });
    if (url.includes("generativelanguage")) return new Response("{}", { status: 429 });
    return new Response(JSON.stringify({ choices: [{ message: { content: '{"kind":"answer","text":"Respuesta de Groq"}' } }] }), { status: 200 });
  };
  const { env, aiCalls } = setup({ GEMINI_API_KEY: "AIza" + "b".repeat(35), GROQ_API_KEY: groqKey, FETCH: fetcher });
  const res = await handleRequest(request(), env);
  const text = await res.text();
  assert.equal(res.status, 200);
  assert.equal(JSON.parse(text).text, "Respuesta de Groq");
  assert.ok(!text.includes(groqKey));
  assert.equal(calls.length, 2);
  assert.equal(calls[1].url, "https://api.groq.com/openai/v1/chat/completions");
  assert.equal(calls[1].auth, `Bearer ${groqKey}`);
  assert.equal(calls[1].body.response_format.type, "json_object");
  assert.equal(aiCalls(), 0);
});

test("Groq alone is the primary provider, and when it fails the free Cloudflare model answers", async () => {
  const fetcher = async () => new Response("{}", { status: 503 });
  const { env, aiCalls } = setup({ GROQ_API_KEY: "gsk_" + "Q".repeat(30), FETCH: fetcher });
  assert.equal((await (await handleRequest(new Request("https://example.test/health"), env)).json()).provider, "groq");
  const res = await handleRequest(request(), env);
  assert.equal(res.status, 200);
  assert.equal(aiCalls(), 1);
  const bad = setup({ GROQ_API_KEY: "sk-not-groq" });
  assert.equal((await (await handleRequest(new Request("https://example.test/health"), bad.env)).json()).provider, "workers-ai");
});
