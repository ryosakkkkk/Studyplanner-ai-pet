globalThis.window = globalThis;
const W = __dirname + '/../../app/src/main/assets/web/camino/';
for (const f of ['fig.js', 'gen.js', 'gen2.js', 'gen3.js', 'gen4.js']) require(W + f);
const limpio = h => String(h).replace(/<svg[\s\S]*?<\/svg>/g, '[FIG]').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
let n = 0;
for (let s = 1; s < 400 && n < 2; s++) { const q = Gen.crear('fis-dimensional', s * 7919 + 3 * 104729, 3); if (q.pasos.length === 4) { n++; console.log(limpio(q.e)); q.pasos.forEach((p, i) => console.log('  ' + (i + 1) + ') ' + limpio(p).slice(0, 200))); } }
