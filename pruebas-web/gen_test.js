// Prueba de los generadores: N semillas por tema y dificultad. Comprueba 5 alternativas distintas, índice correcto,
// pasos no vacíos y que TODA fórmula ($...$) compile en KaTeX.
const path = require('path'); const katex = require(path.join(__dirname, '../app/src/main/assets/web/camino/katex/katex.min.js'));
globalThis.window = globalThis;
require('../app/src/main/assets/web/camino/fig.js'); require('../app/src/main/assets/web/camino/gen.js');
for (const f of ['gen2.js', 'gen3.js', 'gen4.js']) { try { require('../app/src/main/assets/web/camino/' + f); } catch (e) { if (e.code !== 'MODULE_NOT_FOUND') throw e; } }
const Gen = globalThis.Gen; const N = +(process.argv[2] || 200); const solo = process.argv[3];
function tex(s, ctx) {
  const re = /\$([^$]+)\$/g; let m;
  if ((s.split('$').length - 1) % 2) throw new Error('signos $ impares en ' + ctx + ': ' + s);
  while ((m = re.exec(s))) { try { katex.renderToString(m[1], { throwOnError: true, strict: 'error' }); } catch (e) { throw new Error('KaTeX en ' + ctx + ': ' + m[1] + ' -> ' + e.message); } }
  if (/undefined|NaN|\[object/.test(s)) throw new Error('texto roto en ' + ctx + ': ' + s);
}
let total = 0, fallas = 0;
for (const id of Object.keys(Gen.G)) {
  if (solo && id !== solo) continue;
  const enun = new Set(); let errs = 0;
  for (let d = 1; d <= 3; d++) for (let s = 1; s <= N; s++) {
    total++;
    try {
      const q = Gen.crear(id, s * 7919 + d * 104729, d);
      if (q.o.length !== 5 || new Set(q.o).size !== 5) throw new Error('alternativas: ' + JSON.stringify(q.o));
      if (!(q.c >= 0 && q.c < 5)) throw new Error('índice');
      if (!q.pasos || q.pasos.length < 2) throw new Error('pasos');
      tex(q.e, 'enunciado'); q.o.forEach(o => tex(o, 'alternativa')); q.pasos.forEach(p => tex(p.replace(/<svg[\s\S]*?<\/svg>/g, ''), 'paso'));
      enun.add(q.e);
    } catch (e) { errs++; fallas++; if (errs <= 2) console.log('  FALLA', id, 'd' + d, 's' + s, e.message.slice(0, 400)); }
  }
  console.log((errs ? 'MAL ' : 'ok  ') + id.padEnd(22), 'enunciados distintos', enun.size, 'de', 3 * N, errs ? '| fallas ' + errs : '');
}
console.log('total', total, 'fallas', fallas);
process.exit(fallas ? 1 : 0);
