// Tema oscuro y pantallas restantes: tema (idea clave), fin de práctica, camino con avance
const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const tema = process.argv[2] || 'oscuro', out = __dirname + '/cap';
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 393, height: 780 }, deviceScaleFactor: 2 }); const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push('pageerror ' + e.message)); p.on('console', m => { if (m.type() === 'error') errs.push('console ' + m.text()); });
  await p.goto('http://127.0.0.1:8771/camino/index.html' + (tema === 'oscuro' ? '?tema=oscuro' : ''));
  await p.evaluate(() => { localStorage.setItem('camino', JSON.stringify({ grupo: 'B', diag: { hecho: true, fecha: '2026-11-11', ok: 7, n: 13, res: { RM: { ok: 1, n: 2 }, RV: { ok: 2, n: 2 }, LEN: { ok: 1, n: 1 }, ARI: { ok: 0, n: 1 }, ALG: { ok: 1, n: 1 }, FIS: { ok: 0, n: 1 }, QUI: { ok: 0, n: 1 }, BIO: { ok: 1, n: 1 }, ECO: { ok: 1, n: 1 }, HIS: { ok: 0, n: 1 }, GEG: { ok: 0, n: 1 } } }, t: { 'ari-logica': { r: [1, 1, 1, 0, 1], n: 5, v: [] }, 'qui-gases': { r: [1, 0], n: 2, v: [] } }, ult: 'qui-gases' }));
    localStorage.setItem('camino_nat', JSON.stringify({ 'biologia-celula-organelos': 'DOMINIO_SIN_AYUDA', 'mcd-mcm': 'EN_PRACTICA' })); }); await p.reload();
  await p.waitForTimeout(200); await p.screenshot({ path: `${out}/${tema}_10_camino.png`, fullPage: true });
  await p.evaluate(() => caminoIr('tema', 'alg-funciones')); await p.waitForTimeout(150); await p.screenshot({ path: `${out}/${tema}_11_tema.png`, fullPage: true });
  await p.click('#pr');
  for (let i = 0; i < 5; i++) { await p.waitForTimeout(80); const ok = await p.evaluate(() => caminoEstado().ses.q.c); await p.click('.opc >> nth=' + (i === 2 ? (ok + 1) % 5 : ok)); await p.click('#resp'); await p.waitForTimeout(120); if (i === 2) { await p.screenshot({ path: `${out}/${tema}_12_guia.png`, fullPage: true }); await p.click('#vp'); await p.waitForTimeout(80); await p.screenshot({ path: `${out}/${tema}_12b_guia_paso.png`, fullPage: true }); await p.click('#sol'); await p.waitForTimeout(80); await p.screenshot({ path: `${out}/${tema}_12c_solucion.png`, fullPage: true }); } await p.click('#sig'); }
  await p.waitForTimeout(150); await p.screenshot({ path: `${out}/${tema}_13_fin.png`, fullPage: true });
  await p.evaluate(() => caminoIr('tema', 'lit-peruana')); await p.click('#pr'); await p.waitForTimeout(80); await p.click('.opc >> nth=1'); await p.click('#resp'); await p.waitForTimeout(150); await p.screenshot({ path: `${out}/${tema}_14_fija.png`, fullPage: true });
  console.log('errores', errs.length, errs.slice(0, 5)); await b.close();
})();
