// Recorre los temas agregados con los exámenes 2025 y 2026 (datos2.js): idea clave, práctica, guía y solución,
// y revisa la lista de cursos de los grupos B y C. uso: node nuevos_test.js [claro|oscuro]  (servidor en 8771)
const { chromium } = require('playwright');
const tema = process.argv[2] || 'claro', out = __dirname + '/cap';
const NUEVOS = ['eco-niveles', 'eco-energia', 'eco-global', 'len-lengua', 'len-fonetica', 'rv-semantica', 'civ-familia', 'civ-ddhh', 'his-fuentes', 'his-origen', 'his-antiguo', 'his-republica',
  'geg-tierra', 'geg-peru', 'ecn-necesidades', 'ecn-dinero', 'zoo-invertebrados', 'zoo-vertebrados'];
(async () => {
  const b = await chromium.launch(); const fallas = [], errs = []; const ok = (c, m) => { if (!c) fallas.push(m); };
  const abre = async grupo => {
    const ctx = await b.newContext({ viewport: { width: 393, height: 780 }, deviceScaleFactor: 2 }); const p = await ctx.newPage();
    p.on('pageerror', e => errs.push('pageerror ' + e.message)); p.on('console', m => { if (m.type() === 'error') errs.push('console ' + m.text()); });
    await p.goto('http://127.0.0.1:8771/camino/index.html' + (tema === 'oscuro' ? '?tema=oscuro' : ''));
    await p.evaluate(g => { localStorage.setItem('camino', JSON.stringify({ grupo: g, diag: { omitido: true } })); }, grupo); await p.reload();
    return p;
  };
  // cursos de cada grupo: los diez bloques del examen, cada uno con su número de preguntas
  const esperado = { A: 'RM RV LEN ARI ALG GEO TRI FIS QUI ECO', B: 'RM RV LEN ARI ALG FIS QUI BIO ZOO ECO', C: 'RM RV LEN ARI ALG ECO ECN HIS CIV GEG' };
  for (const g of 'ABC') {
    const p = await abre(g);
    const cs = await p.$$eval('[data-c]', xs => xs.map(x => x.getAttribute('data-c')));
    ok(cs.slice().sort().join(' ') === esperado[g].split(' ').sort().join(' '), `cursos del grupo ${g}: ${cs.join(' ')}`);
    const txt = await p.textContent('#app');
    ok(txt.includes('según los exámenes CPU y ordinario de 2025 y 2026'), 'falta la fuente de los pesos en el grupo ' + g);
    ok(!txt.includes('Literatura') && !txt.includes('Psicología'), 'el grupo ' + g + ' ya no lleva Literatura ni Psicología');
    if (g === 'B') { ok(txt.includes('Zoología'), 'el grupo B debe mostrar Zoología'); await p.screenshot({ path: `${out}/nue_${tema}_grupoB.png`, fullPage: true }); }
    if (g === 'C') await p.screenshot({ path: `${out}/nue_${tema}_grupoC.png`, fullPage: true });
    const total = await p.evaluate(g => Object.values(DATOS.grupos[g].cursos).reduce((a, b) => a + b, 0), g); ok(total === 60, 'el grupo ' + g + ' suma ' + total);
    await p.context().close();
  }
  // cada tema nuevo: idea, cinco preguntas con respuesta inmediata y, al final, las que falló otra vez
  const p = await abre('C'); let vistas = 0;
  for (const id of NUEVOS) {
    await p.evaluate(id => caminoIr('tema', id), id); await p.waitForTimeout(40);
    ok(!!(await p.$('.idea')) && (await p.$$('.idea p')).length >= 2, id + ': sin idea clave');
    if (id === 'eco-niveles') await p.screenshot({ path: `${out}/nue_${tema}_tema.png`, fullPage: true });
    await p.click('#pr'); await p.waitForTimeout(40);
    for (let k = 0; k < 12 && !(await p.$('.nota')); k++) {
      const q = await p.evaluate(() => { const q = caminoEstado().ses.q; return { c: q.c, n: q.o.length, e: q.e, x: q.pasos[0], ref: q.ref }; });
      ok(q.n === 5 && q.e.length > 8 && q.x.length > 20 && q.ref.includes('2025 y 2026'), id + ': pregunta incompleta');
      if (k < 5 && k % 2 === 0) {   // falla: ve al instante la correcta y la explicación; la pregunta vuelve al final
        await p.click('.opc >> nth=' + ((q.c + 1) % 5)); await p.click('#resp'); await p.waitForTimeout(40);
        ok((await p.textContent('.veredicto')).includes('Era la') && !!(await p.$('.opc.bien')) && !!(await p.$('.tarjeta .uno, .tarjeta .pasos')), id + ': el error no muestra la respuesta y su explicación');
        if (id === 'his-origen' && k === 0) await p.screenshot({ path: `${out}/nue_${tema}_error.png`, fullPage: true });
      } else { await p.click('.opc >> nth=' + q.c); await p.click('#resp'); await p.waitForTimeout(40); ok(!!(await p.$('.veredicto.ok')), id + ': no marca el acierto'); }
      const w = await p.evaluate(() => ({ sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth })); ok(w.sw <= w.cw + 1, id + ': desborde horizontal');
      vistas++; await p.click('#sig'); await p.waitForTimeout(40);
    }
    ok((await p.textContent('#app')).includes('de 5 a la primera') && (await p.textContent('#app')).includes('Corregiste 3'), id + ': no llegó al resumen con las 3 corregidas');
    await p.evaluate(() => { while (caminoAtras()) { const s = document.querySelector('[data-a="si"]'); if (s) s.click(); } });
  }
  console.log('temas nuevos', NUEVOS.length, '| preguntas vistas', vistas, '| errores de página', errs.length, errs.slice(0, 3));
  console.log(fallas.length || errs.length ? 'FALLAS ' + fallas.length + '\n' + fallas.slice(0, 12).join('\n') : 'temas nuevos bien, fallas 0');
  await b.close();
})();
