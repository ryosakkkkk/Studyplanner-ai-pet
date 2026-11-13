// Prueba de datos y lógica: todos los temas tienen fuente o son de la app, diagnóstico de cada grupo, camino y dominio.
const katex = require('../app/src/main/assets/web/camino/katex/katex.min.js');
globalThis.window = globalThis;
for (const f of ['fig', 'gen', 'gen2', 'gen3', 'gen4', 'nativo', 'datos', 'datos2', 'plan']) require('../app/src/main/assets/web/camino/' + f + '.js');
const { DATOS: D, Plan: P, NATIVO: NAT, Gen } = globalThis; let fallas = 0; const mal = m => { fallas++; console.log('FALLA', m); };
globalThis.window = globalThis;
function tex(s, ctx) { if ((s.split('$').length - 1) % 2) mal('$ impares ' + ctx); const re = /\$([^$]+)\$/g; let m; while ((m = re.exec(s))) { try { katex.renderToString(m[1], { throwOnError: true, strict: 'error' }); } catch (e) { mal('KaTeX ' + ctx + ': ' + m[1]); } } }
const ts = P.temas(); const ids = new Set();
for (const t of ts) {
  if (ids.has(t.id)) mal('id repetido ' + t.id); ids.add(t.id);
  if (!D.cursos[t.curso]) mal('curso ' + t.curso);
  if (t.nat) { if (!NAT.temas[t.id]) mal('tema de la app desconocido: ' + t.id); }
  else { if (!t.gen && !(t.pregs && t.pregs.length >= 6)) mal('tema nuevo sin generador ni 6 preguntas: ' + t.id); if (!D.idea[t.id]) mal('sin idea clave: ' + t.id); (D.idea[t.id] || []).forEach(x => tex(x, 'idea ' + t.id)); }
  if (!t.nat && t.pregs) for (const q of t.pregs) { if (q.o.length !== 5 || new Set(q.o).size !== 5) mal('alternativas ' + t.id + ': ' + q.e); if (!q.x || q.x.length < 20) mal('explicación corta ' + t.id); tex(q.e + q.x + q.o.join(' '), t.id); }
}
for (const g of Object.keys(Gen.G)) if (!ids.has(g)) mal('generador sin tema: ' + g);
for (const k of Object.keys(D.pregs)) if (!ids.has(k)) mal('preguntas sin tema: ' + k);
for (const g of Object.keys(D.grupos)) {
  const G = D.grupos[g], tot = Object.values(G.cursos).reduce((a, b) => a + b, 0); if (tot !== 60) mal('el grupo ' + g + ' suma ' + tot);
  for (const c of Object.keys(G.cursos)) if (!P.temasCurso(c).length) mal('curso sin temas ' + c);
  const esperado = Object.values(G.cursos).reduce((a, w) => a + (w >= 9 ? 2 : 1), 0);
  for (let s = 1; s <= 300; s++) { const d = P.diagnostico(g, s * 7919); if (d.length !== esperado) mal(`diagnóstico ${g} semilla ${s}: ${d.length} de ${esperado}`); for (const q of d) { if (q.o.length !== 5 || new Set(q.o).size !== 5 || !(q.c >= 0 && q.c < 5) || !q.pasos.length) mal('pregunta de diagnóstico ' + q.tema); } }
  // camino sin diagnóstico, con diagnóstico malo en un curso y con todo dominado
  const S = { grupo: g }, c0 = P.camino(S, {}); if (c0.length !== P.temasDe(g).length) mal('camino incompleto ' + g);
  const flojo = Object.keys(G.cursos)[3], S2 = { grupo: g, diag: { hecho: true, res: Object.fromEntries(Object.keys(G.cursos).map(c => [c, { ok: c === flojo ? 0 : 1, n: 1 }])) } };
  const c2 = P.camino(S2, {}); const pos = c2.slice(0, 6).filter(x => x.t.curso === flojo).length;
  console.log(`grupo ${g}: ${P.temasDe(g).length} temas, diagnóstico de ${esperado} preguntas | primeras etapas: ${c0.slice(0, 6).map(x => x.t.curso).join(' ')} | con ${flojo} flojo: ${c2.slice(0, 6).map(x => x.t.curso).join(' ')} (${pos} de 6)`);
  if (pos < 1) mal('el curso flojo no sube en el camino ' + g);
  for (const c of Object.keys(G.cursos)) { const orden = c2.filter(x => x.t.curso === c).map(x => x.t.id), base = P.temasCurso(c).map(t => t.id); if (orden.join() !== base.join()) mal('orden dentro del curso ' + c); }
}
// dominio: 4 de 5 aciertos
const S3 = { grupo: 'A' }; for (const v of [1, 1, 0, 1]) P.anotar(S3, 'ari-logica', v); if (P.dominio(S3, {}, P.tema('ari-logica')) >= 1) mal('dominado con 4 respuestas');
P.anotar(S3, 'ari-logica', 1); if (P.dominio(S3, {}, P.tema('ari-logica')) !== 1) mal('no domina con 4 de 5');
if (P.camino(S3, {}).some(x => x.t.id === 'ari-logica')) mal('tema dominado sigue en el camino');
if (P.dominio({}, { 'mcd-mcm': 'DOMINIO_SIN_AYUDA' }, P.tema('mcd-mcm')) !== 1) mal('dominio de tema de la app');
console.log('temas', ts.length, '| de la app', ts.filter(t => t.nat).length, '| nuevos con generador', ts.filter(t => t.gen).length, '| nuevos con preguntas fijas', ts.filter(t => !t.nat && !t.gen).length, '| preguntas fijas nuevas', ts.filter(t => !t.nat && !t.gen).reduce((a, t) => a + t.pregs.length, 0));
console.log('fallas', fallas); process.exit(fallas ? 1 : 0);
