globalThis.window = globalThis;
const W = __dirname + '/../../app/src/main/assets/web/camino/';
for (const f of ['fig.js', 'gen.js', 'gen2.js', 'gen3.js', 'gen4.js', 'nativo.js', 'datos.js', 'datos2.js', 'plan.js']) require(W + f);
for (const id of Object.keys(DATOS.pregs)) {
  const t = Plan.tema(id), ps = DATOS.pregs[id];
  console.log('\n== ' + id + ' | ' + t.t + ' | ' + ps.length);
  ps.slice(0, 3).forEach(q => console.log('  E: ' + q.e.slice(0, 150) + '\n     R: ' + String(q.o[0]).slice(0, 70) + ' | X: ' + q.x.slice(0, 150)));
}
