// Capturas de la práctica con explicación: node ui2.js <tema claro|oscuro> <id,id,...> [semilla]
const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const tema = process.argv[2] || 'claro', ids = (process.argv[3] || 'ari-conjuntos').split(','), out = __dirname + '/cap'; fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 393, height: 780 }, deviceScaleFactor: 2 }); const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push('pageerror ' + e.message)); p.on('console', m => { if (m.type() === 'error') errs.push('console ' + m.text()); });
  await p.goto('http://127.0.0.1:8771/camino/index.html' + (tema === 'oscuro' ? '?tema=oscuro' : ''));
  await p.evaluate(() => { localStorage.setItem('camino', JSON.stringify({ grupo: 'A', diag: { omitido: true } })); }); await p.reload();
  for (const id of ids) {
    await p.evaluate(id => caminoIr('tema', id), id); await p.waitForTimeout(120);
    if (process.argv[4] === 'idea') await p.screenshot({ path: `${out}/${tema}_t_${id}_0.png`, fullPage: true });
    await p.click('#pr'); await p.waitForTimeout(120);
    const ok = await p.evaluate(() => caminoEstado().ses.q.c);
    await p.click('.opc >> nth=' + ((ok + (id.length % 2)) % 5)); await p.click('#resp'); await p.waitForTimeout(200); if (await p.$('#sol')) { await p.click('#sol'); } await p.waitForTimeout(250);
    await p.evaluate(() => window.scrollTo(0, 0));
    await p.screenshot({ path: `${out}/${tema}_t_${id}.png`, fullPage: true });
    const w = await p.evaluate(() => ({ sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth }));
    if (w.sw > w.cw + 1) console.log('DESBORDE horizontal en', id, w);
    await p.evaluate(() => { caminoAtras(); const b = document.querySelector('[data-a="si"]'); if (b) b.click(); caminoAtras(); });
  }
  console.log('errores', errs.length, errs.slice(0, 5)); await b.close();
})();
