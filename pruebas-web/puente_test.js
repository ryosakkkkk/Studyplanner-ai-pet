// Prueba del puente con la app: se simula window.AndroidCamino con las mismas firmas que CaminoBridge (Kotlin)
// y se comprueba qué llama la página, con qué tipos, y que el resumen sea el que CaminoSummary.parse espera.
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 393, height: 780 } }); const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push('pageerror ' + e.message)); p.on('console', m => { if (m.type() === 'error') errs.push('console ' + m.text()); });
  await p.addInitScript(() => {
    const L = (window.__llamadas = []); let estado = '', resumen = '';
    window.AndroidCamino = {
      cargar: () => { L.push(['cargar']); return estado; },
      guardar: (e, r) => { L.push(['guardar', typeof e, typeof r]); estado = e; resumen = r; window.__resumen = r; },
      dominio: () => { L.push(['dominio']); return window.__dom || '{}'; },
      cabecera: (t, f) => { L.push(['cabecera', t, f, typeof t, typeof f]); },
      evento: t => { L.push(['evento', t]); },
      abrirTema: id => { L.push(['abrirTema', id]); },
      iaDisponible: () => true,
      tutorIA: (id, pet) => { L.push(['tutorIA', id, pet, typeof id, typeof pet]); },
    };
  });
  const fallas = []; const ok = (c, m) => { if (!c) fallas.push(m); };
  {
    const q = await ctx.newPage(); await q.goto('http://127.0.0.1:8771/camino/index.html');
    await q.click('.gr[data-g="A"]'); await q.click('#go'); await q.click('#ns'); await q.waitForTimeout(80);
    const r1 = await q.evaluate(() => caminoAtras()); const n1 = await q.evaluate(() => document.querySelectorAll('.dlg').length);
    const r2 = await q.evaluate(() => caminoAtras()); const n2 = await q.evaluate(() => document.querySelectorAll('.dlg').length);
    for (let i = 0; i < 5; i++) await q.evaluate(() => caminoAtras());
    const n3 = await q.evaluate(() => document.querySelectorAll('.dlg').length);
    ok(r1 === true && n1 === 1 && r2 === true && n2 === 0 && n3 <= 1, `avisos al pulsar atrás: ${n1}, ${n2}, ${n3}`);
    const enDiag = await q.evaluate(() => document.querySelectorAll('.opc').length === 5);
    ok(enDiag, 'cerrar el aviso con atrás no debe salir del diagnóstico');
    await q.close();
  }
  await p.goto('http://127.0.0.1:8771/camino/index.html');
  await p.click('.gr[data-g="B"]'); await p.click('#go');
  const n = await p.evaluate(() => caminoEstado().ses ? 0 : 0);
  // el diagnóstico tiene una pregunta por curso y dos en los que más pesan: se responde hasta que aparece el resultado
  for (let i = 0; i < 20 && !(await p.$('#ver')); i++) { if (i % 4 === 3) await p.click('#ns'); else { await p.click('.opc >> nth=' + (i % 5)); await p.click('#sig'); } }
  await p.click('#ver'); await p.waitForTimeout(100);
  let L = await p.evaluate(() => window.__llamadas);
  ok(L.some(x => x[0] === 'cargar'), 'no llamó a cargar');
  ok(L.filter(x => x[0] === 'cabecera').every(x => x[3] === 'string' && x[4] === 'number' && x[2] >= -1 && x[2] <= 1), 'cabecera con tipos o rango malos');
  ok(L.filter(x => x[0] === 'guardar').length > 0 && L.filter(x => x[0] === 'guardar').every(x => x[1] === 'string' && x[2] === 'string'), 'guardar con tipos malos');
  const r = JSON.parse(await p.evaluate(() => window.__resumen));
  ok(r.g === 'B' && typeof r.n === 'string' && Number.isInteger(r.h) && Number.isInteger(r.t) && r.t > 0 && typeof r.s === 'string' && typeof r.c === 'string' && r.d === true, 'resumen inesperado ' + JSON.stringify(r));
  console.log('resumen', JSON.stringify(r));
  // tema de la app: debe pedir abrirlo con un id del catálogo nativo
  const nat = await p.evaluate(() => { const t = Plan.temas().find(t => t.nat && Plan.temasDe('B').some(x => x.id === t.id)); return t.id; });
  await p.evaluate(id => { const t = Plan.tema(id); document.querySelector('[data-c="' + t.curso + '"]').click(); }, nat); await p.waitForTimeout(80);
  await p.click(`[data-t="${nat}"]`); await p.waitForTimeout(80);
  L = await p.evaluate(() => window.__llamadas);
  ok(L.some(x => x[0] === 'abrirTema' && x[1] === nat), 'no pidió abrir el tema nativo ' + nat);
  // al volver, el dominio nativo se vuelve a leer y cuenta en el avance
  await p.evaluate(id => { window.__dom = JSON.stringify({ [id]: 'DOMINIO_SIN_AYUDA' }); caminoVolver(); }, nat); await p.waitForTimeout(80);
  const r2 = JSON.parse(await p.evaluate(() => window.__resumen));
  ok(r2.h === r.h + 1, `el avance no subió con el dominio nativo: ${r.h} -> ${r2.h}`);
  // tema nuevo: práctica dentro de la página, eventos y conversación con el tutor
  await p.evaluate(() => { while (caminoAtras()) { const s = document.querySelector('[data-a="si"]'); if (s) s.click(); } });
  ok((await p.evaluate(() => caminoAtras())) === false, 'caminoAtras en la raíz debe devolver false');
  await p.evaluate(() => caminoIr('tema', 'bio-genetica')); await p.waitForTimeout(80);
  await p.click('#pr'); await p.waitForTimeout(80);
  const c = await p.evaluate(() => caminoEstado().ses.q.c);
  await p.click('.opc >> nth=' + c); await p.click('#resp'); await p.waitForTimeout(200);
  L = await p.evaluate(() => window.__llamadas);
  ok(L.some(x => x[0] === 'evento' && x[1] === 'correcta'), 'sin evento correcta');
  const hayIA = await p.$('#ab');
  ok(!!hayIA, 'no aparece la entrada al tutor con la IA disponible');
  if (hayIA) {
    await p.click('#ab'); await p.fill('#msg', '¿por qué sale esa proporción?'); await p.click('#env'); await p.waitForTimeout(80);
    L = await p.evaluate(() => window.__llamadas);
    const e = L.find(x => x[0] === 'tutorIA');
    // lo que CaminoBridge.tutorIA y CaminoAiPrompt.relay esperan: dos textos, identificador de letras y números, una línea, menos de 3800
    ok(e && e[3] === 'string' && e[4] === 'string' && /^[a-zA-Z0-9]{1,24}$/.test(e[1]) && e[2].length > 200 && e[2].length <= 3800 && !/[\u0000-\u001f\u007f-\u009f\u2028\u2029]/.test(e[2]), 'tutorIA con argumentos malos ' + JSON.stringify(e).slice(0, 200));
    console.log('tutorIA', e[1], e[2].length, 'caracteres:', e[2].slice(0, 230) + ' … ' + e[2].slice(-150));
    await p.evaluate(id => caminoTutor(id, true, 'Texto de prueba de la IA.'), e[1]); await p.waitForTimeout(80);
    const t = await p.evaluate(() => document.body.innerText);
    ok(t.includes('Texto de prueba de la IA.') && t.includes('Responde una IA y puede equivocarse'), 'la respuesta de la IA no se muestra con su aviso');
  }
  await p.click('#sig'); await p.waitForTimeout(80);
  const w = await p.evaluate(() => caminoEstado().ses.q.c);
  await p.click('.opc >> nth=' + ((w + 1) % 5)); await p.click('#resp'); await p.waitForTimeout(200);
  await p.click('.opc >> nth=' + w); await p.click('#resp'); await p.waitForTimeout(100);
  await p.click('#sig'); await p.waitForTimeout(80); await p.click('#ns'); await p.waitForTimeout(80);
  L = await p.evaluate(() => window.__llamadas);
  const evs = L.filter(x => x[0] === 'evento').map(x => x[1]);
  ok(['incorrecta', 'guiada', 'duda'].every(x => evs.includes(x)), 'faltan eventos de la guía: ' + JSON.stringify(evs));
  // los mismos nombres que atiende onEvent en CaminoScreen.kt
  ok(evs.every(x => ['correcta', 'incorrecta', 'duda', 'guiada', 'logro'].includes(x)), 'evento desconocido');
  // «atrás» dentro de la práctica: la página lo maneja (devuelve true)
  ok((await p.evaluate(() => caminoAtras())) === true, 'caminoAtras en la práctica debe devolver true');
  console.log('llamadas', L.length, 'eventos', JSON.stringify(L.filter(x => x[0] === 'evento').map(x => x[1])));
  console.log('errores de página', errs.length, errs.slice(0, 3));
  console.log(fallas.length ? 'FALLAS ' + fallas.length + '\n' + fallas.join('\n') : 'puente bien, fallas 0');
  await b.close();
})();
