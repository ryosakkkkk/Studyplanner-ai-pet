// Arma el banco de preguntas reales de los exámenes UNAMBA para el Camino (uso autorizado por la universidad).
// Lee los .jsonl transcritos (una pregunta por línea; formato en herramientas/examenes/LEEME.md), valida cada pregunta,
// quita repetidas y escribe app/src/main/assets/web/camino/examenes.js y las figuras en exfig/. Esos archivos NO se suben
// al repositorio público (están en .gitignore): este script no contiene ninguna pregunta.
// Uso: node herramientas/examenes/arma_examenes.js [carpeta_del_banco]
'use strict';
const fs = require('fs'), path = require('path');
const raiz = path.join(__dirname, '..', '..');
const banco = process.argv[2] || 'D:\\StudyPlanner-Android-Toolchain\\examenes_trabajo\\banco';
const cam = path.join(raiz, 'app', 'src', 'main', 'assets', 'web', 'camino');
const katex = require(path.join(cam, 'katex', 'katex.min.js'));
const CURSOS = new Set(['RM', 'RV', 'LEN', 'LIT', 'ARI', 'ALG', 'GEO', 'TRI', 'FIS', 'QUI', 'BIO', 'ZOO', 'ECO', 'HIS', 'GEG', 'CIV', 'ECN', 'PSF', 'EST']);
const LETRAS = 'ABCDE';

const escH = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
// Texto del cuadernillo a HTML: se escapa lo que no es fórmula y los saltos de línea pasan a <br>.
function html(s) {
  return String(s).split(/(\$[^$]+\$)/g).map((p, i) => (i % 2 ? p : escH(p).replace(/\r?\n/g, '<br>'))).join('').trim();
}
function formulasMalas(s) {
  const malas = []; const re = /\$([^$]+)\$/g; let m;
  if ((String(s).split('$').length - 1) % 2) malas.push('signos $ impares');
  while ((m = re.exec(s))) { try { katex.renderToString(m[1], { throwOnError: true }); } catch (e) { malas.push(m[1].slice(0, 40)); } }
  return malas;
}
const sinTildes = s => String(s).toLowerCase().normalize('NFD').replace(/\p{M}+/gu, '');
const norm = s => sinTildes(s).replace(/\$[^$]*\$/g, m => m.replace(/\s+/g, '')).replace(/[^a-z0-9$\\]+/g, ' ').trim();
const slug = s => sinTildes(s).replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 60);
// Forma compacta para comparar enunciados escritos de forma un poco distinta por dos transcripciones.
const compacto = s => sinTildes(s).replace(/\\(operatorname|mathrm|text|left|right|quad)/g, '').replace(/[^a-z0-9+\-*/=^()<>|]+/g, '');

// Parecido entre dos textos (1 = iguales), por distancia de edición.
function parecido(a, b) {
  if (a === b) return 1;
  const n = a.length, m = b.length; if (!n || !m) return 0;
  let prev = new Array(m + 1), cur = new Array(m + 1);
  for (let j = 0; j <= m; j++) prev[j] = j;
  for (let i = 1; i <= n; i++) {
    cur[0] = i; const ca = a.charCodeAt(i - 1);
    for (let j = 1; j <= m; j++) cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca === b.charCodeAt(j - 1) ? 0 : 1));
    [prev, cur] = [cur, prev];
  }
  return 1 - prev[m] / Math.max(n, m);
}

// Generador pseudoaleatorio fijo por pregunta: el orden barajado es siempre el mismo en cada armado.
function azar(semilla) {
  let h = 2166136261; for (const ch of semilla) { h ^= ch.charCodeAt(0); h = Math.imul(h, 16777619); }
  return () => { h ^= h << 13; h ^= h >>> 17; h ^= h << 5; return ((h >>> 0) % 100000) / 100000; };
}
// Alternativas que nombran a otras («todas las anteriores», «A y B»): esas preguntas no se barajan.
const nombraOtras = o => /anterior|todas|ninguna|ambas|\b[A-E]\s*(y|o|,)\s*[A-E]\b/i.test(o);
// Letras en la explicación fuera de las fórmulas, salvo el «Respuesta: X» final, que sí se corrige.
// «Respuesta: B» o «Respuesta: (B)»: la letra sola, no la inicial de una palabra («Respuesta: Cóndor andino»).
const RE_RESPUESTA = /Respuesta:(\s*)\(?([A-E])\)?(?![\p{L}\p{N}\-])/gu;
const letraSuelta = s => /(alternativa|clave|opci[oó]n|\([A-E]\)|\b[A-E]\))/i.test(String(s).replace(/\$[^$]*\$/g, '').replace(RE_RESPUESTA, ''));

// Correcciones a mano de nombres de examen (opcional): banco/nombres.json = {"nombre en el .jsonl": "nombre correcto"}.
// Sirve para juntar trozos de un mismo examen que dos transcripciones nombraron distinto.
const rutaNombres = path.join(banco, 'nombres.json');
const renombrar = fs.existsSync(rutaNombres) ? JSON.parse(fs.readFileSync(rutaNombres, 'utf8').replace(/^﻿/, '')) : {};

// 1) Leer y validar.
const archivos = fs.readdirSync(banco).filter(f => f.endsWith('.jsonl')).sort();
const descartes = []; const validas = [];
let leidas = 0, noVerificadas = 0;
for (const f of archivos) {
  const lineas = fs.readFileSync(path.join(banco, f), 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/).filter(l => l.trim());
  for (const [k, l] of lineas.entries()) {
    let q; try { q = JSON.parse(l); } catch (e) { descartes.push(`${f}:${k + 1} JSON dañado`); continue; }
    leidas++;
    const malo = m => descartes.push(`${q.id || f + ':' + (k + 1)} ${m}`);
    if (!q.verificada) { noVerificadas++; malo('no verificada: ' + (q.nota || '')); continue; }
    if (!q.id || !q.examen || !(q.n > 0)) { malo('faltan id, examen o n'); continue; }
    if (renombrar[q.examen]) q.examen = renombrar[q.examen];
    if (!Array.isArray(q.o) || q.o.length !== 5 || new Set(q.o.map(o => String(o).replace(/\s+/g, ' ').trim())).size !== 5) { malo('alternativas: deben ser 5 distintas'); continue; }
    if (!(Number.isInteger(q.c) && q.c >= 0 && q.c < 5)) { malo('respuesta fuera de rango'); continue; }
    if (!q.e || String(q.e).trim().length < 4) { malo('enunciado vacío'); continue; }
    if (!CURSOS.has(q.curso)) { malo('curso desconocido ' + q.curso); continue; }
    q.x = (Array.isArray(q.x) ? q.x : [q.x]).filter(Boolean).map(String);
    if (!q.x.length) { malo('sin explicación'); continue; }
    const malas = formulasMalas([q.e, ...q.o, ...q.x].join(' '));
    if (malas.length) { malo('fórmula que no compila: ' + malas.join(' | ')); continue; }
    if (q.fig && !fs.existsSync(path.join(banco, q.fig))) { malo('falta la figura ' + q.fig); continue; }
    q.fechado = !!q.anio; q.ce = compacto(q.e).slice(0, 400); q.co = q.o.map(compacto).sort().join('|');
    validas.push(q);
  }
}
// Primero los cuadernillos con fecha: ante una repetida se queda la de ellos y no la de una compilación sin fecha.
validas.sort((a, b) => (b.fechado - a.fechado));

// 2) Quitar repetidas: idénticas (tronco común entre grupos) y casi iguales (la misma pregunta transcrita dos veces
// o reciclada en otro examen). Casi igual = enunciado parecido en 0,75 o más y alternativas en 0,8 o más; si solo se
// parece el enunciado («Hallar x» con otra figura u otros números) son preguntas distintas.
const quedan = [], vistos = new Map(), indice = new Map(), comunes = new Map();
const trigramas = s => { const t = new Set(); for (let i = 0; i + 3 <= s.length; i++) t.add(s.slice(i, i + 3)); return t; };
const repetidaDe = new Map(); // id descartado -> id que se queda
for (const q of validas) {
  const clave = norm(q.e).slice(0, 160) + '|' + q.o.map(norm).sort().join('|');
  if (vistos.has(clave)) { const p = vistos.get(clave); repetidaDe.set(q.id, p.id); if (p.examen !== q.examen) comunes.set(q.id, p); continue; }
  q.tri = trigramas(q.ce);
  const cuenta = new Map();
  for (const t of q.tri) for (const j of indice.get(t) || []) cuenta.set(j, (cuenta.get(j) || 0) + 1);
  let igual = null;
  for (const [j, c] of cuenta) {
    const p = quedan[j]; if (p.examen === q.examen) continue;
    if (c < 0.5 * Math.min(q.tri.size, p.tri.size)) continue;
    if (parecido(q.co, p.co) < 0.8) continue;
    if (parecido(q.ce, p.ce) < 0.75) continue;
    if (q.fig && p.fig && q.ce.length < 40 && parecido(q.co, p.co) < 0.95) continue; // «Hallar x» con figura: pide alternativas casi idénticas
    igual = p; break;
  }
  if (igual) { repetidaDe.set(q.id, igual.id); comunes.set(q.id, igual); continue; }
  vistos.set(clave, q);
  for (const t of q.tri) { if (!indice.has(t)) indice.set(t, []); indice.get(t).push(quedan.length); }
  quedan.push(q);
}
for (const [id, otro] of repetidaDe) descartes.push(`${id} repetida de ${otro}`);

// 3) Un examen de compilación que resulta ser un cuadernillo con fecha (la mayoría de sus repetidas caen en el mismo
// examen y con el mismo número): sus preguntas que faltaban en el cuadernillo pasan a ese examen.
const porId = new Map(validas.map(q => [q.id, q]));
const parejas = new Map(); // examen sin fecha -> Map(examen con fecha -> [coincidencias, mismo número])
for (const [id, otro] of comunes) {
  const q = porId.get(id); if (q.fechado || !otro.fechado) continue;
  if (!parejas.has(q.examen)) parejas.set(q.examen, new Map());
  const m = parejas.get(q.examen); const v = m.get(otro.examen) || [0, 0];
  v[0]++; if (q.n === otro.n) v[1]++; m.set(otro.examen, v);
}
const fusion = new Map();
for (const [sin, m] of parejas) for (const [con, [c, mismoN]] of m) {
  if (c >= 15 && mismoN >= 0.8 * c) { fusion.set(sin, con); console.log(`«${sin}» es «${con}» (${c} repetidas, ${mismoN} con el mismo número)`); }
}
for (const q of quedan) if (fusion.has(q.examen)) {
  const con = fusion.get(q.examen), anio = quedan.find(p => p.examen === con);
  if (quedan.some(p => p.examen === con && p.n === q.n)) { descartes.push(`${q.id} su número ya está en ${con}`); q.fuera = true; continue; }
  q.examen = con; q.anio = anio.anio; q.grupo = anio.grupo;
}

// Un examen que quedó con menos de 5 preguntas propias es casi entero una copia de otro: lo que sobra se deja fuera.
const propias = new Map();
for (const q of quedan) if (!q.fuera) propias.set(q.examen, (propias.get(q.examen) || 0) + 1);
for (const q of quedan) if (!q.fuera && propias.get(q.examen) < 5) { q.fuera = true; descartes.push(`${q.id} su examen es casi entero repetido`); }

// 4) Nombres de las compilaciones: «examen del folio N» pasa a «examen 1, 2, 3…» en el orden del tomo.
const reTomo = /^(Primeros|Segundos) exámenes CPU \(tomo (I|II)\), examen del folio (\d+)$/;
const folios = new Map();
for (const q of quedan) { if (q.fuera) continue; const m = reTomo.exec(q.examen); if (m) { const k = m[1] + ' ' + m[2]; if (!folios.has(k)) folios.set(k, new Set()); folios.get(k).add(+m[3]); } }
const nombre = ex => {
  const m = reTomo.exec(ex); if (!m) return ex;
  const orden = [...folios.get(m[1] + ' ' + m[2])].sort((a, b) => a - b).indexOf(+m[3]) + 1;
  return `${m[1]} exámenes CPU, tomo ${m[2]}: examen ${orden}`;
};

// 5) Compilaciones con la respuesta casi siempre en la A (vienen resueltas así): se barajan las alternativas para
// que no se aprenda la letra. Orden fijo por pregunta; se corrige el «Respuesta: X» de la explicación.
const cuentaA = new Map();
for (const q of quedan) { if (q.fuera) continue; const v = cuentaA.get(q.examen) || [0, 0]; v[0]++; if (q.c === 0) v[1]++; cuentaA.set(q.examen, v); }
let barajadas = 0;
for (const q of quedan) {
  if (q.fuera) continue;
  const [t, a] = cuentaA.get(q.examen);
  if (t < 8 || a < 0.6 * t) continue;
  if (q.o.some(nombraOtras) || q.x.some(letraSuelta)) continue;
  const r = azar(q.id), orden = [0, 1, 2, 3, 4];
  for (let i = 4; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [orden[i], orden[j]] = [orden[j], orden[i]]; }
  const nuevaC = orden.indexOf(q.c);
  q.o = orden.map(i => q.o[i]);
  q.x = q.x.map(s => s.replace(RE_RESPUESTA, `Respuesta:$1${LETRAS[nuevaC]}`));
  q.c = nuevaC; barajadas++;
}

// 6) Salida.
const figDest = path.join(cam, 'exfig');
fs.rmSync(figDest, { recursive: true, force: true }); fs.mkdirSync(figDest, { recursive: true });
const examenes = new Map(), preg = [];
for (const q of quedan) {
  if (q.fuera) continue;
  let fig = '';
  if (q.fig) { fig = 'exfig/' + path.basename(q.fig); fs.copyFileSync(path.join(banco, q.fig), path.join(figDest, path.basename(q.fig))); }
  const nom = nombre(q.examen), exId = slug(nom);
  if (!examenes.has(exId)) examenes.set(exId, { id: exId, nombre: nom, anio: q.anio || null, grupo: q.grupo || '', total: 0 });
  examenes.get(exId).total++;
  preg.push({ id: q.id, ex: exId, n: q.n, curso: q.curso, tema: q.tema || '', e: html(q.e), o: q.o.map(html), c: q.c, x: q.x.map(html), fig });
}
const lista = [...examenes.values()].sort((a, b) => (b.anio || 0) - (a.anio || 0) || a.nombre.localeCompare(b.nombre, 'es', { numeric: true }));
const salida = path.join(cam, 'examenes.js');
fs.writeFileSync(salida, '// Generado por herramientas/examenes/arma_examenes.js. Preguntas de exámenes UNAMBA (uso autorizado). No subir al repositorio.\n' +
  'window.EXAMENES=' + JSON.stringify({ lista, preg }) + ';\n');
const porCurso = {}; preg.forEach(p => { porCurso[p.curso] = (porCurso[p.curso] || 0) + 1; });
const letras = [0, 0, 0, 0, 0]; preg.forEach(p => letras[p.c]++);
const tam = (fs.statSync(salida).size / 1024).toFixed(0), figs = fs.readdirSync(figDest).length;
console.log(`leídas ${leidas} | en la app ${preg.length} de ${lista.length} exámenes | no verificadas ${noVerificadas} | repetidas ${repetidaDe.size} | otros descartes ${descartes.length - noVerificadas - repetidaDe.size}`);
console.log('por curso', JSON.stringify(porCurso));
console.log('respuestas por letra A–E', letras.join(' '), `| barajadas ${barajadas}`);
console.log(`examenes.js ${tam} KB, ${figs} figuras`);
fs.writeFileSync(path.join(banco, 'descartes.txt'), descartes.join('\n') + '\n');
console.log('detalle de descartes en', path.join(banco, 'descartes.txt'));
