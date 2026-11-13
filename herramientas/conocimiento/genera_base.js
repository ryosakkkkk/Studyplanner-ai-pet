// Genera app/src/main/assets/conocimiento/base.json: lo que la mascota sabe sin Internet en «Hablar».
// Fuentes (todas propias del proyecto): el glosario de esta carpeta, la idea clave y las preguntas fijas del Camino
// UNAMBA (assets/web/camino) y los bancos de preguntas de la app (assets/tutor). No usa enunciados de exámenes.
// Uso (desde la raíz del proyecto):  node herramientas/conocimiento/genera_base.js
'use strict';
const fs = require('fs'), path = require('path');
const raiz = path.join(__dirname, '..', '..');
const cam = path.join(raiz, 'app', 'src', 'main', 'assets', 'web', 'camino');
const salida = path.join(raiz, 'app', 'src', 'main', 'assets', 'conocimiento', 'base.json');
globalThis.window = globalThis;
for (const f of ['fig', 'gen', 'gen2', 'gen3', 'gen4', 'nativo', 'datos', 'datos2', 'plan']) require(path.join(cam, f + '.js'));
const D = globalThis.DATOS, P = globalThis.Plan;

// ---------- texto de libro (HTML + LaTeX) a texto simple para la burbuja y la voz
const SIMB = { cdot: '·', times: '×', div: '÷', le: '≤', leq: '≤', ge: '≥', geq: '≥', neq: '≠', ne: '≠', approx: '≈', pi: 'π', theta: 'θ',
  alpha: 'α', beta: 'β', gamma: 'γ', delta: 'δ', lambda: 'λ', mu: 'μ', rho: 'ρ', sigma: 'σ', omega: 'ω', Omega: 'Ω', Delta: 'Δ', angle: '∠', triangle: '△', parallel: '∥', perp: '⊥', cup: '∪', cap: '∩', in: '∈', infty: '∞', to: '→',
  rightarrow: '→', Rightarrow: '⇒', sin: 'sen', cos: 'cos', tan: 'tan', cot: 'cot', sec: 'sec', csc: 'csc', log: 'log', pm: '±', ldots: '…',
  dots: '…', cdots: '…', circ: '°', overline: '', quad: ' ', qquad: ' ' };
const SUP = { 0: '⁰', 1: '¹', 2: '²', 3: '³', 4: '⁴', 5: '⁵', 6: '⁶', 7: '⁷', 8: '⁸', 9: '⁹', '-': '⁻', n: 'ⁿ' };
const SUB = { 0: '₀', 1: '₁', 2: '₂', 3: '₃', 4: '₄', 5: '₅', 6: '₆', 7: '₇', 8: '₈', 9: '₉' };
const sup = s => (/^[0-9n-]+$/.test(s) ? s.split('').map(c => SUP[c]).join('') : [...s].length === 1 ? '^' + s : '^(' + s + ')');
const sub = s => (/^[0-9]+$/.test(s) ? s.split('').map(c => SUB[c]).join('') : s);
function latex(m) {
  let s = m.replace(/\\(text|mathrm|mathbf|textbf|operatorname)\{([^{}]*)\}/g, '$2').replace(/\\left|\\right/g, '').replace(/\^\{?\\circ\}?/g, '°');
  for (let k = 0; k < 4; k++) s = s.replace(/\\[dt]?frac\{([^{}]*)\}\{([^{}]*)\}/g, (_, a, b) => (/^[\w.,]+$/.test(a) ? a : '(' + a + ')') + '/' + (/^[\w.,]+$/.test(b) ? b : '(' + b + ')'));
  s = s.replace(/\\sqrt\[3\]\{([^{}]*)\}/g, '∛($1)').replace(/\\sqrt\{([^{}]*)\}/g, (_, a) => '√' + (/^\w+$/.test(a) ? a : '(' + a + ')'));
  s = s.replace(/\^\{([^{}]*)\}/g, (_, a) => sup(a)).replace(/\^(\w)/g, (_, a) => sup(a)).replace(/_\{([^{}]*)\}/g, (_, a) => sub(a)).replace(/_(\w)/g, (_, a) => sub(a));
  s = s.replace(/\\[,;:! ]/g, ' ').replace(/\\%/g, '%').replace(/\\\{/g, '{').replace(/\\\}/g, '}');
  s = s.replace(/\\([a-zA-Z]+)/g, (x, w) => (SIMB[w] != null ? SIMB[w] : ''));
  return s.replace(/[{}]/g, '').replace(/\s+/g, ' ').trim();
}
const ENT = { '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&nbsp;': ' ', '&#39;': "'" };
function plano(h) {
  let s = String(h == null ? '' : h).replace(/<svg[\s\S]*?<\/svg>/g, ' ').replace(/<\/t[dh]>/g, ' | ').replace(/<\/tr>/g, '; ').replace(/<br\s*\/?>/g, ' ');
  s = s.replace(/\$\$?([^$]+)\$\$?/g, (_, m) => latex(m));
  s = s.replace(/<[^>]+>/g, '').replace(/&[a-z#0-9]+;/g, e => ENT[e] || ' ');
  return s.replace(/\s+/g, ' ').replace(/\s+([.,;:])/g, '$1').trim();
}
const corta = (s, n) => { if (s.length <= n) return s; const c = s.slice(0, n), p = Math.max(c.lastIndexOf('. '), c.lastIndexOf('? ')); return p > n * 0.5 ? c.slice(0, p + 1) : c.replace(/\s+\S*$/, '') + '…'; };
const slug = s => s.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 48);

const entradas = [], ids = new Set();
function poner(e) {
  if (!e.x || e.x.length < 20) return;
  let id = e.id, n = 2; while (ids.has(id)) id = e.id + '-' + n++;
  ids.add(id); entradas.push({ id, tipo: e.tipo, c: e.c, t: e.t, x: corta(e.x, e.tipo === 'def' ? 700 : 600), ...(e.q ? { q: corta(e.q, 400) } : {}), k: e.k || [], tema: e.tema || '' });
}

// 1. Glosario propio
const glos = JSON.parse(fs.readFileSync(path.join(__dirname, 'glosario.json'), 'utf8')).entradas;
for (const g of glos) poner({ id: 'g-' + slug(g.t), tipo: 'def', c: g.c, t: g.t, x: g.x, k: g.k });

// 2. Camino: idea clave de cada tema propio de la página y sus preguntas fijas
for (const t of P.temas()) {
  if (t.nat) continue;
  const idea = D.idea[t.id] || [];
  if (idea.length) poner({ id: 'idea-' + t.id, tipo: 'def', c: t.curso, t: t.t, x: plano(idea.join(' ')), tema: t.id });
  if (!t.gen && t.pregs) t.pregs.forEach((p, i) => {
    poner({ id: 'pc-' + t.id + '-' + i, tipo: 'preg', c: t.curso, t: t.t, q: plano(p.e), x: 'Respuesta: ' + plano(p.o[p.c]) + '. ' + plano(Array.isArray(p.x) ? p.x.join(' ') : p.x), tema: t.id });
  });
}

// 3. Bancos de preguntas de la app (los temas nativos que no son de matemática)
const AREA = { FISICA: 'FIS', QUIMICA: 'QUI', BIOLOGIA: 'BIO', ZOOLOGIA: 'ZOO', ECOLOGIA: 'ECO', HISTORIA: 'HIS', GEOGRAFIA: 'GEG', CIVICA: 'CIV',
  ECONOMIA: 'ECN', COMUNICACION: 'LEN', LENGUAJE: 'LEN', RAZONAMIENTO_VERBAL: 'RV', RAZ_VERBAL: 'RV', LITERATURA: 'LIT', PSICOLOGIA: 'PSF', FILOSOFIA: 'PSF' };
for (const f of ['banco_preguntas_v1.json', 'banco_preguntas_v1b.json', 'banco_preguntas_v2.json']) {
  const banco = JSON.parse(fs.readFileSync(path.join(raiz, 'app', 'src', 'main', 'assets', 'tutor', f), 'utf8'));
  for (const t of banco.topics) {
    const c = AREA[t.area] || t.area;
    poner({ id: 'bt-' + t.id, tipo: 'def', c, t: t.title, x: [t.objective].concat(t.explanation || []).join(' '), tema: t.id });
    for (const q of t.questions || []) poner({ id: 'bq-' + q.id, tipo: 'preg', c, t: t.title, q: q.statement, x: 'Respuesta: ' + q.options[q.correctIndex] + '. ' + q.explanation, tema: t.id });
  }
}

const cursos = {}; for (const k of Object.keys(D.cursos)) cursos[k] = D.cursos[k].n;
const base = { v: 1, generado: new Date().toISOString().slice(0, 10), fuentes: 'Glosario propio, Camino UNAMBA y bancos de preguntas de la app. Contenido original; no copia exámenes.', cursos, entradas };
fs.mkdirSync(path.dirname(salida), { recursive: true });
fs.writeFileSync(salida, JSON.stringify(base));
const cuenta = t => entradas.filter(e => e.tipo === t).length;
console.log(`base.json: ${entradas.length} entradas (${cuenta('def')} definiciones, ${cuenta('preg')} preguntas resueltas), ${(fs.statSync(salida).size / 1024).toFixed(0)} KB`);
const sinLatex = entradas.filter(e => /\\[a-zA-Z]|\$/.test((e.x + (e.q || '')).replace(/US\$/g, '')));
if (sinLatex.length) { console.log('AVISO: quedó notación sin convertir en', sinLatex.length, 'entradas, por ejemplo', sinLatex[0].id, sinLatex[0].x.slice(0, 120)); process.exitCode = 1; }
