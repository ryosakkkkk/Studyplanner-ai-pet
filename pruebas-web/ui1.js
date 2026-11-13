// Recorrido de la interfaz en tamaño de teléfono; guarda capturas en test/cap/
const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const tema = process.argv[2] || 'claro', out = __dirname + '/cap'; fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 393, height: 780 }, deviceScaleFactor: 2 }); const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push('pageerror ' + e.message)); p.on('console', m => { if (m.type() === 'error') errs.push('console ' + m.text()); });
  await p.goto('http://127.0.0.1:8771/camino/index.html' + (tema === 'oscuro' ? '?tema=oscuro' : '')); await p.evaluate(() => localStorage.clear()); await p.reload();
  const cap = async n => { await p.waitForTimeout(150); await p.screenshot({ path: `${out}/${tema}_${n}.png`, fullPage: true }); };
  await cap('01_grupo');
  await p.click('.gr[data-g="A"]'); await cap('02_diag_intro');
  await p.click('#go'); await cap('03_diag_p1');
  const n = await p.evaluate(() => 12);
  for (let i = 0; i < 12; i++) { if (i % 3 === 2) await p.click('#ns'); else { await p.click('.opc >> nth=' + (i % 5)); if (i === 4) await cap('04_diag_p5_sel'); await p.click('#sig'); } }
  await cap('05_diag_fin');
  await p.click('details >> nth=3'); await cap('06_diag_revision');
  await p.click('#ver'); await cap('07_camino');
  await p.click('[data-c="ALG"]'); await cap('08_curso');
  await p.evaluate(() => caminoAtras());
  console.log('estado', JSON.stringify(await p.evaluate(() => ({ pila: caminoEstado().pila, grupo: caminoEstado().S.grupo, diag: caminoEstado().S.diag }))).slice(0, 300));
  console.log('errores', errs.length, errs.slice(0, 5));
  await b.close();
})();
