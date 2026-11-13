// Lista, por tema generado, las formas distintas de la última frase del enunciado (para escribir las guías).
// uso: node formas.js
globalThis.window = globalThis;
const W = __dirname + '/../../app/src/main/assets/web/camino/';
for (const f of ['fig.js', 'gen.js', 'gen2.js', 'gen3.js', 'gen4.js']) require(W + f);
const limpio = h => String(h).replace(/<svg[\s\S]*?<\/svg>/g, '[FIG]').replace(/<table[\s\S]*?<\/table>/g, '[TABLA]').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
for (const id of Object.keys(Gen.G)) {
  const vistos = new Map();
  for (let d = 1; d <= 3; d++) for (let s = 1; s <= 120; s++) {
    const q = Gen.crear(id, s * 7919 + d * 104729, d);
    const t = limpio(q.e).replace(/\$[^$]*\$/g, '§');
    const fr = t.split(/(?<=[.?:])\s+/);
    const cola = fr[fr.length - 1];
    const k = cola.replace(/-?\d+([.,]\d+)?/g, '#').replace(/\b[A-ZÁÉÍÓÚ][a-záéíóúñ]+\b/g, 'N').slice(0, 64) + ' |' + (q.pasos.length - 1);
    vistos.set(k, (vistos.get(k) || 0) + 1);
  }
  console.log(id + ': ' + [...vistos.keys()].slice(0, 8).join('  //  '));
}
