// Prueba de la guía socrática: cada pregunta generada recibe una pregunta de guía por paso, escrita para su tipo
// (no las genéricas), y toda fórmula de las guías compila en KaTeX. uso: node guia_test.js [semillas=40]
globalThis.window = globalThis;
const W = __dirname + '/../app/src/main/assets/web/camino/';
for (const f of ['fig.js', 'gen.js', 'gen2.js', 'gen3.js', 'gen4.js', 'nativo.js', 'datos.js', 'datos2.js', 'plan.js', 'guia.js']) require(W + f);
const katex = require(W + 'katex/katex.min.js');
const N = +(process.argv[2] || 40);
const GEN = new Set(['¿Qué datos te dan y qué te piden exactamente?', '¿Qué relación o fórmula une esos datos con lo que piden?', '¿Qué operación falta para llegar a la respuesta?']);
let total = 0, fallas = 0; const genericas = {}, reglas = {};
const falla = m => { fallas++; if (fallas <= 15) console.log('FALLA ' + m); };
const tex = (id, g) => { for (const m of g.matchAll(/\$([^$]+)\$/g)) { try { katex.renderToString(m[1], { throwOnError: true, strict: 'error' }); } catch (e) { falla(id + ' fórmula: ' + m[1] + ' -> ' + e.message.slice(0, 80)); } } if ((g.match(/\$/g) || []).length % 2) falla(id + ' signo $ suelto: ' + g); };
for (const id of Object.keys(Gen.G)) {
  if (!Guia.temas[id]) falla(id + ' no tiene guía propia');
  for (let d = 1; d <= 3; d++) for (let s = 1; s <= N; s++) {
    const q = Gen.crear(id, s * 7919 + d * 104729, d), n = q.pasos.length - 1, g = Guia.de(id, q); total++;
    if (g.length !== n) falla(`${id}: ${g.length} guías para ${n} pasos | ${q.e.slice(0, 80)}`);
    g.forEach((x, k) => { if (!x || x.length < 12) falla(id + ' guía vacía'); if (GEN.has(x)) genericas[id + ' paso ' + (k + 1) + ' de ' + n] = (genericas[id + ' paso ' + (k + 1) + ' de ' + n] || 0) + 1; tex(id, x); });
    const r = (Guia.temas[id] || []).findIndex(r => (!r[2] || r[2] === n) && (!r[0] || r[0].test(q.e)));
    reglas[id + '#' + r] = (reglas[id + '#' + r] || 0) + 1;
  }
}
// cada regla escrita debe usarse alguna vez (si no, su condición no calza con ningún enunciado)
for (const id of Object.keys(Guia.temas)) Guia.temas[id].forEach((r, i) => { if (!reglas[id + '#' + i]) falla(`${id}: la regla ${i} (${r[0]}) no se usa nunca`); r[1].forEach(x => tex(id, x)); });
for (const id of Object.keys(DATOS.pregs)) {
  const g = Guia.de(id, { e: DATOS.pregs[id][0].e, pasos: [DATOS.pregs[id][0].x] });
  if (g.length !== 2 || !Guia.fijas[id]) falla(id + ' (preguntas fijas) sin sus dos preguntas de guía');
  g.forEach(x => tex(id, x));
}
console.log('preguntas revisadas', total, '| temas con guía', Object.keys(Guia.temas).length, '+', Object.keys(Guia.fijas).length, 'de preguntas fijas');
console.log('pasos que cayeron en la guía genérica:', Object.keys(genericas).length ? JSON.stringify(genericas) : 'ninguno');
console.log(fallas ? 'FALLAS ' + fallas : 'guía bien, fallas 0');
