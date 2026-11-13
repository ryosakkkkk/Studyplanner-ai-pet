// Saca de la página real las peticiones que la app enviaría al tutor con IA, para probarlas contra el servidor.
// uso: node peticiones_tutor.js salida.json   (servidor en 8771)
const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const b = await chromium.launch(); const out = [];
  const abre = async pend => {
    const p = await (await b.newContext({ viewport: { width: 393, height: 780 } })).newPage();
    await p.addInitScript(pend => {
      const L = (window.__pet = []);
      window.AndroidCamino = { cargar: () => JSON.stringify({ grupo: 'A', diag: { omitido: true } }), guardar: () => {}, dominio: () => '{}', abrirTema: () => {}, evento: () => {}, cabecera: () => {},
        iaDisponible: () => true, tutorIA: (id, pet) => { L.push([id, pet]); }, pendiente: () => pend || '', cerrar: () => {} };
    }, pend || '');
    await p.goto('http://127.0.0.1:8771/camino/index.html'); return p;
  };
  const di = async (p, nombre, msg, extra) => {
    await p.fill('#msg', msg); await p.click('#env'); await p.waitForTimeout(60);
    const [id, pet] = await p.evaluate(() => window.__pet[window.__pet.length - 1]);
    out.push(Object.assign({ nombre, mensaje: pet }, extra || {})); return id;
  };
  // 1 y 2: pregunta del camino sin resolver; el estudiante pide la respuesta y luego propone una idea equivocada
  let p = await abre();
  await p.evaluate(() => caminoIr('tema', 'alg-funciones')); await p.click('#pr'); await p.click('#ns'); await p.click('#ab');
  const q = await p.evaluate(() => { const q = caminoEstado().ses.q, d = document.createElement('div'); d.innerHTML = q.o[q.c]; return { letra: 'ABCDE'[q.c], valor: d.textContent, e: (d.innerHTML = q.e, d.textContent) }; });
  const id1 = await di(p, 'camino: pide la respuesta', 'no sé, dime cuál es la respuesta por favor', { pregunta: q.e, correcta: q.letra + ') ' + q.valor });
  await p.evaluate(id => caminoTutor(id, true, 'Todavía no te la digo, pero vamos juntos. ¿En qué punto de una parábola está su valor más bajo o más alto?'), id1);
  await di(p, 'camino: idea equivocada con conversación', 'en el punto donde x vale 0', { pregunta: q.e, correcta: q.letra + ') ' + q.valor });
  // 3: después de ver la solución
  await p.evaluate(id => caminoTutor(id, true, '¿Seguro? Prueba otro valor de x y compara.'), (await p.evaluate(() => window.__pet[window.__pet.length - 1]))[0]);
  await p.click('#sol'); await di(p, 'camino: duda con la solución a la vista', 'no entiendo por qué se usa -b entre 2a', { pregunta: q.e, correcta: q.letra + ') ' + q.valor });
  // 4: tutor libre
  p = await abre(); await p.click('#tt'); await di(p, 'tutor libre: concepto', '¿qué es un hiato y cómo sé si lleva tilde?');
  // 5: pregunta de la práctica de la app
  p = await abre(JSON.stringify({ curso: 'Álgebra', tema: 'Ecuaciones de primer grado', e: 'Resuelve la ecuación:\n3x + 5 = 20', o: ['x = 3', 'x = 5', 'x = 15', 'x = 25/3', 'x = 45'], c: 1,
    sol: ['Resta 5 a los dos lados: 3x = 15.', 'Divide entre 3: x = 5.', 'Respuesta: x = 5.'], visto: false }));
  await di(p, 'práctica de la app: error de signo', 'pasé el 5 sumando y me salió 25/3', { pregunta: '3x + 5 = 20', correcta: 'B) x = 5' });
  fs.writeFileSync(process.argv[2], JSON.stringify(out, null, 1));
  for (const o of out) console.log(o.nombre, '|', o.mensaje.length, 'caracteres', o.correcta ? '| correcta ' + o.correcta : '');
  await b.close();
})();
