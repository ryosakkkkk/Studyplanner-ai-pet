// Lógica del camino: temas por grupo, examen de diagnóstico, nivel por curso y orden de las etapas. Sin interfaz.
(function (root) {
  'use strict';
  const D = () => root.DATOS, NAT = () => root.NATIVO, GEN = () => root.Gen;
  let _temas = null;
  function temas() {
    if (_temas) return _temas;
    _temas = D().lista.map(([id, curso, f, titulo]) => {
      const nat = !titulo;
      return { id, curso, f, nat, t: titulo || (NAT().temas[id] || {}).t || id, gen: !nat && !!GEN().G[id], pregs: nat ? (NAT().banco[id] || null) : (D().pregs[id] || null) };
    });
    return _temas;
  }
  const tema = id => temas().find(t => t.id === id);
  const temasDe = grupo => { const cs = D().grupos[grupo].cursos; return temas().filter(t => cs[t.curso]); };
  const temasCurso = curso => temas().filter(t => t.curso === curso);

  // ---------- pregunta lista para mostrar: {e, o[5], c, pasos[], ref, curso, tema}
  function barajar(R, p) { // pregunta fija: baraja las alternativas
    const orden = R.sh(p.o.map((_, i) => i));
    return { e: p.e, o: orden.map(i => p.o[i]), c: orden.indexOf(p.c), pasos: Array.isArray(p.x) ? p.x : [p.x], ref: p.ref || '' };
  }
  function pregunta(t, semilla, dif, evitar) {
    const R = GEN().rng(semilla); let q;
    if (t.gen) q = GEN().G[t.id](R, dif || 2);
    else if (t.pregs && t.pregs.length) {
      let idx = t.pregs.map((_, i) => i).filter(i => !(evitar || []).includes(i)); if (!idx.length) idx = t.pregs.map((_, i) => i);
      const i = R.pick(idx); q = barajar(R, t.pregs[i]); q.i = i;
    } else return null;
    q.curso = t.curso; q.tema = t.id; q.titulo = t.t;
    return q;
  }
  const tieneFuente = t => t.gen || (t.pregs && t.pregs.length);

  // ---------- diagnóstico: una pregunta por curso del grupo y dos en los cursos que más pesan
  function diagnostico(grupo, semilla) {
    const R = GEN().rng(semilla), cs = D().grupos[grupo].cursos, out = [];
    for (const c of Object.keys(cs)) {
      const n = cs[c] >= 9 ? 2 : 1, cand = temasCurso(c).filter(tieneFuente), usados = [];
      for (let k = 0; k < n && cand.length; k++) {
        const libres = cand.filter(t => !usados.includes(t.id)), pool = (libres.length ? libres : cand).slice(0, 5);
        const tot = pool.reduce((a, t) => a + t.f, 0); let r = R.n() * tot, t = pool[0];
        for (const x of pool) { r -= x.f; if (r <= 0) { t = x; break; } }
        usados.push(t.id);
        const q = pregunta(t, R.int(1, 2000000000), R.n() < 0.6 ? 1 : 2);
        if (q) out.push(q);
      }
    }
    return out;
  }

  // ---------- dominio de un tema (0 a 1) y estado
  const NATV = { NO_INICIADO: 0, EN_PRACTICA: 0.4, SOLO_CON_AYUDA: 0.6, DOMINIO_SIN_AYUDA: 1 };
  function dominio(S, N, t) {
    if (t.nat) return NATV[(N || {})[t.id]] || 0;
    const r = (S.t || {})[t.id]; if (!r || !r.r || !r.r.length) return 0;
    const u = r.r.slice(-5), ok = u.reduce((a, v) => a + v, 0);
    if (u.length >= 5 && ok >= 4) return 1;
    return Math.min(0.8, 0.3 + 0.5 * ok / Math.max(u.length, 3));
  }
  const estado = m => m >= 1 ? 'dominado' : m > 0 ? 'en práctica' : 'nuevo';

  // ---------- nivel estimado por curso (0 a 1): diagnóstico más lo practicado después
  function nivelCurso(S, N, curso) {
    let ok = 0.5, n = 1; const d = ((S.diag || {}).res || {})[curso];
    if (d) { ok += d.ok * 1.5; n += d.n * 1.5; }
    for (const t of temasCurso(curso)) {
      if (t.nat) { const m = dominio(S, N, t); if (m > 0) { ok += m; n += 1; } }
      else { const r = (S.t || {})[t.id]; if (r && r.r && r.r.length) { const u = r.r.slice(-5); ok += u.reduce((a, v) => a + v, 0) / u.length * 2; n += 2; } }
    }
    return ok / n;
  }

  // ---------- el camino: todas las etapas pendientes, ordenadas
  function camino(S, N) {
    if (!S.grupo) return [];
    const g = D().grupos[S.grupo], cs = g.cursos, maxw = Math.max(...Object.values(cs)), nivel = {}, cola = {};
    for (const c of Object.keys(cs)) { nivel[c] = nivelCurso(S, N, c); cola[c] = temasCurso(c).map(t => ({ t, m: dominio(S, N, t) })).filter(x => x.m < 1); }
    // prioridad = peso del curso en el examen (suavizado) × frecuencia del tema × cuánto falta en el curso.
    // Los cursos recién usados bajan un rato para que el camino alterne y no se quede en uno solo.
    const out = [], rec = [];
    for (;;) {
      let best = null, bp = -1;
      for (const c of Object.keys(cola)) {
        const x = cola[c][0]; if (!x) continue;
        let p = Math.sqrt(cs[c] / maxw) * x.t.f * (0.3 + 0.7 * (1 - nivel[c]) * 2) * (x.m > 0 ? 1.3 : 1);
        const k = rec.indexOf(c); if (k >= 0) p *= [0.4, 0.55, 0.7, 0.85][k];
        if (p > bp) { bp = p; best = c; }
      }
      if (!best) break;
      const x = cola[best].shift(); out.push({ t: x.t, m: x.m, estado: estado(x.m) }); rec.unshift(best); if (rec.length > 4) rec.pop();
    }
    // lo que está en práctica va primero: se termina lo empezado (el más reciente arriba)
    const ult = S.ult; out.sort((a, b) => (b.t.id === ult && b.m > 0 ? 1 : 0) - (a.t.id === ult && a.m > 0 ? 1 : 0));
    return out;
  }
  function avance(S, N) {
    if (!S.grupo) return { hechos: 0, total: 0, cursos: [] };
    const ts = temasDe(S.grupo), hechos = ts.filter(t => dominio(S, N, t) >= 1).length;
    const cursos = Object.keys(D().grupos[S.grupo].cursos).map(c => { const tc = temasCurso(c); return { c, nivel: nivelCurso(S, N, c), hechos: tc.filter(t => dominio(S, N, t) >= 1).length, total: tc.length, peso: D().grupos[S.grupo].cursos[c] }; });
    return { hechos, total: ts.length, cursos };
  }
  // anota una respuesta de práctica en un tema nuevo
  function anotar(S, id, ok, i) {
    S.t = S.t || {}; const r = S.t[id] = S.t[id] || { r: [], n: 0, v: [] };
    r.r.push(ok ? 1 : 0); if (r.r.length > 10) r.r.shift(); r.n++;
    if (i != null) { r.v.push(i); if (r.v.length > 4) r.v.shift(); }
    S.ult = id;
  }

  // ---------- repaso espaciado (cajas de Leitner): cada tema practicado vuelve cuando toca, no antes
  // Caja 1 = mañana, 2 = en 3 días, 3 = en 7, 4 = en 14, 5 = en 30. Acertar sube de caja; fallar la devuelve a la 1.
  const INTERVALOS = [0, 1, 3, 7, 14, 30];
  const dos = n => (n < 10 ? '0' : '') + n;
  const fechaISO = d => d.getFullYear() + '-' + dos(d.getMonth() + 1) + '-' + dos(d.getDate());
  const hoy = () => fechaISO(new Date());
  function sumarDias(iso, n) { const p = String(iso).split('-').map(Number); return fechaISO(new Date(p[0], p[1] - 1, p[2] + n)); }
  // resultado de una sesión (o de una pregunta de repaso) en un tema: mueve su caja y fija el próximo repaso
  function programar(S, id, aciertos, total, dia) {
    S.rep = S.rep || {}; const r = S.rep[id] = S.rep[id] || { c: 1, p: dia };
    const f = total > 0 ? aciertos / total : 0;
    r.c = f >= 0.8 ? Math.min(5, r.c + 1) : f >= 0.5 ? Math.max(1, r.c) : 1;
    r.p = sumarDias(dia, INTERVALOS[r.c]);
    return r;
  }
  // días que faltan para el repaso de un tema (0 = hoy o ya venció; null = nunca practicado)
  function diasParaRepaso(S, id, dia) {
    const r = (S.rep || {})[id]; if (!r) return null;
    const a = String(dia).split('-').map(Number), b = String(r.p).split('-').map(Number);
    return Math.max(0, Math.round((new Date(b[0], b[1] - 1, b[2]) - new Date(a[0], a[1] - 1, a[2])) / 86400000));
  }
  // temas del grupo (de la página, no los de la app) con el repaso vencido, el más atrasado primero
  function vencidos(S, dia) {
    if (!S.grupo || !S.rep) return [];
    return temasDe(S.grupo).filter(t => !t.nat && tieneFuente(t) && S.rep[t.id] && S.rep[t.id].p <= dia)
      .sort((a, b) => (S.rep[a.id].p < S.rep[b.id].p ? -1 : S.rep[a.id].p > S.rep[b.id].p ? 1 : 0));
  }
  // preguntas del repaso de hoy: intercaladas (un tema distinto en cada una, en rueda), máximo n
  function repaso(S, dia, semilla, n) {
    const ts = vencidos(S, dia).slice(0, 6); if (!ts.length) return [];
    const R = GEN().rng(semilla), out = [], cant = Math.min(n || 6, Math.max(3, ts.length));
    for (let k = 0; out.length < cant && k < cant * 3; k++) {
      const t = ts[k % ts.length], q = pregunta(t, R.int(1, 2000000000), 2, ((S.t || {})[t.id] || {}).v);
      if (q) out.push(q);
    }
    return out;
  }
  root.Plan = { temas, tema, temasDe, temasCurso, pregunta, diagnostico, dominio, estado, nivelCurso, camino, avance, anotar, tieneFuente,
    INTERVALOS, hoy, sumarDias, programar, diasParaRepaso, vencidos, repaso };
})(typeof window !== 'undefined' ? window : globalThis);
