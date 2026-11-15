// Revisa el banco de exámenes UNAMBA armado (app/src/main/assets/web/camino/examenes.js) tal como lo lee la página.
// Ese archivo no está en el repositorio (uso autorizado solo dentro de la APK): si falta, la prueba se omite.
'use strict';
const fs = require('fs'), path = require('path');
const cam = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'web', 'camino');
const archivo = path.join(cam, 'examenes.js');
if (!fs.existsSync(archivo)) { console.log('examenes: no hay examenes.js (se arma con herramientas/examenes), se omite | fallas 0'); process.exit(0); }
const katex = require(path.join(cam, 'katex', 'katex.min.js'));
global.window = {};
require(archivo);
const E = window.EXAMENES;
let fallas = 0;
const falla = m => { fallas++; if (fallas <= 20) console.log('FALLA', m); };
const CURSOS = new Set(['RM', 'RV', 'LEN', 'LIT', 'ARI', 'ALG', 'GEO', 'TRI', 'FIS', 'QUI', 'BIO', 'ZOO', 'ECO', 'HIS', 'GEG', 'CIV', 'ECN', 'PSF', 'EST']);
// La página pone el HTML tal cual y KaTeX dibuja lo que va entre $…$: cada fórmula debe compilar.
function formulas(s, id) {
  const re = /\$([^$]+)\$/g; let m;
  if ((s.split('$').length - 1) % 2) falla(`${id}: signos $ impares`);
  while ((m = re.exec(s))) { try { katex.renderToString(m[1], { throwOnError: true, strict: 'ignore' }); } catch (e) { falla(`${id}: fórmula ${m[1].slice(0, 30)}`); } }
}
if (!E || !Array.isArray(E.lista) || !Array.isArray(E.preg)) { console.log('FALLA formato de window.EXAMENES'); process.exit(1); }
const exs = new Map(E.lista.map(x => [x.id, x]));
if (exs.size !== E.lista.length) falla('ids de examen repetidos');
const ids = new Set(), porEx = new Map(), letras = [0, 0, 0, 0, 0];
for (const p of E.preg) {
  if (ids.has(p.id)) falla(`id repetido ${p.id}`); ids.add(p.id);
  if (!exs.has(p.ex)) falla(`${p.id}: examen ${p.ex} no está en la lista`);
  porEx.set(p.ex, (porEx.get(p.ex) || 0) + 1);
  if (!CURSOS.has(p.curso)) falla(`${p.id}: curso ${p.curso}`);
  if (!(p.n > 0)) falla(`${p.id}: número`);
  if (!Array.isArray(p.o) || p.o.length !== 5 || new Set(p.o).size !== 5) falla(`${p.id}: alternativas`);
  if (!(Number.isInteger(p.c) && p.c >= 0 && p.c < 5)) falla(`${p.id}: respuesta`); else letras[p.c]++;
  if (!p.e || !Array.isArray(p.x) || !p.x.length) falla(`${p.id}: enunciado o explicación vacíos`);
  if (/<(?!br>)/.test([p.e, ...p.o, ...p.x].join(' ').replace(/\$[^$]*\$/g, ''))) falla(`${p.id}: HTML sin escapar`);
  if (p.fig && !fs.existsSync(path.join(cam, p.fig))) falla(`${p.id}: falta ${p.fig}`);
  formulas([p.e, ...p.o, ...p.x].join(' '), p.id);
}
for (const x of E.lista) if (porEx.get(x.id) !== x.total) falla(`${x.nombre}: total ${x.total} y hay ${porEx.get(x.id) || 0}`);
// Ninguna letra debe dominar (las compilaciones que traían todo en la A se barajan al armar).
const max = Math.max(...letras) / E.preg.length;
if (max > 0.3) falla(`una letra tiene el ${(max * 100).toFixed(0)} % de las respuestas`);
console.log(`examenes: ${E.lista.length} exámenes, ${E.preg.length} preguntas, respuestas A–E ${letras.join(' ')} | fallas ${fallas}`);
process.exit(fallas ? 1 : 0);
