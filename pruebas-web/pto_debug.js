// Busca pasos donde un signo de puntuación queda solo en una línea después de una fórmula (ancho de teléfono).
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 393, height: 780 } });
  await p.goto('http://127.0.0.1:8771/camino/index.html');
  await p.evaluate(() => { localStorage.setItem('camino', JSON.stringify({ grupo: 'A', diag: { omitido: true } })); }); await p.reload();
  const ids = (process.argv[2] || 'alg-funciones').split(',');
  let malos = 0, vistos = 0;
  for (const id of ids) for (let k = 0; k < +(process.argv[3] || 12); k++) {
    await p.evaluate(id => caminoIr('tema', id), id); await p.click('#pr'); await p.waitForTimeout(40);
    await p.click('#ns'); await p.click('#sol'); await p.waitForTimeout(60);
    const r = await p.evaluate(() => {
      const out = [];
      // nodos de texto que empiezan con puntuación y cuyo rectángulo está en otra línea que el elemento anterior
      const w = document.createTreeWalker(document.getElementById('app'), NodeFilter.SHOW_TEXT);
      for (let n = w.nextNode(); n; n = w.nextNode()) {
        if (!/^[.,;:!?)»]/.test(n.nodeValue) || !n.previousSibling || n.previousSibling.nodeType !== 1) continue;
        const rg = document.createRange(); rg.setStart(n, 0); rg.setEnd(n, 1); const a = rg.getBoundingClientRect(), pr = n.previousSibling.getBoundingClientRect();
        if (a.width && a.top > pr.bottom - 4) out.push({ t: n.nodeValue.slice(0, 20), prevTag: n.previousSibling.tagName, padre: n.parentNode.tagName + '.' + n.parentNode.className, prevHtml: n.previousSibling.outerHTML.slice(0, 80), sig: n.nextSibling ? (n.nextSibling.tagName || 'texto') : 'nada', top: [Math.round(a.top), Math.round(pr.top), Math.round(pr.bottom)] });
      }
      // y signos .pto que quedaron en una línea distinta a la de su fórmula
      document.querySelectorAll('.pto').forEach(s => { const a = s.getBoundingClientRect(), b = s.parentNode.getBoundingClientRect(); if (a.top > b.bottom - 4) out.push({ t: 'pto', html: s.parentNode.outerHTML.slice(-200) }); });
      return out;
    });
    vistos++; if (r.length) { malos++; if (malos <= 3) console.log(id, JSON.stringify(r[0]).slice(0, 520)); }
    await p.evaluate(() => { caminoAtras(); const s = document.querySelector('[data-a="si"]'); if (s) s.click(); caminoAtras(); });
  }
  console.log('preguntas vistas', vistos, '| con puntuación suelta', malos);
  await b.close();
})();
