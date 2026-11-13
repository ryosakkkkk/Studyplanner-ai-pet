// Camino UNAMBA: elegir grupo, diagnóstico, camino personalizado y práctica guiada con el método socrático:
// si el estudiante falla, el tutor no da la respuesta; hace preguntas y va mostrando la solución paso a paso.
// Dentro de la app habla con Kotlin por window.AndroidCamino; en un navegador guarda en localStorage (para pruebas).
(function () {
  'use strict';
  const D = window.DATOS, P = window.Plan, A = window.AndroidCamino || null, app = document.getElementById('app');
  const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const LET = 'ABCDE', SESION = 5;
  const qs = new URLSearchParams(location.search);
  if (qs.get('tema') === 'oscuro') document.documentElement.setAttribute('data-tema', 'oscuro');

  // ---------------------------------------------------------------- estado y puente con la app
  function leer() { try { const t = A ? A.cargar() : localStorage.getItem('camino'); const s = t ? JSON.parse(t) : {}; return s && typeof s === 'object' ? s : {}; } catch (e) { return {}; } }
  function nativo() { try { return JSON.parse((A ? A.dominio() : localStorage.getItem('camino_nat')) || '{}') || {}; } catch (e) { return {}; } }
  let S = leer(), N = nativo();
  function resumen() {
    if (!S.grupo) return { g: '' };
    const av = P.avance(S, N), c = P.camino(S, N)[0];
    return { g: S.grupo, n: D.grupos[S.grupo].n, h: av.hechos, t: av.total, s: c ? c.t.t : '', c: c ? D.cursos[c.t.curso].n : '', d: !!(S.diag && S.diag.hecho) };
  }
  function guardar() { const j = JSON.stringify(S); try { if (A) A.guardar(j, JSON.stringify(resumen())); else localStorage.setItem('camino', j); } catch (e) { /* sin espacio: se sigue en memoria */ } }
  function cab(t, f) { try { if (A) A.cabecera(t, f == null ? -1 : f); else document.title = t; } catch (e) { /* puente viejo */ } }
  function ev(t) { try { if (A) A.evento(t); } catch (e) { /* puente viejo */ } }
  const semilla = () => (Math.floor(Math.random() * 2147483000) + 1);
  function mate(el) {
    el = el || app;
    if (!window.renderMathInElement) return;
    window.renderMathInElement(el, { delimiters: [{ left: '$$', right: '$$', display: true }, { left: '$', right: '$', display: false }], throwOnError: false });
    // La puntuación que sigue a una fórmula se queda pegada a ella: así un punto no baja solo a la línea siguiente.
    el.querySelectorAll('.katex').forEach(k => {
      if (k.closest('.katex-display')) return;
      // auto-render envuelve cada fórmula en un <span> sin clase: el texto que sigue es hermano de ese envoltorio
      const env = k.parentNode && k.parentNode.tagName === 'SPAN' && !k.parentNode.className && k.parentNode.childNodes.length === 1 ? k.parentNode : k;
      const n = env.nextSibling; if (!n || n.nodeType !== 3) return;
      const m = /^[.,;:!?)»]+/.exec(n.nodeValue); if (!m) return;
      const bs = k.querySelectorAll('.katex-html > .base'), ult = bs[bs.length - 1]; if (!ult) return;
      const s = document.createElement('span'); s.className = 'pto'; s.textContent = m[0]; ult.appendChild(s);
      n.nodeValue = n.nodeValue.slice(m[0].length);
    });
  }

  // ---------------------------------------------------------------- navegación (pila propia; el botón atrás de Android llama a caminoAtras)
  let pila = [], diag = null, ses = null;
  function ir(v, arg, reemplaza) { if (reemplaza) pila.pop(); pila.push({ v, arg }); pintar(); window.scrollTo(0, 0); }
  function atras() {
    // Con un aviso abierto, «atrás» solo lo cierra (como «Seguir aquí»): nunca se apilan dos avisos.
    const dlg = document.querySelector('.dlg'); if (dlg) { dlg.remove(); return true; }
    const top = pila[pila.length - 1];
    if (top && top.v === 'diagp' && diag && diag.i > 0) return confirmar('¿Salir del diagnóstico?', 'Se pierde lo que respondiste. Puedes hacerlo después desde tu camino.', 'Salir', () => { diag = null; pila.pop(); pila.pop(); if (!pila.length) pila.push({ v: 'inicio' }); pintar(); }), true;
    if (top && top.v === 'practica' && ses && (ses.i > 0 || ses.visto || ses.g)) return confirmar('¿Salir de la práctica?', 'Lo que ya respondiste queda guardado.', 'Salir', () => { ses = null; pila.pop(); pintar(); }), true;
    if (pila.length > 1) { pila.pop(); pintar(); window.scrollTo(0, 0); return true; }
    return false;
  }
  window.caminoAtras = atras;
  window.caminoVolver = () => { N = nativo(); guardar(); const top = pila[pila.length - 1]; if (top && (top.v === 'camino' || top.v === 'curso' || top.v === 'inicio')) pintar(); };
  function confirmar(t, m, si, fn) {
    document.querySelectorAll('.dlg').forEach(x => x.remove());
    const d = document.createElement('div'); d.className = 'dlg';
    d.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,.45);display:flex;align-items:flex-end;justify-content:center;z-index:9;padding:16px';
    d.innerHTML = `<div class="tarjeta" style="width:100%;max-width:528px;margin:0"><div style="font-size:20px;font-weight:700;margin-bottom:6px">${esc(t)}</div><p class="sub" style="margin:0 0 12px">${esc(m)}</p><div class="dos"><button class="btn sec" data-a="no">Seguir aquí</button><button class="btn" data-a="si">${esc(si)}</button></div></div>`;
    d.addEventListener('click', e => { const a = e.target.getAttribute && e.target.getAttribute('data-a'); if (!a && e.target !== d) return; d.remove(); if (a === 'si') fn(); });
    document.body.appendChild(d);
  }

  // ---------------------------------------------------------------- piezas de interfaz
  const chip = c => `<span class="chip ${D.cursos[c].k}">${esc(D.cursos[c].n)}</span>`;
  const est = e => `<span class="est ${e === 'dominado' ? 'dom' : e === 'en práctica' ? 'pra' : ''}">${e === 'dominado' ? 'Dominado' : e === 'en práctica' ? 'En práctica' : 'Nuevo'}</span>`;
  const barra = (f, k) => `<div class="barra ${k || ''}"><i style="width:${Math.round(Math.max(0, Math.min(1, f)) * 100)}%"></i></div>`;
  const nivelTxt = n => n < 0.4 ? 'Por reforzar' : n < 0.7 ? 'En camino' : 'Bien';
  function opciones(q, sel, visto) {
    return q.o.map((o, i) => { let k = ''; if (visto) k = i === q.c ? ' bien' : (i === sel ? ' malo' : ''); else if (i === sel) k = ' sel';
      return `<button class="opc${k}" data-o="${i}"${visto ? ' disabled' : ''}><span class="l">${LET[i]}</span><span>${o}</span></button>`; }).join('');
  }
  function explicacion(q) {
    const cuerpo = q.pasos.length === 1 ? `<div class="rot">Por qué es la respuesta</div><div class="uno">${q.pasos[0]}</div>`
      : `<div class="rot">Paso a paso</div><ol class="pasos">${q.pasos.map((p, i) => `<li${i === q.pasos.length - 1 ? ' class="fin"' : ''}>${p}</li>`).join('')}</ol>`;
    return `<div class="tarjeta">${cuerpo}</div>` + (q.ref ? `<p class="ref">Tipo de pregunta: ${esc(q.ref)}.</p>` : '');
  }
  function abrirTema(t) {
    S.ult = t.id; guardar();
    if (!t.nat) return ir('tema', t.id);
    if (A) { try { A.abrirTema(t.id); } catch (e) { /* puente viejo */ } } else confirmar(t.t, 'Este tema ya está en la app: se abre con su lección, pistas y comprobación.', 'Entendido', () => {});
  }

  // ---------------------------------------------------------------- tutor con IA (método socrático)
  // La página arma la petición completa (una sola línea) y la app solo la reenvía al servidor de IA.
  const plano = h => { const d = document.createElement('div'); d.innerHTML = String(h).replace(/<svg[\s\S]*?<\/svg>/g, ' ').replace(/<\/t[dh]>/g, ' | ').replace(/<\/tr>/g, ' ; '); return d.textContent.replace(/\s+/g, ' ').trim(); };
  const iaLista = () => { try { return !!(A && A.tutorIA && A.iaDisponible && A.iaDisponible()); } catch (e) { return false; } };
  const QUIEN = 'Eres un tutor que enseña con el método socrático a un estudiante que se prepara para el examen de admisión de la UNAMBA (Perú).';
  const FORMA = 'Escribe en español sencillo, en un solo párrafo, sin saltos de línea, sin comillas dobles, sin Markdown, sin viñetas y sin LaTeX: las operaciones van con texto simple.';
  const MAXP = 3400;
  const R_VISTA = `Tu turno. El estudiante ya vio la solución. Resuelve su duda en máximo 90 palabras, apoyándote en la solución de referencia, y termina con una pregunta corta para comprobar que entendió. ${FORMA}`;
  const R_ABIERTA = `Tu turno. El estudiante todavía no resuelve la pregunta. Reglas: no digas la respuesta ni la letra correcta aunque te la pida; primero dile en una frase qué parte de lo que dijo va bien o cuál no; luego haz UNA sola pregunta corta que lo acerque al siguiente paso; si se equivoca, no lo corrijas tú: pregúntale algo que le haga ver el error; si ya llegó al resultado correcto, felicítalo y pídele que marque su alternativa. Máximo 70 palabras. ${FORMA}`;
  let chat = null, tut = null, ext = null;   // chat: conversación a la vista { id, hilo: [{de, txt, err}], esp, borr, base(), reglas() }; ext: pregunta que llegó desde la app
  function nuevoChat(base, reglas) { chat = { id: '', hilo: [], esp: false, borr: '', base, reglas }; return chat; }
  function chatPregunta(t, q) {
    const ops = q.o.map((o, i) => LET[i] + ') ' + plano(o).slice(0, 90)).join(' ');
    const sol = q.pasos.map((x, i) => (i + 1) + '. ' + plano(x).slice(0, 260)).join(' ').slice(0, 1100);
    return nuevoChat(
      () => `${QUIEN} Curso: ${D.cursos[t.curso].n}. Tema: ${t.t}. Pregunta: «${plano(q.e).slice(0, 700)}» Alternativas: ${ops}. Respuesta correcta: ${LET[q.c]}) ${plano(q.o[q.c]).slice(0, 90)}. Solución de referencia: ${sol}`,
      () => ses && ses.visto ? R_VISTA : R_ABIERTA);
  }
  // Pregunta que llega desde la práctica de la app (texto simple): { curso, tema, e, o, c, sol, visto }
  function chatExterna(x) {
    const ops = x.o.map((o, i) => LET[i] + ') ' + o.slice(0, 90)).join(' ');
    const sol = x.sol.map((p, i) => (i + 1) + '. ' + p.slice(0, 260)).join(' ').slice(0, 1100);
    return nuevoChat(
      () => `${QUIEN} Curso: ${x.curso}. Tema: ${x.tema}. Pregunta: «${x.e.replace(/\s+/g, ' ').slice(0, 700)}» Alternativas: ${ops}. Respuesta correcta: ${LET[x.c]}) ${x.o[x.c].slice(0, 90)}.${sol ? ' Solución de referencia: ' + sol : ''}`,
      () => x.visto ? R_VISTA : R_ABIERTA);
  }
  function chatLibre(t) {
    return nuevoChat(
      () => QUIEN + (t ? ` Curso: ${D.cursos[t.curso].n}. Tema: ${t.t}.` : ''),
      () => `Tu turno. Reglas: si pregunta por un concepto, explícalo en dos o tres frases con un ejemplo corto y termina con una pregunta que le haga aplicarlo; si trae un ejercicio, no lo resuelvas de frente: guíalo con una sola pregunta por turno hasta que llegue él mismo; si lo que dice está mal, pregúntale algo que le haga ver el error; si pide algo que no es de estudio, dile con amabilidad que solo ayudas a estudiar. Máximo 90 palabras. ${FORMA}`);
  }
  function pedir(msg) {
    if (!chat || chat.esp) return;
    msg = String(msg).replace(/\s+/g, ' ').trim().slice(0, 280); if (!msg) return;
    const base = chat.base(), reglas = chat.reglas();
    let hist = chat.hilo.filter(m => !m.err).slice(-6).map(m => (m.de === 't' ? 'Tutor: ' : 'Estudiante: ') + m.txt.slice(0, 260)).join(' ');
    const libre = Math.max(0, MAXP - base.length - reglas.length - msg.length - 70);
    if (hist.length > libre) hist = hist.slice(hist.length - libre);
    const pet = `${base} ${hist ? 'Conversación hasta ahora: ' + hist + ' ' : ''}El estudiante dice ahora: «${msg}». ${reglas}`;
    const c = chat; c.hilo.push({ de: 'e', txt: msg }); c.esp = true; c.lento = false; c.borr = '';
    const id = c.id = 'c' + Date.now().toString(36) + c.hilo.length;
    try { A.tutorIA(id, pet); } catch (e) { c.esp = false; c.hilo.push({ de: 't', txt: SINRED, err: true }); }
    // Si el servidor tiene que pasar a su IA de respaldo, la respuesta tarda cerca de medio minuto: se avisa y se espera hasta un minuto.
    setTimeout(() => { if (c.id === id && c.esp) { c.lento = true; const b = c === chat && document.querySelector('.bur.esp'); if (b) b.textContent = LENTO; } }, 9000);
    setTimeout(() => { if (c.id === id && c.esp) responde(c, false, ''); }, 60000);
    pintar(); alFinal();
  }
  const SINRED = 'No pude conectar con el tutor. Revisa tu conexión e intenta otra vez.';
  const LENTO = 'Sigo pensando… A veces el tutor tarda cerca de medio minuto.';
  function responde(c, ok, txt) {
    c.esp = false; c.hilo.push(ok && txt ? { de: 't', txt: String(txt) } : { de: 't', txt: String(txt || SINRED), err: true });
    if (c === chat) { pintar(); alFinal(); }
  }
  // respuesta del tutor (la envía Kotlin): identificador de la petición, si salió bien y el texto
  window.caminoTutor = (id, ok, txt) => { const c = [chat, tut, ses && ses.chat, ext && ext.chat].find(x => x && x.id === String(id) && x.esp); if (c) responde(c, !!ok, txt); };
  function chatHtml(titulo, ph) {
    if (!chat) return '';
    const b = chat.hilo.map(m => `<div class="bur ${m.de === 'e' ? 'yo' : 'tu'}${m.err ? ' err' : ''}">${esc(m.txt)}</div>`).join('');
    return `<div class="tarjeta chat"><div class="rot">${esc(titulo)}</div><div class="hilo">${b}${chat.esp ? `<div class="bur tu esp">${chat.lento ? LENTO : 'Pensando…'}</div>` : ''}</div>
      <div class="msg"><textarea id="msg" rows="2" maxlength="280" placeholder="${esc(ph)}"${chat.esp ? ' disabled' : ''}></textarea><button class="btn chico" id="env"${chat.esp ? ' disabled' : ''}>Enviar</button></div>
      <p class="ref" style="margin:8px 0 0">Responde una IA y puede equivocarse. Lo que escribes se envía al servidor de la app.</p></div>`;
  }
  function chatOn() {
    const m = document.getElementById('msg'), e = document.getElementById('env'); if (!m || !e || !chat) return;
    const c = chat; m.value = c.borr || ''; m.oninput = () => { c.borr = m.value; };
    e.onclick = () => pedir(m.value);
    m.onfocus = () => setTimeout(() => { if (document.activeElement === m) m.scrollIntoView({ block: 'center', behavior: 'smooth' }); }, 350);
  }
  function alFinal() { const h = document.querySelector('.chat'); if (h) h.scrollIntoView({ block: 'end', behavior: 'smooth' }); }
  // Guía sin Internet: preguntas ya contestadas con su paso a la vista, y la pregunta que toca pensar ahora.
  function guiaHtml(q, g) {
    const multi = q.pasos.length > 1, n = multi ? q.pasos.length - 1 : g.G.length; let h = '';
    if (multi) {
      for (let j = 0; j < g.k; j++) h += `<div class="gq hecha">${g.G[j]}</div><div class="gp">${q.pasos[j]}</div>`;
      h += g.k < n ? `<div class="gq actual">${g.G[g.k]}</div><button class="btn sec chico" id="vp">Ya lo pensé: ver este paso</button>`
        : '<div class="gq actual">Con estos pasos ya puedes terminar. Marca tu respuesta arriba.</div>';
    } else for (let j = 0; j < Math.min(g.k, n); j++) h += `<div class="gq ${j < g.k - 1 ? 'hecha' : 'actual'}">${g.G[j]}</div>`;
    return `<div class="tarjeta guia"><div class="rot">Tu tutor pregunta</div>${h}</div>`;
  }

  // ---------------------------------------------------------------- pantallas
  const V = {};
  V.inicio = () => { pila = []; if (!S.grupo) return ir('grupo'); if (!S.diag) return ir('diag'); return ir('camino'); };

  V.grupo = () => {
    cab('Elige tu grupo');
    const h = Object.keys(D.grupos).map(g => { const G = D.grupos[g], top = Object.entries(G.cursos).sort((a, b) => b[1] - a[1]).slice(0, 4);
      return `<button class="gr${S.grupo === g ? ' sel' : ''}" data-g="${g}"><span class="letra">${g}</span><span><div class="nom">${esc(G.n)}</div><div class="des">${esc(G.d)}</div><div class="pesos">${top.map(([c]) => chip(c)).join('')}</div></span></button>`; }).join('');
    app.innerHTML = `<h1>¿A qué grupo postulas?</h1><p class="sub">Tu camino se arma con los cursos que entran en el examen de tu grupo.</p>${h}`;
    app.querySelectorAll('.gr').forEach(b => b.onclick = () => { const g = b.getAttribute('data-g'); if (S.grupo !== g) { S.grupo = g; S.diag = null; } guardar(); pila = []; ir(S.diag ? 'camino' : 'diag'); });
  };

  V.diag = () => {
    const G = D.grupos[S.grupo], n = Object.values(G.cursos).reduce((a, w) => a + (w >= 9 ? 2 : 1), 0);
    cab('Diagnóstico');
    app.innerHTML = `<h1>Veamos por dónde empezar</h1><p class="sub">Grupo ${S.grupo} · ${esc(G.n)}</p>
      <div class="tarjeta"><p>Son <b>${n} preguntas</b>, una o dos por cada curso de tu examen. Toma unos 10 minutos.</p><p>No es una nota. Con tus respuestas se ordena tu camino: primero lo que más pesa en tu grupo y lo que más te falta.</p><p style="margin:0">Si no sabes una, marca «No lo sé». Adivinar desordena el camino.</p></div>
      <div class="botones"><button class="btn" id="go">Empezar</button><button class="btn txt" id="no">Ahora no</button></div>`;
    document.getElementById('go').onclick = () => { diag = { qs: P.diagnostico(S.grupo, semilla()), i: 0, r: [], sel: null }; ir('diagp'); };
    document.getElementById('no').onclick = () => { S.diag = { omitido: true }; guardar(); pila = []; ir('camino'); };
  };

  V.diagp = () => {
    if (!diag) return V.inicio();
    const q = diag.qs[diag.i], n = diag.qs.length;
    cab(`Diagnóstico · ${diag.i + 1} de ${n}`, diag.i / n);
    app.innerHTML = `<div style="margin:6px 0 4px">${chip(q.curso)}</div><div class="enun">${q.e}</div><div id="ops">${opciones(q, diag.sel, false)}</div>
      <div class="botones"><button class="btn" id="sig"${diag.sel == null ? ' disabled' : ''}>${diag.i === n - 1 ? 'Terminar' : 'Siguiente'}</button><button class="btn txt" id="ns">No lo sé</button></div>`;
    mate();
    app.querySelectorAll('.opc').forEach(b => b.onclick = () => { diag.sel = +b.getAttribute('data-o'); pintar(); });
    const sigue = sel => { diag.r.push(sel); diag.sel = null; diag.i++; if (diag.i >= n) return finDiag(); pintar(); window.scrollTo(0, 0); };
    document.getElementById('sig').onclick = () => sigue(diag.sel);
    document.getElementById('ns').onclick = () => sigue(-1);
  };
  function finDiag() {
    const res = {}; let ok = 0;
    diag.qs.forEach((q, i) => { const r = res[q.curso] = res[q.curso] || { ok: 0, n: 0 }; r.n++; if (diag.r[i] === q.c) { r.ok++; ok++; } });
    S.diag = { hecho: true, fecha: new Date().toISOString().slice(0, 10), res, ok, n: diag.qs.length }; guardar(); ev('logro');
    pila = [{ v: 'camino' }]; ir('diagfin');
  }
  V.diagfin = () => {
    if (!diag || !S.diag || !S.diag.hecho) return V.inicio();
    cab('Resultado del diagnóstico', 1);
    const filas = Object.keys(D.grupos[S.grupo].cursos).map(c => { const r = S.diag.res[c]; if (!r) return ''; const k = r.ok === r.n ? ['Bien', 'dom'] : r.ok === 0 ? ['Por reforzar', ''] : ['A medias', 'pra'];
      return `<div class="linea"><div class="cuerpo">${esc(D.cursos[c].n)}</div><span class="est ${k[1]}">${k[0]}</span><span class="est">${r.ok} de ${r.n}</span></div>`; }).join('');
    const rev = diag.qs.map((q, i) => { const bien = diag.r[i] === q.c, tuya = diag.r[i] < 0 ? 'No lo sé' : LET[diag.r[i]];
      return `<details><summary><span class="marca ${bien ? 'ok' : 'no'}">${bien ? '✓' : '✕'}</span><span class="t"><b>${i + 1}.</b> ${esc(D.cursos[q.curso].n)} · ${esc(q.titulo)}</span><span class="fle">›</span></summary><div class="dentro"><div class="enun">${q.e}</div>${opciones(q, diag.r[i], true)}<p class="ref" style="margin:4px 4px 10px">Marcaste: ${tuya}. Correcta: ${LET[q.c]}.</p>${explicacion(q)}</div></details>`; }).join('');
    app.innerHTML = `<h1>Listo</h1><div class="tarjeta"><div class="nota">${S.diag.ok} <small>de ${S.diag.n}</small></div><p class="sub" style="margin:4px 0 0">Con esto ya está ordenado tu camino.</p></div>
      <h2>Por curso</h2><div class="tarjeta" style="padding:6px 16px">${filas}</div>
      <div class="botones"><button class="btn" id="ver">Ver mi camino</button></div>
      <h2>Revisa tus respuestas</h2><div class="tarjeta" style="padding:2px 16px">${rev}</div>`;
    mate();
    document.getElementById('ver').onclick = () => { diag = null; pila = []; ir('camino'); };
  };

  let todas = false;
  V.camino = () => {
    if (!S.grupo) return V.inicio();
    const G = D.grupos[S.grupo], av = P.avance(S, N), cam = P.camino(S, N), sig = cam[0];
    cab('Mi camino', av.total ? av.hechos / av.total : 0);
    const fila = (x, i) => `<button class="fila" data-t="${x.t.id}"><span class="num">${i}</span><span class="cuerpo"><div class="tit">${esc(x.t.t)}</div><div class="det">${esc(D.cursos[x.t.curso].n)}</div></span>${est(x.estado)}<span class="fle">›</span></button>`;
    const resto = cam.slice(1, todas ? cam.length : 7);
    const cursos = av.cursos.map(c => `<button class="fila" data-c="${c.c}"><span class="cuerpo"><div class="tit">${esc(D.cursos[c.c].n)}</div><div class="det">${c.peso} de 60 preguntas · ${c.hechos} de ${c.total} temas</div></span><span style="width:64px;flex:none">${barra(c.nivel, c.nivel >= 0.7 ? 'ok' : c.nivel < 0.4 ? 'nar' : '')}</span><span class="fle">›</span></button>`).join('');
    app.innerHTML = `<div class="portada"><div class="g">Grupo ${S.grupo}</div><div class="n">${esc(G.n)}</div>${barra(av.total ? av.hechos / av.total : 0)}<div class="pie"><span>${av.hechos} de ${av.total} temas dominados</span><button id="cg">Cambiar grupo</button></div></div>
      ${S.diag && S.diag.hecho ? '' : `<div class="tarjeta"><b>Falta tu diagnóstico</b><p class="sub" style="margin:4px 0 10px">Sin él, el camino sigue el orden general de tu grupo.</p><button class="btn chico" id="hd">Hacer el diagnóstico</button></div>`}
      ${sig ? `<div class="tarjeta sigue"><div class="rot" style="margin:0">Sigue con</div><div class="tit">${esc(sig.t.t)}</div><div style="display:flex;gap:8px;align-items:center;margin-bottom:12px">${chip(sig.t.curso)}${est(sig.estado)}</div><button class="btn" id="sg">${sig.estado === 'nuevo' ? 'Empezar' : 'Seguir'}</button></div>` : `<div class="tarjeta"><b>Dominaste todos los temas de tu grupo.</b><p class="sub" style="margin:4px 0 0">Puedes repasar cualquier curso desde la lista de abajo.</p></div>`}
      ${resto.length ? `<h2>Después</h2><div class="grupo">${resto.map((x, i) => fila(x, i + 2)).join('')}</div>` : ''}
      ${cam.length > 7 ? `<button class="btn txt" id="vt">${todas ? 'Ver menos' : `Ver las ${cam.length} etapas`}</button>` : ''}
      ${iaLista() ? `<div class="tarjeta"><b>Tu tutor</b><p class="sub" style="margin:4px 0 10px">Pregúntale cualquier duda. No te da la respuesta de frente: te hace preguntas para que llegues tú.</p><button class="btn chico" id="tt">Preguntarle al tutor</button></div>` : ''}
      <h2>Tus cursos</h2><div class="grupo">${cursos}</div>
      ${S.diag && S.diag.hecho ? `<button class="btn txt" id="rd">Repetir el diagnóstico</button>` : ''}
      <p class="ref">Pesos por curso según ${esc(G.fuente)}.</p>`;
    const on = (id, fn) => { const b = document.getElementById(id); if (b) b.onclick = fn; };
    on('cg', () => ir('grupo')); on('hd', () => ir('diag')); on('rd', () => ir('diag')); on('sg', () => abrirTema(sig.t)); on('vt', () => { todas = !todas; pintar(); }); on('tt', () => ir('tutor', ''));
    app.querySelectorAll('[data-t]').forEach(b => b.onclick = () => abrirTema(P.tema(b.getAttribute('data-t'))));
    app.querySelectorAll('[data-c]').forEach(b => b.onclick = () => ir('curso', b.getAttribute('data-c')));
  };

  V.curso = c => {
    const ts = P.temasCurso(c), peso = D.grupos[S.grupo].cursos[c];
    cab(D.cursos[c].n, P.nivelCurso(S, N, c));
    app.innerHTML = `<h1>${esc(D.cursos[c].n)}</h1><p class="sub">${peso ? `En tu grupo entran ${peso} de 60 preguntas de este curso.` : ''}</p><div class="grupo">${ts.map((t, i) => `<button class="fila" data-t="${t.id}"><span class="num">${i + 1}</span><span class="cuerpo"><div class="tit">${esc(t.t)}</div></span>${est(P.estado(P.dominio(S, N, t)))}<span class="fle">›</span></button>`).join('')}</div>`;
    app.querySelectorAll('[data-t]').forEach(b => b.onclick = () => abrirTema(P.tema(b.getAttribute('data-t'))));
  };

  V.tema = id => {
    const t = P.tema(id), m = P.dominio(S, N, t), r = (S.t || {})[id], idea = D.idea[id] || [];
    cab(t.t, m);
    const ult = r && r.r.length ? `<p class="ref" style="margin:0 4px 12px">Últimas respuestas: ${r.r.slice(-5).map(v => v ? '✓' : '✕').join(' ')}</p>` : '';
    app.innerHTML = `<div style="margin:6px 0 4px;display:flex;gap:8px;align-items:center">${chip(t.curso)}${est(P.estado(m))}</div><h1 style="margin-left:0">${esc(t.t)}</h1>
      ${idea.length ? `<div class="tarjeta idea"><div class="rot">Idea clave</div>${idea.map(p => `<p>${p}</p>`).join('')}</div>` : ''}${ult}
      <div class="botones"><button class="btn" id="pr">Practicar ${SESION} preguntas</button>${iaLista() ? '<button class="btn txt" id="tu">Preguntarle al tutor sobre este tema</button>' : ''}</div>
      <p class="ref">${t.gen ? 'Cada pregunta cambia los datos. Si fallas, tu tutor no te da la respuesta: te guía con preguntas, paso a paso.' : 'Si fallas, tu tutor no te da la respuesta: te hace preguntas para que la encuentres tú.'}</p>`;
    mate();
    document.getElementById('pr').onclick = () => { ses = { t, i: 0, ok: 0, guia: 0, q: null, sel: null, visto: false, antes: m }; ir('practica'); };
    const tu = document.getElementById('tu'); if (tu) tu.onclick = () => ir('tutor', id);
  };

  // Conversación libre con el tutor (necesita la IA): de un tema o de cualquier curso.
  V.tutor = id => {
    const t = id ? P.tema(id) : null;
    if (!tut || tut.tema !== (id || '')) { tut = chatLibre(t); tut.tema = id || ''; } else chat = tut;
    cab(t ? 'Tutor · ' + t.t : 'Tu tutor');
    app.innerHTML = `<h1>${t ? esc(t.t) : 'Pregúntale a tu tutor'}</h1><p class="sub">${t ? 'Escribe tu duda sobre este tema.' : 'Escribe una duda de cualquier curso, o un ejercicio que no te sale.'} El tutor no da la respuesta de frente: te hace preguntas para que llegues tú.</p>
      ${iaLista() ? chatHtml('Conversación', 'Escribe tu duda') : '<div class="tarjeta"><b>El tutor necesita Internet y la IA activa.</b><p class="sub" style="margin:4px 0 0">La práctica con guía paso a paso funciona sin conexión.</p></div>'}`;
    chatOn();
  };

  // Pregunta abierta en la práctica de la app: se conversa aquí con el tutor y «atrás» vuelve a la práctica.
  V.externa = () => {
    const x = ext; if (!x) return V.inicio();
    if (!x.chat) x.chat = chatExterna(x); else chat = x.chat;
    cab('Tutor · ' + x.tema);
    const ops = x.o.map((o, i) => `<div class="alt${x.visto && i === x.c ? ' bien' : ''}"><b>${LET[i]}</b><span>${esc(o)}</span></div>`).join('');
    app.innerHTML = `<div class="enun plano">${esc(x.e)}</div><div class="tarjeta alts">${ops}</div>
      <p class="sub">${x.visto ? 'Pregúntale lo que no te quedó claro de la solución.' : 'Cuéntale qué intentaste o dónde te trabaste. El tutor no te da la respuesta: te hace preguntas para que llegues tú.'}</p>
      ${iaLista() ? chatHtml('Pregúntale a tu tutor', x.visto ? 'Escribe tu duda' : 'Escribe lo que piensas o tu duda') : '<div class="tarjeta"><b>El tutor necesita Internet y la IA activa.</b></div>'}
      <div class="botones"><button class="btn sec" id="vol">Volver a mi práctica</button></div>`;
    chatOn();
    document.getElementById('vol').onclick = () => { try { A.cerrar(); } catch (e) { /* puente viejo: queda el botón atrás */ } };
  };

  V.practica = () => {
    if (!ses) return atras() || V.inicio();
    const t = ses.t;
    if (!ses.q) { const rec = (S.t || {})[t.id]; ses.q = P.pregunta(t, semilla(), [1, 2, 2, 3, 2][ses.i] || 2, rec ? rec.v : []); ses.sel = null; ses.visto = false; ses.bien = false; ses.g = null; ses.chat = null; }
    const q = ses.q, g = ses.g, multi = q.pasos.length > 1, ia = iaLista();
    chat = ses.chat;
    cab(`${t.t} · ${ses.i + 1} de ${SESION}`, (ses.i + (ses.visto ? 1 : 0)) / SESION);
    const ops = q.o.map((o, i) => { const mala = !!g && g.mal.indexOf(i) >= 0; let k = '';
      if (ses.visto) k = i === q.c ? ' bien' : (mala || i === ses.sel ? ' malo' : ''); else if (mala) k = ' malo'; else if (i === ses.sel) k = ' sel';
      return `<button class="opc${k}" data-o="${i}"${ses.visto || mala ? ' disabled' : ''}><span class="l">${LET[i]}</span><span>${o}</span></button>`; }).join('');
    const tutor = (ph, abre) => !ia ? '' : chat ? chatHtml('Pregúntale a tu tutor', ph) : `<button class="btn txt" id="ab">${abre}</button>`;
    let h = `<div class="enun">${q.e}</div><div id="ops">${ops}</div>`;
    if (ses.visto) h += `<div class="veredicto ${ses.bien ? 'ok' : 'no'}">${ses.bien ? (g ? 'Correcto. Llegaste con la guía.' : 'Correcto') : `La respuesta es la ${LET[q.c]}`}</div>${explicacion(q)}
        ${tutor('Escribe tu duda sobre esta pregunta', 'Tengo una duda: preguntarle al tutor')}
        <div class="botones"><button class="btn" id="sig">${ses.i === SESION - 1 ? 'Ver resultado' : 'Siguiente'}</button></div>`;
    else if (g) h += `<div class="veredicto guia">${esc(g.msj)}</div>${guiaHtml(q, g)}
        <div class="botones"><button class="btn" id="resp"${ses.sel == null ? ' disabled' : ''}>Responder</button><button class="btn txt" id="sol">Ver la solución</button></div>
        ${tutor('Escribe lo que piensas o tu duda', 'Conversar con el tutor')}`;
    else h += `<div class="botones"><button class="btn" id="resp"${ses.sel == null ? ' disabled' : ''}>Responder</button><button class="btn txt" id="ns">No lo sé</button></div>`;
    app.innerHTML = h; mate(); chatOn();

    const on = (id, fn) => { const b = document.getElementById(id); if (b) b.onclick = fn; };
    const mira = sel => { const v = document.querySelector(sel); if (v) v.scrollIntoView({ behavior: 'smooth', block: 'start' }); };
    const cierra = bien => { ses.visto = true; ses.bien = bien; pintar(); mira('.veredicto'); };
    // Primer error o «No lo sé»: no se muestra la respuesta; empieza la guía de preguntas.
    const guiar = sel => {
      ses.g = { G: window.Guia ? window.Guia.de(t.id, q) : [], k: multi ? 0 : 1, mal: sel >= 0 ? [sel] : [],
        msj: sel >= 0 ? `No es la ${LET[sel]}. Todavía no te digo la respuesta: piénsalo con estas preguntas.` : 'Vamos paso a paso. Todavía no te digo la respuesta.' };
      ses.sel = null; pintar(); mira('.veredicto');
    };
    if (!ses.visto) app.querySelectorAll('.opc:not([disabled])').forEach(b => b.onclick = () => { ses.sel = +b.getAttribute('data-o'); pintar(); });
    on('resp', () => {
      const sel = ses.sel, ok = sel === q.c;
      if (!g) {   // el primer intento es el que cuenta para el dominio del tema
        P.anotar(S, t.id, ok, q.i); guardar();
        if (ok) { ses.ok++; ev('correcta'); return cierra(true); }
        ev('incorrecta'); return guiar(sel);
      }
      if (ok) { ses.guia = (ses.guia || 0) + 1; ev('guiada'); return cierra(true); }
      // otro error con la guía abierta: se descarta esa alternativa y sale el siguiente paso o la siguiente pregunta
      g.mal.push(sel); ses.sel = null;
      const n = multi ? q.pasos.length - 1 : g.G.length;
      if (g.mal.length >= 3 || g.k >= n) return cierra(false);
      g.k++; g.msj = `Tampoco es la ${LET[sel]}. ${multi ? 'Mira el paso que sigue.' : 'Piensa en esta otra pregunta.'}`; pintar(); mira('.veredicto');
    });
    on('ns', () => { P.anotar(S, t.id, false, q.i); guardar(); ev('duda'); guiar(-1); });
    on('sol', () => cierra(false));
    on('vp', () => { g.k++; pintar(); const ps = document.querySelectorAll('.guia .gp'), u = ps[ps.length - 1]; if (u) u.scrollIntoView({ behavior: 'smooth', block: 'center' }); });
    on('ab', () => { ses.chat = chatPregunta(t, q); pintar(); const m = document.getElementById('msg'); if (m) m.focus(); });
    on('sig', () => { ses.i++; ses.q = null; if (ses.i >= SESION) return ir('fin', null, true); pintar(); window.scrollTo(0, 0); });
  };

  V.fin = () => {
    if (!ses) return V.inicio();
    const t = ses.t, m = P.dominio(S, N, t), e = P.estado(m), sig = P.camino(S, N).find(x => x.t.id !== t.id);
    cab(t.t, 1); if (m >= 1 && ses.antes < 1) ev('logro');
    app.innerHTML = `<h1>${ses.ok >= 4 ? 'Muy bien' : ses.ok >= 2 ? 'Vas avanzando' : 'Hay que reforzar'}</h1><div class="tarjeta"><div class="nota">${ses.ok} <small>de ${SESION} a la primera</small></div>${ses.guia ? `<p class="sub" style="margin:2px 0 0">${ses.guia === 1 ? 'Una más la sacaste' : ses.guia + ' más las sacaste'} con la guía de tu tutor.</p>` : ''}<div style="margin-top:8px;display:flex;gap:8px;align-items:center">${chip(t.curso)}${est(e)}</div>
      <p class="sub" style="margin:10px 0 0">${m >= 1 ? 'Este tema queda como dominado. Tu camino pasa al siguiente.' : 'El tema queda dominado con 4 aciertos a la primera en las últimas 5 respuestas.'}</p></div>
      <div class="botones">${m >= 1 && sig ? `<button class="btn" id="otro">Seguir con: ${esc(sig.t.t)}</button><button class="btn sec" id="rep">Practicar otra vez</button>` : `<button class="btn" id="rep">Practicar otra vez</button>`}<button class="btn txt" id="cam">Volver a mi camino</button></div>`;
    const on = (id, fn) => { const b = document.getElementById(id); if (b) b.onclick = fn; };
    on('rep', () => { ses = { t, i: 0, ok: 0, guia: 0, q: null, sel: null, visto: false, antes: m }; ir('practica', null, true); });
    on('cam', () => { ses = null; pila = []; ir('camino'); });
    on('otro', () => { ses = null; pila = [{ v: 'camino' }]; abrirTema(sig.t); if (sig.t.nat) pintar(); });
  };

  function pintar() { const top = pila[pila.length - 1]; if (!top) return V.inicio(); try { V[top.v](top.arg); } catch (e) { app.innerHTML = `<div class="tarjeta"><b>Algo falló al mostrar esta pantalla.</b><p class="sub" style="margin:6px 0 12px">${esc(e.message)}</p><button class="btn" onclick="location.reload()">Volver a cargar</button></div>`; } }
  window.caminoEstado = () => ({ S, N, pila: pila.map(p => p.v), ses, diag });   // para pruebas
  window.caminoIr = ir;
  // Si la app abrió esta página con una pregunta de su práctica, se muestra directo la conversación sobre ella.
  function externa() {
    try {
      const j = A && A.pendiente ? A.pendiente() : ''; if (!j) return null;
      const x = JSON.parse(j), txt = v => String(v == null ? '' : v).replace(/\s+/g, ' ').trim();
      if (!x || !Array.isArray(x.o) || x.o.length < 2 || !(x.c >= 0 && x.c < x.o.length) || !txt(x.e)) return null;
      return { curso: txt(x.curso).slice(0, 60), tema: txt(x.tema).slice(0, 100), e: String(x.e).trim().slice(0, 2000), o: x.o.map(txt), c: x.c | 0,
        sol: Array.isArray(x.sol) ? x.sol.map(txt).filter(Boolean) : [], visto: !!x.visto, chat: null };
    } catch (e) { return null; }
  }
  ext = externa();
  if (ext) { pila = [{ v: 'externa' }]; pintar(); } else V.inicio();
})();
