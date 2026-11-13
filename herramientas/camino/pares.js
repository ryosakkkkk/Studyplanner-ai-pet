// Muestra, para ciertas reglas, la pregunta de guía junto al paso que revela (para revisar que calcen).
globalThis.window = globalThis;
const W = __dirname + '/../../app/src/main/assets/web/camino/';
for (const f of ['fig.js', 'gen.js', 'gen2.js', 'gen3.js', 'gen4.js', 'guia.js']) require(W + f);
const limpio = h => String(h).replace(/<svg[\s\S]*?<\/svg>/g, '[FIG]').replace(/<table[\s\S]*?<\/table>/g, '[TABLA]').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
const pedir = process.argv.slice(2);
for (const id of Object.keys(Gen.G)) {
  const vistos = new Set();
  for (let d = 1; d <= 3; d++) for (let s = 1; s <= 80; s++) {
    const q = Gen.crear(id, s * 7919 + d * 104729, d), n = q.pasos.length - 1;
    const r = Guia.temas[id].findIndex(r => (!r[2] || r[2] === n) && (!r[0] || r[0].test(q.e)));
    const k = id + '#' + r; if (vistos.has(k) || (pedir.length && !pedir.includes(k) && !pedir.includes(id))) continue; vistos.add(k);
    const g = Guia.de(id, q);
    console.log('\n' + k + '  E: ' + limpio(q.e).slice(0, 120));
    g.forEach((x, i) => console.log('   ? ' + x.slice(0, 110) + '\n     -> ' + limpio(q.pasos[i]).slice(0, 120)));
    console.log('     = ' + limpio(q.pasos[n]).slice(0, 90));
  }
}
