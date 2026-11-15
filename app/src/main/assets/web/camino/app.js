// Camino UNAMBA: elegir grupo, diagnóstico, camino personalizado y práctica con respuesta inmediata.
// Método (práctica de recuperación): el estudiante responde primero; al comprobar ve al instante si acertó y por qué,
// con la solución paso a paso. Lo que falla vuelve al final de la lección con datos nuevos, y cada tema regresa en el
// «Repaso de hoy» cuando toca (cajas de Leitner), mezclado con otros temas. Todo funciona sin Internet.
// Dentro de la app habla con Kotlin por window.AndroidCamino (guardar, mascota y voz); en un navegador guarda en
// localStorage (para pruebas).
(function () {
  'use strict';
  const D = window.DATOS, P = window.Plan, A = window.AndroidCamino || null, app = document.getElementById('app');
  const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const LET = 'ABCDE', SESION = 5, DIFS = [1, 2, 2, 3, 2], PARTE = 10;
  // Exámenes reales (examenes.js, solo si está en la app): { lista: [{id, nombre, anio, grupo, total}], preg: [{id, ex, n, curso, tema, e, o, c, x, fig}] }
  const EX = window.EXAMENES && Array.isArray(window.EXAMENES.preg) && window.EXAMENES.preg.length ? window.EXAMENES : null;
  if (EX && !D.cursos.EST) D.cursos.EST = { n: 'Estadística', k: 'mat' };
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

  // ---------------------------------------------------------------- voz de la mascota (motor del teléfono, sin Internet)
  const puede = f => { try { return !!(A && A[f] && A[f]()); } catch (e) { return false; } };
  const vozLista = () => puede('vozDisponible');
  const oidoListo = () => puede('escuchaDisponible');
  const callada = () => puede('silencio');
  // Texto de libro (HTML + LaTeX) a texto para leer en voz alta. Kotlin termina de adaptarlo (Speech.forTts).
  function paraVoz(h) {
    let s = String(h == null ? '' : h).replace(/<svg[\s\S]*?<\/svg>/g, ' ').replace(/<\/t[dh]>/g, ', ').replace(/<\/tr>/g, '. ').replace(/<br\s*\/?>/g, '. ');
    s = s.replace(/\$\$?([^$]+)\$\$?/g, (_, m) => ' ' + latexVoz(m) + ' ');
    const d = document.createElement('div'); d.innerHTML = s;
    return d.textContent.replace(/\s+/g, ' ').trim();
  }
  function latexVoz(m) {
    let s = m.replace(/\\(text|mathrm|mathbf|textbf|operatorname)\{([^{}]*)\}/g, ' $2 ').replace(/\\left|\\right/g, '').replace(/\^\{?\\circ\}?/g, '°');
    for (let k = 0; k < 4; k++) s = s.replace(/\\[dt]?frac\{([^{}]*)\}\{([^{}]*)\}/g, '($1)/($2)');
    s = s.replace(/\\sqrt\[3\]\{([^{}]*)\}/g, ' raíz cúbica de ($1)').replace(/\\sqrt\{([^{}]*)\}/g, '√($1)').replace(/\\sqrt\s*(\w)/g, '√$1');
    s = s.replace(/\^\{2\}|\^2(?!\d)/g, '²').replace(/\^\{3\}|\^3(?!\d)/g, '³').replace(/\^\{([^{}]*)\}/g, '^($1)');
    s = s.replace(/_\{([^{}]*)\}/g, ' $1').replace(/_(\w)/g, ' $1');
    const pal = { cdot: '×', times: '×', div: '÷', le: ' menor o igual que ', leq: ' menor o igual que ', ge: ' mayor o igual que ', geq: ' mayor o igual que ',
      neq: ' diferente de ', ne: ' diferente de ', approx: '≈', pi: ' pi ', theta: 'θ', alpha: ' alfa ', beta: ' beta ', Delta: ' delta ', angle: ' ángulo ', triangle: ' triángulo ',
      parallel: ' es paralela a ', perp: ' es perpendicular a ', cup: ' unión ', cap: ' intersección ', in: ' pertenece a ', subset: ' incluido en ', infty: ' infinito ', to: ' a ',
      rightarrow: ' entonces ', Rightarrow: ' entonces ', sin: ' sen ', cos: ' cos ', tan: ' tan ', cot: ' cot ', sec: ' sec ', csc: ' csc ', log: ' log ', overline: ' segmento ',
      pm: '±', ldots: '...', dots: '...', cdots: '...' };
    s = s.replace(/\\[,;:! ]/g, ' ').replace(/\\%/g, '%');
    s = s.replace(/\\([a-zA-Z]+)/g, (x, w) => (pal[w] != null ? pal[w] : ' '));
    s = s.replace(/<(?!=)/g, ' menor que ').replace(/>(?!=)/g, ' mayor que ').replace(/[{}]/g, ' ');
    return s.replace(/\s+/g, ' ').trim();
  }
  function hablar(t) { try { if (A && A.hablar && t) A.hablar(String(t).slice(0, 3800)); } catch (e) { /* sin voz */ } }
  function callar() { try { if (A && A.callar) A.callar(); } catch (e) { /* sin voz */ } }
  const punto = s => (/[.?!:;]$/.test(s) ? s : s + '.');
  const vozPregunta = q => 'Pregunta. ' + punto(paraVoz(q.e)) + ' ' + q.o.map((o, i) => 'Opción ' + LET[i] + '. ' + punto(paraVoz(o))).join(' ');
  const vozSolucion = q => (q.pasos.length > 1 ? q.pasos.map((p, i) => 'Paso ' + (i + 1) + '. ' + punto(paraVoz(p))).join(' ') : paraVoz(q.pasos[0]));
  // dictado: la app escucha y devuelve la alternativa con caminoOido(índice, texto); solo marca, no responde sola
  let oyendo = false, oidoMsj = '';
  function escuchar() { if (oyendo) return; oyendo = true; oidoMsj = ''; callar(); pintar(); try { A.escuchar(); } catch (e) { oyendo = false; pintar(); } }
  window.caminoOido = (i, txt) => {
    oyendo = false; i = i | 0;
    const top = pila[pila.length - 1], v = top && top.v;
    const objetivo = v === 'practica' && ses && !ses.visto ? ses : v === 'diagp' && diag ? diag : null;
    if (objetivo && i >= 0 && i < 5) { objetivo.sel = i; oidoMsj = `Escuché «${txt}»: marqué la ${LET[i]}. Toca «Comprobar».`; }
    else oidoMsj = txt ? `No entendí la letra («${txt}»). Di por ejemplo «la B».` : '';
    if (v === 'diagp' && objetivo && i >= 0) oidoMsj = `Escuché «${txt}»: marqué la ${LET[i]}.`;
    pintar();
  };
  const ICO_VOZ = '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path fill="currentColor" d="M3 10v4h4l5 4V6L7 10H3zm13.5 2a4.5 4.5 0 0 0-2.5-4v8a4.5 4.5 0 0 0 2.5-4zM14 3.2v2.1a7 7 0 0 1 0 13.4v2.1a9 9 0 0 0 0-17.6z"/></svg>';
  const ICO_MIC = '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path fill="currentColor" d="M12 14a3 3 0 0 0 3-3V5a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.9V21h2v-3.1A7 7 0 0 0 19 11h-2z"/></svg>';
  const btnVoz = (id, txt) => vozLista() ? `<button class="vozb" id="${id}" aria-label="${esc(txt)}">${ICO_VOZ}<span>${esc(txt)}</span></button>` : '';
  const btnMic = () => oidoListo() ? `<button class="vozb mic${oyendo ? ' on' : ''}" id="mic"${oyendo ? ' disabled' : ''}>${ICO_MIC}<span>${oyendo ? 'Te escucho… di la letra' : 'Responder con voz'}</span></button>` : '';

  // ---------------------------------------------------------------- navegación (pila propia; el botón atrás de Android llama a caminoAtras)
  let pila = [], diag = null, ses = null;
  function ir(v, arg, reemplaza) { if (reemplaza) pila.pop(); pila.push({ v, arg }); oidoMsj = ''; callar(); pintar(); window.scrollTo(0, 0); }
  function atras() {
    // Con un aviso abierto, «atrás» solo lo cierra (como «Seguir aquí»): nunca se apilan dos avisos.
    const dlg = document.querySelector('.dlg'); if (dlg) { dlg.remove(); return true; }
    callar();
    const top = pila[pila.length - 1];
    if (top && top.v === 'diagp' && diag && diag.i > 0) return confirmar('¿Salir del diagnóstico?', 'Se pierde lo que respondiste.', 'Salir', () => { diag = null; pila.pop(); pila.pop(); if (!pila.length) pila.push({ v: 'inicio' }); pintar(); }), true;
    if (top && top.v === 'practica' && ses && (ses.i > 0 || ses.visto)) return confirmar('¿Salir de la práctica?', 'Lo que ya respondiste queda guardado.', 'Salir', () => { ses = null; pila.pop(); pintar(); }), true;
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
  const cuando = n => n == null ? '' : n <= 0 ? 'hoy' : n === 1 ? 'mañana' : `en ${n} días`;
  function opciones(q, sel, visto) {
    return q.o.map((o, i) => { let k = ''; if (visto) k = i === q.c ? ' bien' : (i === sel ? ' malo' : ''); else if (i === sel) k = ' sel';
      return `<button class="opc${k}" data-o="${i}"${visto ? ' disabled' : ''}><span class="l">${LET[i]}</span><span>${o}</span></button>`; }).join('');
  }
  function explicacion(q) {
    return q.pasos.length === 1 ? `<div class="uno">${q.pasos[0]}</div>`
      : `<ol class="pasos">${q.pasos.map((p, i) => `<li${i === q.pasos.length - 1 ? ' class="fin"' : ''}>${p}</li>`).join('')}</ol>`;
  }
  function abrirTema(t) {
    S.ult = t.id; guardar();
    if (!t.nat) return ir('tema', t.id);
    if (A) { try { A.abrirTema(t.id); } catch (e) { /* puente viejo */ } } else confirmar(t.t, 'Este tema se practica en su pantalla de la app.', 'Entendido', () => {});
  }
  const ELOGIO = ['¡Bien!', '¡Excelente!', '¡Así se hace!', '¡Correcto!', '¡Muy bien!'];
  function confeti() {
    const c = document.createElement('div'); c.className = 'confeti'; c.setAttribute('aria-hidden', 'true');
    for (let i = 0; i < 28; i++) { const p = document.createElement('i'); p.style.left = ((i * 37) % 100) + '%'; p.style.animationDelay = ((i * 53) % 60) / 100 + 's'; p.className = 'c' + (i % 5); c.appendChild(p); }
    document.body.appendChild(c); setTimeout(() => c.remove(), 2600);
  }

  // ---------------------------------------------------------------- pantallas
  const V = {};
  V.inicio = () => { pila = []; if (!S.grupo) return ir('grupo'); if (!S.diag) return ir('diag'); return ir('camino'); };

  V.grupo = () => {
    cab('Elige tu grupo');
    // Cada grupo muestra los cursos que lo distinguen (los que no entran en los tres exámenes).
    const comun = c => Object.keys(D.grupos).every(g => D.grupos[g].cursos[c]);
    const h = Object.keys(D.grupos).map(g => { const G = D.grupos[g], top = Object.keys(G.cursos).filter(c => !comun(c)).slice(0, 4);
      return `<button class="gr${S.grupo === g ? ' sel' : ''}" data-g="${g}"><span class="letra">${g}</span><span><div class="nom">${esc(G.n)}</div><div class="pesos">${top.map(c => chip(c)).join('')}</div></span></button>`; }).join('');
    app.innerHTML = `<h1>¿A qué grupo postulas?</h1>${h}`;
    app.querySelectorAll('.gr').forEach(b => b.onclick = () => { const g = b.getAttribute('data-g'); if (S.grupo !== g) { S.grupo = g; S.diag = null; } guardar(); pila = []; ir(S.diag ? 'camino' : 'diag'); });
  };

  V.diag = () => {
    const G = D.grupos[S.grupo], n = Object.values(G.cursos).reduce((a, w) => a + (w >= 9 ? 2 : 1), 0);
    cab('Diagnóstico');
    app.innerHTML = `<h1>Diagnóstico</h1><div class="tarjeta"><div class="grande">${n} preguntas</div><p class="sub" style="margin:2px 0 0">Unos 10 minutos. Si no sabes una, marca «No lo sé».</p></div>
      <div class="botones"><button class="btn" id="go">Empezar</button><button class="btn txt" id="no">Ahora no</button></div>`;
    document.getElementById('go').onclick = () => { diag = { qs: P.diagnostico(S.grupo, semilla()), i: 0, r: [], sel: null }; ir('diagp'); };
    document.getElementById('no').onclick = () => { S.diag = { omitido: true }; guardar(); pila = []; ir('camino'); };
  };

  V.diagp = () => {
    if (!diag) return V.inicio();
    const q = diag.qs[diag.i], n = diag.qs.length;
    cab(`Diagnóstico · ${diag.i + 1} de ${n}`, diag.i / n);
    app.innerHTML = `<div class="fila-top">${chip(q.curso)}${btnVoz('esc', 'Escuchar')}</div><div class="enun">${q.e}</div><div id="ops">${opciones(q, diag.sel, false)}</div>
      ${btnMic()}${oidoMsj ? `<p class="oido">${esc(oidoMsj)}</p>` : ''}
      <div class="botones"><button class="btn" id="sig"${diag.sel == null ? ' disabled' : ''}>${diag.i === n - 1 ? 'Terminar' : 'Siguiente'}</button><button class="btn txt" id="ns">No lo sé</button></div>`;
    mate();
    app.querySelectorAll('.opc').forEach(b => b.onclick = () => { diag.sel = +b.getAttribute('data-o'); oidoMsj = ''; pintar(); });
    const sigue = sel => { callar(); diag.r.push(sel); diag.sel = null; oidoMsj = ''; diag.i++; if (diag.i >= n) return finDiag(); pintar(); window.scrollTo(0, 0); };
    document.getElementById('sig').onclick = () => sigue(diag.sel);
    document.getElementById('ns').onclick = () => sigue(-1);
    const e = document.getElementById('esc'); if (e) e.onclick = () => hablar(vozPregunta(q));
    const m = document.getElementById('mic'); if (m) m.onclick = escuchar;
  };
  function finDiag() {
    const res = {}; let ok = 0;
    diag.qs.forEach((q, i) => { const r = res[q.curso] = res[q.curso] || { ok: 0, n: 0 }; r.n++; if (diag.r[i] === q.c) { r.ok++; ok++; } });
    S.diag = { hecho: true, fecha: P.hoy(), res, ok, n: diag.qs.length }; guardar(); ev('logro');
    pila = [{ v: 'camino' }]; ir('diagfin');
  }
  V.diagfin = () => {
    if (!diag || !S.diag || !S.diag.hecho) return V.inicio();
    cab('Resultado del diagnóstico', 1);
    const filas = Object.keys(D.grupos[S.grupo].cursos).map(c => { const r = S.diag.res[c]; if (!r) return ''; const k = r.ok === r.n ? ['Bien', 'dom'] : r.ok === 0 ? ['A reforzar', ''] : ['A medias', 'pra'];
      return `<div class="linea"><div class="cuerpo">${esc(D.cursos[c].n)}</div><span class="est ${k[1]}">${k[0]}</span></div>`; }).join('');
    const rev = diag.qs.map((q, i) => { const bien = diag.r[i] === q.c, tuya = diag.r[i] < 0 ? 'No lo sé' : LET[diag.r[i]];
      return `<details><summary><span class="marca ${bien ? 'ok' : 'no'}">${bien ? '✓' : '✕'}</span><span class="t"><b>${i + 1}.</b> ${esc(q.titulo)}</span><span class="fle">›</span></summary><div class="dentro"><div class="enun">${q.e}</div>${opciones(q, diag.r[i], true)}<p class="ref" style="margin:4px 4px 10px">Marcaste: ${tuya}. Correcta: ${LET[q.c]}.</p><div class="tarjeta">${explicacion(q)}</div></div></details>`; }).join('');
    app.innerHTML = `<div class="tarjeta centro"><div class="nota">${S.diag.ok} <small>de ${S.diag.n}</small></div><p class="sub" style="margin:4px 0 0">Tu camino ya está ordenado.</p></div>
      <div class="botones"><button class="btn" id="ver">Ver mi camino</button></div>
      <h2>Por curso</h2><div class="tarjeta" style="padding:6px 16px">${filas}</div>
      <h2>Tus respuestas</h2><div class="tarjeta" style="padding:2px 16px">${rev}</div>`;
    mate();
    document.getElementById('ver').onclick = () => { diag = null; pila = []; ir('camino'); };
  };

  let todas = false;
  V.camino = () => {
    if (!S.grupo) return V.inicio();
    const G = D.grupos[S.grupo], av = P.avance(S, N), cam = P.camino(S, N), sig = cam[0], dia = P.hoy(), venc = P.vencidos(S, dia);
    cab('Mi camino', av.total ? av.hechos / av.total : 0);
    const fila = (x, i) => `<button class="fila" data-t="${x.t.id}"><span class="num">${i}</span><span class="cuerpo"><div class="tit">${esc(x.t.t)}</div><div class="det">${esc(D.cursos[x.t.curso].n)}</div></span>${est(x.estado)}<span class="fle">›</span></button>`;
    const resto = cam.slice(1, todas ? cam.length : 6);
    const cursos = av.cursos.map(c => `<button class="fila" data-c="${c.c}"><span class="cuerpo"><div class="tit">${esc(D.cursos[c.c].n)}</div><div class="det">${c.peso} preguntas · ${c.hechos}/${c.total} temas</div></span><span style="width:64px;flex:none">${barra(c.nivel, c.nivel >= 0.7 ? 'ok' : c.nivel < 0.4 ? 'nar' : '')}</span><span class="fle">›</span></button>`).join('');
    app.innerHTML = `<div class="portada"><div class="g">Grupo ${S.grupo}</div><div class="n">${esc(G.n)}</div>${barra(av.total ? av.hechos / av.total : 0)}<div class="pie"><span>${av.hechos} de ${av.total} temas dominados</span><button id="cg">Cambiar</button></div></div>
      ${EX ? `<button class="tarjeta exam" id="ex"><span class="cuerpo"><span class="tit">Exámenes UNAMBA</span><span class="sub">${EX.lista.length} exámenes · ${EX.preg.length} preguntas reales</span></span><span class="fle">›</span></button>` : ''}
      ${venc.length ? `<div class="tarjeta repaso"><div class="tit">Repaso de hoy</div><p class="sub" style="margin:2px 0 12px">${venc.length === 1 ? '1 tema' : venc.length + ' temas'} · preguntas mezcladas</p><button class="btn nar" id="rp">Repasar</button></div>` : ''}
      ${S.diag && S.diag.hecho ? '' : `<div class="tarjeta"><div class="tit">Haz tu diagnóstico</div><p class="sub" style="margin:2px 0 12px">Para ordenar tu camino.</p><button class="btn chico" id="hd">Empezar</button></div>`}
      ${sig ? `<div class="tarjeta sigue"><div class="rot" style="margin:0">Sigue con</div><div class="tit">${esc(sig.t.t)}</div><div style="display:flex;gap:8px;align-items:center;margin-bottom:12px">${chip(sig.t.curso)}${est(sig.estado)}</div><button class="btn" id="sg">${sig.estado === 'nuevo' ? 'Empezar' : 'Seguir'}</button></div>` : `<div class="tarjeta"><b>Dominaste todos los temas de tu grupo.</b></div>`}
      ${resto.length ? `<h2>Después</h2><div class="grupo">${resto.map((x, i) => fila(x, i + 2)).join('')}</div>` : ''}
      ${cam.length > 6 ? `<button class="btn txt" id="vt">${todas ? 'Ver menos' : `Ver las ${cam.length} etapas`}</button>` : ''}
      <h2>Tus cursos</h2><div class="grupo">${cursos}</div>
      ${S.diag && S.diag.hecho ? `<button class="btn txt" id="rd">Repetir el diagnóstico</button>` : ''}`;
    const on = (id, fn) => { const b = document.getElementById(id); if (b) b.onclick = fn; };
    on('cg', () => ir('grupo')); on('hd', () => ir('diag')); on('rd', () => ir('diag')); on('sg', () => abrirTema(sig.t)); on('vt', () => { todas = !todas; pintar(); });
    on('rp', () => empezarRepaso());
    on('ex', () => ir('examenes'));
    app.querySelectorAll('[data-t]').forEach(b => b.onclick = () => abrirTema(P.tema(b.getAttribute('data-t'))));
    app.querySelectorAll('[data-c]').forEach(b => b.onclick = () => ir('curso', b.getAttribute('data-c')));
  };

  V.curso = c => {
    const ts = P.temasCurso(c), peso = D.grupos[S.grupo].cursos[c], reales = EX ? EX.preg.filter(p => p.curso === c).length : 0;
    cab(D.cursos[c].n, P.nivelCurso(S, N, c));
    app.innerHTML = `<h1>${esc(D.cursos[c].n)}</h1>${peso ? `<p class="sub">${peso} de 60 preguntas en tu grupo</p>` : ''}
      ${reales ? `<button class="btn sec" id="rc" style="margin-bottom:12px">Preguntas reales de examen (${reales})</button>` : ''}
      <div class="grupo">${ts.map((t, i) => `<button class="fila" data-t="${t.id}"><span class="num">${i + 1}</span><span class="cuerpo"><div class="tit">${esc(t.t)}</div></span>${est(P.estado(P.dominio(S, N, t)))}<span class="fle">›</span></button>`).join('')}</div>`;
    app.querySelectorAll('[data-t]').forEach(b => b.onclick = () => abrirTema(P.tema(b.getAttribute('data-t'))));
    const rc = document.getElementById('rc'); if (rc) rc.onclick = () => empezarCursoReal(c);
  };

  // ---------------------------------------------------------------- exámenes reales de la UNAMBA
  // Preguntas de los cuadernillos (uso autorizado por la universidad). Viven en examenes.js y exfig/, que no se suben al
  // repositorio público. Los cuadernillos no traen clave: la respuesta y la explicación son del equipo del proyecto.
  const vistas = () => { S.ex = S.ex || {}; S.ex.v = S.ex.v || {}; return S.ex.v; };
  function preguntaReal(p) {
    const fig = p.fig ? `<img class="fig foto" src="${esc(p.fig)}" alt="Figura de la pregunta ${p.n}">` : '';
    return { e: p.e + fig, o: p.o.slice(), c: p.c, pasos: p.x && p.x.length ? p.x : ['Respuesta: ' + LET[p.c] + '.'], ref: '', id: p.id, curso: p.curso, real: true };
  }
  const temaReal = (titulo, curso) => ({ id: 'examen', t: titulo, curso: D.cursos[curso] ? curso : 'RM', nat: false });
  const delExamen = id => EX.preg.filter(p => p.ex === id).sort((a, b) => a.n - b.n);
  function empezarReal(titulo, lista, info) {
    if (!lista.length) return pintar();
    ses = Object.assign({ modo: 'examen', titulo, cola: lista.map(p => ({ tipo: 'r', t: temaReal(titulo, p.curso), q: preguntaReal(p) })), i: 0, nuevas: lista.length, ok: 0, corr: 0, racha: 0, mejor: 0, q: null, sel: null, visto: false }, info);
    ir('practica');
  }
  function empezarParte(exId, desde) {
    const ex = EX.lista.find(x => x.id === exId);
    empezarReal(ex.nombre, delExamen(exId).slice(desde, desde + PARTE), { exId, desde });
  }
  // 10 preguntas reales del curso: primero las que nunca respondió, luego las que falló; en desorden.
  function empezarCursoReal(c) {
    const v = vistas(), R = window.Gen.rng(semilla());
    const todas = R.sh(EX.preg.filter(p => p.curso === c));
    const orden = todas.filter(p => !(p.id in v)).concat(todas.filter(p => v[p.id] === 0), todas.filter(p => v[p.id] === 1));
    empezarReal('Preguntas reales · ' + D.cursos[c].n, orden.slice(0, PARTE), { cursoReal: c });
  }
  const resumenReal = lista => { const v = vistas(); let hechas = 0, ok = 0; lista.forEach(p => { if (p.id in v) { hechas++; ok += v[p.id]; } }); return { hechas, ok }; };

  V.examenes = () => {
    if (!EX) return V.inicio();
    cab('Exámenes UNAMBA', null);
    const filas = EX.lista.map(x => { const r = resumenReal(delExamen(x.id));
      return `<button class="fila" data-x="${x.id}"><span class="cuerpo"><div class="tit">${esc(x.nombre)}</div><div class="det">${x.total} preguntas${r.hechas ? ` · ${r.hechas} respondidas, ${r.ok} bien` : ''}</div></span>${barra(x.total ? r.hechas / x.total : 0)}<span class="fle">›</span></button>`; }).join('');
    app.innerHTML = `<h1>Exámenes UNAMBA</h1><p class="sub">Preguntas reales de los cuadernillos. La solución es del equipo: los cuadernillos no traen clave.</p><div class="grupo lista-ex">${filas}</div>`;
    app.querySelectorAll('[data-x]').forEach(b => b.onclick = () => ir('examen', b.getAttribute('data-x')));
  };
  V.examen = id => {
    const x = EX && EX.lista.find(e => e.id === id); if (!x) return V.inicio();
    const qs = delExamen(id);
    cab(x.nombre, resumenReal(qs).hechas / Math.max(1, qs.length));
    const partes = [];
    for (let d = 0; d < qs.length; d += PARTE) {
      const tramo = qs.slice(d, d + PARTE), r = resumenReal(tramo), cursos = [...new Set(tramo.map(p => p.curso))].filter(c => D.cursos[c]).map(c => D.cursos[c].n).join(', ');
      partes.push(`<button class="fila" data-d="${d}"><span class="num">${d / PARTE + 1}</span><span class="cuerpo"><div class="tit">Preguntas ${tramo[0].n}–${tramo[tramo.length - 1].n}</div><div class="det">${esc(cursos)}</div></span><span class="est${r.hechas === tramo.length ? ' dom' : ''}">${r.hechas ? `${r.ok}/${tramo.length}` : 'Nuevo'}</span><span class="fle">›</span></button>`);
    }
    app.innerHTML = `<h1>${esc(x.nombre)}</h1><p class="sub">${qs.length} preguntas en partes de ${PARTE}.</p><div class="grupo">${partes.join('')}</div>`;
    app.querySelectorAll('[data-d]').forEach(b => b.onclick = () => empezarParte(id, +b.getAttribute('data-d')));
  };

  V.tema = id => {
    const t = P.tema(id), m = P.dominio(S, N, t), r = (S.t || {})[id], idea = D.idea[id] || [], dr = P.diasParaRepaso(S, id, P.hoy());
    cab(t.t, m);
    const ult = r && r.r.length ? `<div class="puntos" aria-label="Últimas respuestas">${r.r.slice(-5).map(v => `<i class="${v ? 'ok' : 'no'}"></i>`).join('')}</div>` : '';
    app.innerHTML = `<div style="margin:6px 0 4px;display:flex;gap:8px;align-items:center;flex-wrap:wrap">${chip(t.curso)}${est(P.estado(m))}${dr != null ? `<span class="est">Repaso ${cuando(dr)}</span>` : ''}</div><h1 style="margin-left:0">${esc(t.t)}</h1>
      ${idea.length ? `<div class="tarjeta idea"><div class="fila-top"><div class="rot" style="margin:0">Idea clave</div>${btnVoz('esc', 'Escuchar')}</div>${idea.map(p => `<p>${p}</p>`).join('')}</div>` : ''}${ult}
      <div class="botones"><button class="btn" id="pr">Practicar</button></div>`;
    mate();
    document.getElementById('pr').onclick = () => empezar(t);
    const e = document.getElementById('esc'); if (e) e.onclick = () => hablar(t.t + '. ' + idea.map(paraVoz).join(' '));
  };

  // ---------------------------------------------------------------- práctica con respuesta inmediata
  // cola: preguntas nuevas (cuentan para el dominio) y, al final, las que falló otra vez, con datos nuevos si el tema
  // tiene generador. «No lo sé» muestra la solución como ejemplo resuelto y también la deja para el final.
  function empezar(t) {
    const m = P.dominio(S, N, t);
    ses = { modo: 'tema', t, cola: DIFS.slice(0, SESION).map(d => ({ tipo: 'n', t, dif: d })), i: 0, nuevas: SESION, ok: 0, corr: 0, racha: 0, mejor: 0, antes: m, q: null, sel: null, visto: false };
    ir('practica');
  }
  function empezarRepaso() {
    const qsR = P.repaso(S, P.hoy(), semilla(), 6); if (!qsR.length) return pintar();
    ses = { modo: 'repaso', t: null, cola: qsR.map(q => ({ tipo: 'r', t: P.tema(q.tema), q })), i: 0, nuevas: qsR.length, ok: 0, corr: 0, racha: 0, mejor: 0, temas: {}, q: null, sel: null, visto: false };
    ir('practica');
  }
  function preparar(it) {
    if (it.q && !it.usada) { it.usada = true; return it.q; }
    const rec = (S.t || {})[it.t.id];
    if (it.tipo === 'c' && it.fija != null && it.t.pregs) {   // pregunta fija: la misma, con las alternativas en otro orden
      const R = window.Gen.rng(semilla()), p = it.t.pregs[it.fija], orden = R.sh(p.o.map((_, i) => i));
      return { e: p.e, o: orden.map(i => p.o[i]), c: orden.indexOf(p.c), pasos: Array.isArray(p.x) ? p.x : [p.x], ref: p.ref || '', i: it.fija, curso: it.t.curso, tema: it.t.id, titulo: it.t.t };
    }
    return P.pregunta(it.t, semilla(), it.dif || 2, rec ? rec.v : []);
  }

  V.practica = () => {
    if (!ses) return atras() || V.inicio();
    let it = ses.cola[ses.i];
    if (!ses.q) {
      ses.q = preparar(it); ses.sel = null; ses.visto = false; ses.bien = false; ses.nose = false; oidoMsj = '';
      if (!ses.q) { ses.i++; if (ses.i >= ses.cola.length) return ir('fin', null, true); return V.practica(); }
    }
    const q = ses.q, t = it.t, corrige = it.tipo === 'c';
    const hechas = Math.min(ses.nuevas, ses.i + (ses.visto && !corrige ? 1 : 0));
    const cuenta = corrige ? 'las que fallaste' : `${ses.i + 1} de ${ses.nuevas}`;
    const real = ses.modo === 'examen';
    cab(`${ses.modo === 'repaso' ? 'Repaso' : real ? ses.titulo : t.t} · ${cuenta}`, corrige ? 1 : hechas / ses.nuevas);
    const arriba = `<div class="fila-top">${corrige ? '<span class="chip nar">Otra vez</span>' : ses.modo === 'repaso' || real ? chip(t.curso) : '<span></span>'}${btnVoz('esc', 'Escuchar')}</div>`;
    let h = `${arriba}<div class="enun">${q.e}</div><div id="ops">${opciones(q, ses.sel, ses.visto)}</div>`;
    if (!ses.visto) {
      h += `${btnMic()}${oidoMsj ? `<p class="oido">${esc(oidoMsj)}</p>` : ''}
        <div class="botones"><button class="btn" id="resp"${ses.sel == null ? ' disabled' : ''}>Comprobar</button><button class="btn txt" id="ns">No lo sé</button></div>`;
    } else {
      const ultima = ses.i === ses.cola.length - 1;
      const titulo = ses.bien ? (ses.elogio || '¡Bien!') : ses.nose ? 'Así se resuelve' : `Era la ${LET[q.c]}`;
      const nota = ses.bien ? (corrige ? 'Ahora sí.' : ses.racha >= 3 ? `${ses.racha} seguidas` : '') : (corrige ? '' : 'Te la pregunto otra vez al final.');
      h += `<div class="veredicto ${ses.bien ? 'ok' : ses.nose ? 'nose' : 'no'}" id="ver"><div class="vt">${titulo}</div>${nota ? `<div class="vn">${esc(nota)}</div>` : ''}</div>
        ${ses.bien ? `<details class="tarjeta porque"><summary>Ver por qué</summary>${explicacion(q)}</details>` : `<div class="tarjeta">${explicacion(q)}</div>`}
        ${q.real ? '<p class="ref">Pregunta del examen real. Solución del equipo (el cuadernillo no trae clave).</p>' : ''}
        ${btnVoz('expl', 'Explicar con voz')}
        <div class="botones"><button class="btn${ses.bien ? ' ok' : ''}" id="sig">${ultima ? 'Ver resultado' : 'Continuar'}</button></div>`;
    }
    app.innerHTML = h; mate();

    const on = (id, fn) => { const b = document.getElementById(id); if (b) b.onclick = fn; };
    const mira = sel => { const v = document.querySelector(sel); if (v) v.scrollIntoView({ behavior: 'smooth', block: 'start' }); };
    if (!ses.visto) app.querySelectorAll('.opc').forEach(b => b.onclick = () => { ses.sel = +b.getAttribute('data-o'); oidoMsj = ''; pintar(); });
    const cierra = (ok, sel) => {
      callar(); oidoMsj = '';
      const primera = !corrige;
      if (primera) {   // el primer intento es el que cuenta para el dominio del tema (y para su repaso)
        if (real) {    // pregunta real: se guarda si salió bien a la primera; la que falla vuelve igual al final
          vistas()[q.id] = ok ? 1 : 0;
          if (!ok) ses.cola.push({ tipo: 'c', t, q: Object.assign({}, q) });
        } else {
          P.anotar(S, t.id, ok, q.i);
          if (ses.modo === 'repaso') { const x = ses.temas[t.id] = ses.temas[t.id] || { ok: 0, n: 0 }; x.n++; if (ok) x.ok++; }
          if (!ok) ses.cola.push({ tipo: 'c', t, dif: it.dif || 2, fija: t.gen ? null : (q.i != null ? q.i : null) });
        }
        guardar();
      }
      ses.visto = true; ses.bien = ok; ses.nose = sel < 0;
      if (ok) {
        ses.racha++; ses.mejor = Math.max(ses.mejor, ses.racha); ses.elogio = ELOGIO[Math.floor(Math.random() * ELOGIO.length)];
        if (primera) ses.ok++; else ses.corr++;
        ev(primera ? 'correcta' : 'corregida');
        if (primera && (ses.racha === 3 || ses.racha === 5)) ev('racha');
      } else { ses.racha = 0; ev(sel < 0 ? 'duda' : 'incorrecta'); }
      pintar(); mira('#ver');
      // La mascota dice el veredicto (corto). La explicación completa se lee con «Explicar con voz».
      if (vozLista() && !callada()) hablar(ok ? ses.elogio : sel < 0 ? 'Mira cómo se resuelve.' : `Era la ${LET[q.c]}.`);
    };
    on('resp', () => { if (ses.sel != null) cierra(ses.sel === q.c, ses.sel); });
    on('ns', () => cierra(false, -1));
    on('esc', () => hablar(vozPregunta(q)));
    on('expl', () => hablar((ses.bien ? '' : `La respuesta es la ${LET[q.c]}. `) + vozSolucion(q)));
    on('mic', escuchar);
    on('sig', () => { callar(); ses.i++; ses.q = null; if (ses.i >= ses.cola.length) return ir('fin', null, true); pintar(); window.scrollTo(0, 0); });
  };

  V.fin = () => {
    if (!ses) return V.inicio();
    const dia = P.hoy();
    if (!ses.cerrada) {   // se programa el próximo repaso una sola vez por sesión
      ses.cerrada = true;
      if (ses.modo === 'tema') ses.rep = P.programar(S, ses.t.id, ses.ok, ses.nuevas, dia);
      else if (ses.modo === 'repaso') Object.keys(ses.temas).forEach(id => P.programar(S, id, ses.temas[id].ok, ses.temas[id].n, dia));
      guardar();
    }
    if (ses.modo === 'examen') {
      cab(ses.titulo, 1);
      const sigue = ses.exId != null && delExamen(ses.exId).length > ses.desde + PARTE;
      app.innerHTML = `<div class="tarjeta centro"><div class="rot" style="margin:0">${ses.ok >= ses.nuevas * 0.8 ? '¡Muy bien!' : ses.ok >= ses.nuevas / 2 ? 'Vas avanzando' : 'A reforzar'}</div><div class="nota">${ses.ok} <small>de ${ses.nuevas} a la primera</small></div>
          <div class="datos"><span>${ses.corr ? `Corregiste ${ses.corr}` : 'Sin errores pendientes'}</span><span>Mejor racha: ${ses.mejor}</span></div></div>
        <div class="botones">${sigue ? `<button class="btn" id="sx">Siguiente parte</button>` : ''}<button class="btn${sigue ? ' sec' : ''}" id="ot">${ses.cursoReal ? 'Otras 10 del curso' : 'Repetir esta parte'}</button><button class="btn txt" id="vx">${ses.cursoReal ? 'Volver al curso' : 'Volver al examen'}</button></div>`;
      if (ses.ok >= ses.nuevas - 1 && !ses.festejo) { ses.festejo = true; ev('logro'); confeti(); }
      const on = (id, fn) => { const b = document.getElementById(id); if (b) b.onclick = fn; };
      const s = ses;
      on('sx', () => { pila.pop(); empezarParte(s.exId, s.desde + PARTE); });
      on('ot', () => { pila.pop(); if (s.cursoReal) empezarCursoReal(s.cursoReal); else empezarParte(s.exId, s.desde); });
      on('vx', () => { ses = null; pila.pop(); pintar(); });
      return;
    }
    if (ses.modo === 'repaso') {
      cab('Repaso de hoy', 1);
      const quedan = P.vencidos(S, dia).length;
      app.innerHTML = `<div class="tarjeta centro"><div class="nota">${ses.ok} <small>de ${ses.nuevas}</small></div><p class="sub" style="margin:2px 0 0">${ses.corr ? `Corregiste ${ses.corr}. ` : ''}${quedan ? `Quedan ${quedan} temas por repasar.` : 'Repaso al día.'}</p></div>
        <div class="botones">${quedan ? '<button class="btn nar" id="otra">Repasar más</button>' : ''}<button class="btn${quedan ? ' sec' : ''}" id="cam">Volver a mi camino</button></div>`;
      if (ses.ok >= ses.nuevas - 1) { ev('logro'); confeti(); }
      const on = (id, fn) => { const b = document.getElementById(id); if (b) b.onclick = fn; };
      on('otra', () => { ses = null; pila = [{ v: 'camino' }]; empezarRepaso(); });
      on('cam', () => { ses = null; pila = []; ir('camino'); });
      return;
    }
    const t = ses.t, m = P.dominio(S, N, t), e = P.estado(m), sig = P.camino(S, N).find(x => x.t.id !== t.id);
    cab(t.t, 1);
    if (m >= 1 && ses.antes < 1 && !ses.festejo) { ses.festejo = true; ev('logro'); confeti(); }
    const dr = P.diasParaRepaso(S, t.id, dia);
    app.innerHTML = `<div class="tarjeta centro"><div class="rot" style="margin:0">${ses.ok >= 4 ? '¡Muy bien!' : ses.ok >= 2 ? 'Vas avanzando' : 'A reforzar'}</div><div class="nota">${ses.ok} <small>de ${ses.nuevas} a la primera</small></div>
        <div class="datos"><span>${ses.corr ? `Corregiste ${ses.corr}` : 'Sin errores pendientes'}</span><span>Mejor racha: ${ses.mejor}</span><span>Repaso ${cuando(dr)}</span></div>
        <div style="margin-top:10px;display:flex;gap:8px;align-items:center;justify-content:center">${chip(t.curso)}${est(e)}</div></div>
      <div class="botones">${m >= 1 && sig ? `<button class="btn" id="otro">Seguir con: ${esc(sig.t.t)}</button><button class="btn sec" id="rep">Practicar otra vez</button>` : `<button class="btn" id="rep">Practicar otra vez</button>`}<button class="btn txt" id="cam">Volver a mi camino</button></div>`;
    const on = (id, fn) => { const b = document.getElementById(id); if (b) b.onclick = fn; };
    on('rep', () => { pila.pop(); empezar(t); });
    on('cam', () => { ses = null; pila = []; ir('camino'); });
    on('otro', () => { ses = null; pila = [{ v: 'camino' }]; abrirTema(sig.t); if (sig.t.nat) pintar(); });
  };

  function pintar() { const top = pila[pila.length - 1]; if (!top) return V.inicio(); try { V[top.v](top.arg); } catch (e) { app.innerHTML = `<div class="tarjeta"><b>Algo falló al mostrar esta pantalla.</b><p class="sub" style="margin:6px 0 12px">${esc(e.message)}</p><button class="btn" onclick="location.reload()">Volver a cargar</button></div>`; } }
  window.caminoEstado = () => ({ S, N, pila: pila.map(p => p.v), ses, diag });   // para pruebas
  window.caminoIr = ir;
  window.caminoVoz = { paraVoz, latexVoz };   // para pruebas
  V.inicio();
})();
