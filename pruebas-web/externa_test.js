// Prueba de la pregunta que llega desde la práctica de la app (AndroidCamino.pendiente): se abre directo la
// conversación con el tutor, la petición lleva la pregunta y la solución, y «atrás» sale de la página.
// uso: node externa_test.js [claro|oscuro]   (servidor en 8771). Deja capturas en cap/ext_*.png
const { chromium } = require('playwright');
const tema = process.argv[2] || 'claro', out = __dirname + '/cap';
// El mismo JSON que escribe TutorBrief.toJson() en Kotlin.
const PREG = { curso: 'Álgebra', tema: 'Ecuaciones lineales', e: 'Resuelve la ecuación:\n3x + 5 = 20', o: ['x = 3', 'x = 5', 'x = 15', 'x = 25/3', 'x = 45'], c: 1,
  sol: ['Resta 5 a los dos lados: 3x = 15.', 'Divide entre 3: x = 5.', 'Respuesta: x = 5.'], visto: false };
(async () => {
  const b = await chromium.launch(); const fallas = []; const ok = (c, m) => { if (!c) fallas.push(m); }; const errs = [];
  const abre = async (pend, ia) => {
    const ctx = await b.newContext({ viewport: { width: 393, height: 780 }, deviceScaleFactor: 2 }); const p = await ctx.newPage();
    p.on('pageerror', e => errs.push('pageerror ' + e.message)); p.on('console', m => { if (m.type() === 'error') errs.push('console ' + m.text()); });
    await p.addInitScript(([pend, ia]) => {
      const L = (window.__llamadas = []);
      window.AndroidCamino = {
        cargar: () => JSON.stringify({ grupo: 'A', diag: { omitido: true } }), guardar: () => {}, dominio: () => '{}', abrirTema: () => {}, evento: () => {},
        cabecera: (t, f) => { L.push(['cabecera', t, f]); }, iaDisponible: () => ia, tutorIA: (id, pet) => { L.push(['tutorIA', id, pet]); },
        pendiente: () => { if (sessionStorage.getItem('dada')) return ''; sessionStorage.setItem('dada', '1'); return pend; }, cerrar: () => { L.push(['cerrar']); },
      };
    }, [pend, ia]);
    await p.goto('http://127.0.0.1:8771/camino/index.html' + (tema === 'oscuro' ? '?tema=oscuro' : ''));
    return p;
  };
  const L = p => p.evaluate(() => window.__llamadas);

  // 1. Pregunta abierta (todavía sin resolver)
  let p = await abre(JSON.stringify(PREG), true);
  ok((await p.evaluate(() => caminoEstado().pila.join())) === 'externa', 'debe abrir directo la pregunta que llegó de la app');
  let t = await p.textContent('#app');
  ok(t.includes('3x + 5 = 20') && t.includes('x = 25/3') && t.includes('El tutor no te da la respuesta'), 'no se ve la pregunta con sus alternativas');
  ok(!(await p.$('.alt.bien')), 'sin resolver no debe marcarse la alternativa correcta');
  ok((await p.$eval('.enun', e => getComputedStyle(e).whiteSpace)) === 'pre-line', 'el enunciado debe respetar sus saltos de línea');
  ok((await L(p)).some(x => x[0] === 'cabecera' && x[1] === 'Tutor · Ecuaciones lineales'), 'falta el título en la barra');
  await p.fill('#msg', 'pasé el 5 sumando y me salió 25/3'); await p.click('#env'); await p.waitForTimeout(80);
  let c = (await L(p)).find(x => x[0] === 'tutorIA');
  ok(!!c && c[2].includes('Curso: Álgebra. Tema: Ecuaciones lineales.') && c[2].includes('«Resuelve la ecuación: 3x + 5 = 20»') && c[2].includes('Respuesta correcta: B) x = 5.') && c[2].includes('Solución de referencia: 1. Resta 5') && c[2].includes('no digas la respuesta') && c[2].includes('«pasé el 5 sumando y me salió 25/3»'), 'petición del tutor incompleta: ' + (c ? c[2].slice(0, 300) : 'no hubo'));
  ok(!!c && c[2].length <= 3800 && !/[\u0000-\u001f]/.test(c[2]), 'la petición debe ir en una línea y con tope');
  ok((await p.textContent('.bur.esp')).trim() === 'Pensando…', 'al enviar debe verse «Pensando…»');
  await p.waitForTimeout(9300);   // el servidor puede tardar: a los 9 segundos se avisa y se sigue esperando
  ok((await p.textContent('.bur.esp')).includes('cerca de medio minuto') && await p.$eval('#env', e => e.disabled), 'si tarda debe avisar que sigue pensando');
  await p.evaluate(id => caminoTutor(id, true, 'Fíjate en el 5: está sumando a la izquierda. ¿Con qué operación pasa al otro lado?'), c[1]); await p.waitForTimeout(80);
  ok((await p.textContent('.hilo')).includes('¿Con qué operación pasa al otro lado?'), 'la respuesta del tutor no aparece');
  await p.screenshot({ path: `${out}/ext_${tema}_1_abierta.png`, fullPage: true });
  ok((await p.evaluate(() => caminoAtras())) === false, '«atrás» debe salir de la página (vuelve a la práctica de la app)');
  await p.click('#vol'); ok((await L(p)).some(x => x[0] === 'cerrar'), '«Volver a mi práctica» debe pedir el cierre');
  const w = await p.evaluate(() => ({ sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth })); ok(w.sw <= w.cw + 1, 'desborde horizontal');
  // al recargar (la app ya entregó la pregunta) se vuelve al camino normal
  await p.reload(); ok((await p.evaluate(() => caminoEstado().pila.join())) === 'camino', 'sin pregunta pendiente se abre el camino');
  await p.context().close();

  // 2. Pregunta ya resuelta: marca la correcta y cambian las reglas del tutor
  p = await abre(JSON.stringify({ ...PREG, visto: true }), true);
  ok(!!(await p.$('.alt.bien')) && (await p.textContent('.alt.bien')).includes('x = 5'), 'con la solución vista debe marcarse la correcta');
  await p.fill('#msg', '¿por qué se divide entre 3?'); await p.click('#env'); await p.waitForTimeout(60);
  c = (await L(p)).find(x => x[0] === 'tutorIA');
  ok(!!c && c[2].includes('ya vio la solución') && !c[2].includes('no digas la respuesta'), 'reglas para la pregunta ya resuelta');
  await p.screenshot({ path: `${out}/ext_${tema}_2_vista.png`, fullPage: true });
  await p.context().close();

  // 3. Datos dañados o incompletos: se ignoran y se abre el camino normal
  for (const malo of ['{', '{"e":"x","o":["a"],"c":0}', '{"e":"","o":["a","b"],"c":0}', '{"e":"x","o":["a","b"],"c":7}', '[]', 'null']) {
    p = await abre(malo, true);
    ok((await p.evaluate(() => caminoEstado().pila.join())) === 'camino', 'datos malos deben ignorarse: ' + malo);
    await p.context().close();
  }
  // 4. Texto con etiquetas: se muestra como texto, no como HTML
  p = await abre(JSON.stringify({ ...PREG, e: 'Si a < b y b > c, <img src=x onerror=alert(1)> ¿qué pasa?', o: ['<b>uno</b>', 'dos', 'tres', 'cuatro', 'cinco'] }), true);
  ok(!(await p.$('#app img')) && !(await p.$('.alt b b')) && (await p.textContent('#app')).includes('<img src=x'), 'el texto de la pregunta no debe interpretarse como HTML');
  await p.context().close();
  // 5. Sin IA
  p = await abre(JSON.stringify(PREG), false);
  ok((await p.textContent('#app')).includes('El tutor necesita Internet y la IA activa') && !(await p.$('#msg')), 'sin IA debe avisar');
  await p.context().close();

  console.log('errores de página', errs.length, errs.slice(0, 3));
  console.log(fallas.length || errs.length ? 'FALLAS ' + fallas.length + '\n' + fallas.join('\n') : 'pregunta de la app bien, fallas 0');
  await b.close();
})();
