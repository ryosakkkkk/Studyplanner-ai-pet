// Prueba de la práctica con guía socrática y del tutor con IA (puente simulado, tamaño de teléfono).
// uso: node socratico_test.js [claro|oscuro]   (servidor en 8771). Deja capturas en cap/soc_*.png
const { chromium } = require('playwright');
const tema = process.argv[2] || 'claro', out = __dirname + '/cap';
(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 393, height: 780 }, deviceScaleFactor: 2 }); const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push('pageerror ' + e.message)); p.on('console', m => { if (m.type() === 'error') errs.push('console ' + m.text()); });
  await p.addInitScript(() => {
    const L = (window.__llamadas = []); let estado = JSON.stringify({ grupo: 'A', diag: { omitido: true } });
    window.AndroidCamino = {
      cargar: () => estado, guardar: (e) => { estado = e; }, dominio: () => '{}', cabecera: () => {}, abrirTema: () => {},
      evento: t => { L.push(['evento', t]); }, iaDisponible: () => !window.__sinIA,
      tutorIA: (id, pet) => { L.push(['tutorIA', id, pet]); },
    };
  });
  const fallas = []; const ok = (c, m) => { if (!c) fallas.push(m); };
  const cap = n => p.screenshot({ path: `${out}/soc_${tema}_${n}.png`, fullPage: true });
  const evs = async () => (await p.evaluate(() => window.__llamadas)).filter(x => x[0] === 'evento').map(x => x[1]);
  const limpia = () => p.evaluate(() => { window.__llamadas.length = 0; });
  const abre = async id => { await p.evaluate(() => { while (caminoAtras()) { const s = document.querySelector('[data-a="si"]'); if (s) s.click(); } }); await p.evaluate(id => caminoIr('tema', id), id); await p.click('#pr'); await p.waitForTimeout(60); };
  const c = () => p.evaluate(() => caminoEstado().ses.q.c);
  const otra = (c, mal) => [0, 1, 2, 3, 4].find(i => i !== c && !mal.includes(i));
  await p.goto('http://127.0.0.1:8771/camino/index.html' + (tema === 'oscuro' ? '?tema=oscuro' : ''));

  // 1. Tema con varios pasos: error -> guía sin revelar la respuesta -> pasos -> acierto con guía
  await abre('geo-paralelas'); await limpia();
  let ci = await c(); const m1 = otra(ci, []);
  await p.click('.opc >> nth=' + m1); await p.click('#resp'); await p.waitForTimeout(120);
  ok(!(await p.$('.opc.bien')), 'tras el primer error no debe verse la alternativa correcta');
  ok((await p.textContent('.veredicto')).includes('Todavía no te digo la respuesta'), 'falta el aviso de que no se da la respuesta');
  ok((await p.$$('.gq.actual')).length === 1 && !!(await p.$('#vp')), 'debe haber una pregunta de guía con su botón');
  ok((await p.$$('.guia .gp')).length === 0, 'ningún paso debe verse antes de pedirlo');
  ok(await p.$eval('.opc >> nth=' + m1, e => e.disabled && e.classList.contains('malo')), 'la alternativa fallada debe quedar descartada');
  ok(JSON.stringify(await evs()) === '["incorrecta"]', 'eventos tras el primer error: ' + JSON.stringify(await evs()));
  await cap('1_guia');
  const n = await p.evaluate(() => caminoEstado().ses.q.pasos.length - 1);
  for (let k = 0; k < n; k++) { await p.click('#vp'); await p.waitForTimeout(60); ok((await p.$$('.guia .gp')).length === k + 1, 'paso ' + (k + 1) + ' no apareció'); }
  ok(!(await p.$('#vp')) && (await p.textContent('.gq.actual')).includes('ya puedes terminar'), 'al terminar los pasos debe invitar a responder');
  await cap('2_guia_pasos');
  await p.click('.opc >> nth=' + ci); await p.click('#resp'); await p.waitForTimeout(120);
  ok((await p.textContent('.veredicto')).includes('Llegaste con la guía'), 'acierto con guía sin su mensaje');
  ok(JSON.stringify(await evs()) === '["incorrecta","guiada"]', 'eventos con guía: ' + JSON.stringify(await evs()));
  ok(await p.evaluate(() => { const r = caminoEstado().S.t['geo-paralelas'].r; return r[r.length - 1] === 0 && caminoEstado().ses.ok === 0 && caminoEstado().ses.guia === 1; }), 'el dominio debe contar el primer intento (fallado) y la sesión una con guía');
  await cap('3_con_guia');

  // 2. «No lo sé» y errores seguidos: se muestra la respuesta al agotar la guía (o al tercer error)
  await p.click('#sig'); await p.waitForTimeout(60); await limpia(); ci = await c();
  await p.click('#ns'); await p.waitForTimeout(80);
  ok((await p.textContent('.veredicto')).includes('Vamos paso a paso') && !(await p.$('.opc.bien')), '«No lo sé» debe abrir la guía sin la respuesta');
  const mal = []; let vueltas = 0;
  while (!(await p.$('.veredicto.no')) && vueltas++ < 6) { const x = otra(ci, mal); mal.push(x); await p.click('.opc >> nth=' + x); await p.click('#resp'); await p.waitForTimeout(80); }
  ok(mal.length <= 3 && (await p.textContent('.veredicto')).includes('La respuesta es la'), 'tras agotar la guía debe mostrarse la respuesta (errores: ' + mal.length + ')');
  ok(!!(await p.$('.opc.bien')) && !!(await p.$('.pasos')), 'al final se ve la correcta y la solución');
  ok((await evs())[0] === 'duda' && !(await evs()).includes('guiada'), 'eventos de «No lo sé»: ' + JSON.stringify(await evs()));

  // 3. Ver la solución desde la guía
  await p.click('#sig'); await p.waitForTimeout(60); await p.click('#ns'); await p.click('#sol'); await p.waitForTimeout(80);
  ok(!!(await p.$('.pasos')) && !!(await p.$('#sig')), '«Ver la solución» debe mostrarla');

  // 4. Tema de preguntas fijas: una pregunta de guía por intento, sin pasos
  await abre('lit-peruana'); ci = await c(); const f = [];
  for (let k = 1; k <= 2; k++) { const x = otra(ci, f); f.push(x); await p.click('.opc >> nth=' + x); await p.click('#resp'); await p.waitForTimeout(80);
    ok((await p.$$('.guia .gq')).length === k && !(await p.$('#vp')) && !(await p.$('.opc.bien')), `preguntas fijas: tras ${k} error(es) deben verse ${k} pregunta(s) de guía`); if (k === 1) await cap('4_fija_guia'); }
  await p.click('.opc >> nth=' + ci); await p.click('#resp'); await p.waitForTimeout(80);
  ok((await p.textContent('.veredicto')).includes('Llegaste con la guía') && !!(await p.$('.uno')), 'pregunta fija resuelta con guía');

  // 5. Acierto a la primera: sin guía
  await abre('rm-calendario'); await limpia(); ci = await c();
  await p.click('.opc >> nth=' + ci); await p.click('#resp'); await p.waitForTimeout(80);
  ok((await p.textContent('.veredicto')).trim() === 'Correcto' && !(await p.$('.guia')) && JSON.stringify(await evs()) === '["correcta"]', 'acierto a la primera');

  // 6. Tutor con IA dentro de una pregunta
  await abre('alg-funciones'); await limpia(); ci = await c();
  await p.click('.opc >> nth=' + otra(ci, [])); await p.click('#resp'); await p.waitForTimeout(80);
  ok(!!(await p.$('#ab')), 'con la IA activa debe ofrecer conversar con el tutor');
  await p.click('#ab'); await p.waitForTimeout(60);
  await p.fill('#msg', 'creo que primero\n  saco el vértice   con -b/2a'); await p.click('#env'); await p.waitForTimeout(80);
  let L = await p.evaluate(() => window.__llamadas); let t = L.find(x => x[0] === 'tutorIA');
  ok(!!t && typeof t[1] === 'string' && /^[a-z0-9]+$/.test(t[1]), 'no llamó a tutorIA con un identificador simple');
  const pet = t ? t[2] : '';
  ok(pet.length > 400 && pet.length <= 3600 && !/[\u0000-\u001f]/.test(pet), 'petición fuera de tamaño o con saltos de línea: ' + pet.length);
  ok(pet.includes('método socrático') && pet.includes('no digas la respuesta') && pet.includes('Respuesta correcta:') && pet.includes('«creo que primero saco el vértice con -b/2a»') && pet.includes('Solución de referencia:'), 'la petición no trae lo esperado');
  ok(!!(await p.$('.bur.esp')) && await p.$eval('#env', e => e.disabled), 'mientras espera debe mostrar «Pensando» y bloquear el envío');
  await p.evaluate(id => caminoTutor('otro' + id, true, 'respuesta vieja', ''), t[1]);
  ok(!(await p.textContent('.hilo')).includes('respuesta vieja'), 'una respuesta con otro identificador no debe mostrarse');
  await p.evaluate(id => caminoTutor(id, true, 'Vas bien con la fórmula. ¿Cuánto valen a y b en esta parábola?', 'Generado por IA — puede contener errores'), t[1]); await p.waitForTimeout(80);
  ok((await p.$$('.bur.yo')).length === 1 && (await p.$$('.bur.tu:not(.esp)')).length === 1 && (await p.textContent('.hilo')).includes('¿Cuánto valen a y b'), 'la respuesta del tutor no aparece');
  ok(!(await p.$('.opc.bien')), 'conversar con el tutor no debe revelar la respuesta');
  await cap('5_tutor');
  await p.fill('#msg', 'a vale 1'); await p.click('#env'); await p.waitForTimeout(60);
  L = await p.evaluate(() => window.__llamadas); t = L.filter(x => x[0] === 'tutorIA')[1];
  ok(!!t && t[2].includes('Conversación hasta ahora:') && t[2].includes('Tutor: Vas bien') && t[2].includes('«a vale 1»'), 'la segunda petición no lleva la conversación');
  await p.evaluate(id => caminoTutor(id, false, '', ''), t[1]); await p.waitForTimeout(60);
  ok(!!(await p.$('.bur.err')) && !(await p.$eval('#env', e => e.disabled)), 'si falla la IA debe avisar y dejar reintentar');
  await p.click('#sol'); await p.waitForTimeout(60);
  await p.fill('#msg', 'no entiendo el paso 2'); await p.click('#env'); await p.waitForTimeout(60);
  L = await p.evaluate(() => window.__llamadas); t = L.filter(x => x[0] === 'tutorIA')[2];
  ok(!!t && t[2].includes('ya vio la solución') && !t[2].includes('No pude conectar'), 'después de ver la solución cambian las reglas y no se envían los avisos de error');
  await p.evaluate(id => caminoTutor(id, true, 'En el paso 2 se reemplaza la x del vértice en la función.', ''), t[1]); await p.waitForTimeout(60);

  // 7. Tutor libre desde el camino
  await p.evaluate(() => { while (caminoAtras()) { const s = document.querySelector('[data-a="si"]'); if (s) s.click(); } });
  ok(!!(await p.$('#tt')), 'el camino debe ofrecer el tutor cuando hay IA');
  await p.click('#tt'); await p.waitForTimeout(60); await limpia();
  await p.fill('#msg', '¿qué es un hiato?'); await p.click('#env'); await p.waitForTimeout(60);
  L = await p.evaluate(() => window.__llamadas); t = L.find(x => x[0] === 'tutorIA');
  ok(!!t && t[2].includes('método socrático') && t[2].includes('«¿qué es un hiato?»') && !t[2].includes('Respuesta correcta'), 'petición del tutor libre');
  await p.evaluate(id => caminoTutor(id, true, 'Un hiato son dos vocales juntas que se pronuncian en sílabas distintas, como en «po-e-ta». ¿Cómo separarías «país»?', ''), t[1]); await p.waitForTimeout(60);
  await cap('6_tutor_libre');
  ok((await p.evaluate(() => caminoAtras())) === true && !!(await p.$('.portada')), 'atrás desde el tutor vuelve al camino');

  // 8. Sin IA: no aparecen las entradas al tutor y la guía sigue funcionando
  await p.evaluate(() => { window.__sinIA = true; caminoIr('camino'); }); await p.waitForTimeout(60);
  ok(!(await p.$('#tt')), 'sin IA el camino no debe ofrecer el tutor');
  await abre('ari-divisores'); ci = await c(); await p.click('.opc >> nth=' + otra(ci, [])); await p.click('#resp'); await p.waitForTimeout(80);
  ok(!!(await p.$('.guia')) && !(await p.$('#ab')) && !(await p.$('.chat')), 'sin IA: guía sí, conversación no');

  // 9. Sesión completa: el resumen separa «a la primera» de «con guía»
  await p.evaluate(() => { window.__sinIA = false; }); await abre('rm-edades');
  for (let i = 0; i < 5; i++) { ci = await c(); if (i === 1) { await p.click('.opc >> nth=' + otra(ci, [])); await p.click('#resp'); await p.waitForTimeout(40); } await p.click('.opc >> nth=' + ci); await p.click('#resp'); await p.waitForTimeout(40); await p.click('#sig'); await p.waitForTimeout(40); }
  const fin = await p.textContent('#app');
  ok(fin.includes('4 de 5 a la primera') && fin.includes('Una más la sacaste con la guía'), 'resumen de la sesión: ' + fin.slice(0, 120));
  await cap('7_fin');
  const w = await p.evaluate(() => ({ sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth }));
  ok(w.sw <= w.cw + 1, 'desborde horizontal');
  console.log('errores de página', errs.length, errs.slice(0, 3));
  console.log(fallas.length || errs.length ? 'FALLAS ' + fallas.length + '\n' + fallas.join('\n') : 'socrático bien, fallas 0');
  await b.close();
})();
