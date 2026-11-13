// Figuras para las explicaciones: gráficas, rectas numéricas, diagramas de Venn, triángulos, vectores y tablas.
// Devuelven SVG o HTML como texto. Los colores salen de variables CSS, así sirven en tema claro y oscuro.
(function (root) {
  'use strict';
  const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const r2 = v => Math.round(v * 100) / 100;
  const COL = { pri: 'var(--pri)', sec: 'var(--nar)', ok: 'var(--ok)', mal: 'var(--mal)', tin: 'var(--tinta)', sua: 'var(--suave)' };
  const col = c => COL[c] || c || COL.pri;
  function svg(w, h, body, alt) {
    return `<svg class="fig" viewBox="0 0 ${r2(w)} ${r2(h)}" role="img" aria-label="${esc(alt || 'Figura')}" xmlns="http://www.w3.org/2000/svg">${body}</svg>`;
  }
  function txt(x, y, s, o) {
    o = o || {};
    return `<text x="${r2(x)}" y="${r2(y)}" text-anchor="${o.a || 'middle'}" class="${o.c || 'ft'}"${o.f ? ` fill="${col(o.f)}"` : ''}>${esc(s)}</text>`;
  }
  const num = v => { const s = (Math.round(v * 1000) / 1000).toString().replace('.', ','); return s.replace('-', '−'); };
  function paso(span, n) {
    const bruto = span / (n || 6), p = Math.pow(10, Math.floor(Math.log10(bruto))), m = bruto / p;
    return (m < 1.5 ? 1 : m < 3.5 ? 2 : m < 7.5 ? 5 : 10) * p;
  }

  // ---- plano cartesiano con curvas, polilíneas, áreas y puntos
  function plano(o) {
    const W = o.w || 320, H = o.h || 240, ml = 36, mr = 14, mt = 24, mb = 40;
    const [x0, x1] = o.x, [y0, y1] = o.y;
    const X = x => ml + (x - x0) / (x1 - x0) * (W - ml - mr), Y = y => H - mb - (y - y0) / (y1 - y0) * (H - mt - mb);
    let b = '';
    // Rótulo de un punto: no se sale de la figura (si no cabe a un lado, va al otro). `c` es la caja que ocupa.
    const rot = p => {
      let dx = p.dx != null ? p.dx : 8, a = p.a || 'start'; const w = String(p.t).length * 6.4, x = X(p.x);
      if (a === 'start' && x + dx + w > W - 2) { a = 'end'; dx = -8; } else if (a === 'end' && x + dx - w < 2) { a = 'start'; dx = 8; }
      const lx = x + dx, ly = Math.max(mt + 2, Y(p.y) + (p.dy != null ? p.dy : -8));
      return { lx, ly, a, c: a === 'end' ? [lx - w, lx, ly - 10, ly + 3] : [lx, lx + w, ly - 10, ly + 3] };
    };
    // Los números de los ejes que quedarían debajo de un rótulo no se escriben.
    const rots = (o.puntos || []).filter(p => p.t).map(p => ({ x: X(p.x), e: rot(p) })), cajas = rots.map(r => r.e.c);
    const choca = (xa, xb, ya, yb) => cajas.some(c => xa < c[1] + 3 && xb > c[0] - 3 && ya < c[3] + 1 && yb > c[2] - 1);
    const sx = o.pasoX || paso(x1 - x0), sy = o.pasoY || paso(y1 - y0);
    for (let x = Math.ceil(x0 / sx) * sx; x <= x1 + 1e-9; x += sx) {
      b += `<line x1="${r2(X(x))}" y1="${mt}" x2="${r2(X(x))}" y2="${H - mb}" class="fg"/>`;
      const ty = y0 <= 0 && y1 >= 0 ? Math.min(Y(0) + 13, H - mb + 13) : H - mb + 13, tw = num(x).length * 5.5;
      const mismo = rots.some(r => Math.abs(r.x - X(x)) < 1 && Math.abs(r.e.ly - ty) < 12); // un punto rotulado en esa misma marca
      if ((Math.abs(x) > 1e-9 || y0 > 0 || y1 < 0) && !mismo && !choca(X(x) - tw / 2, X(x) + tw / 2, ty - 9, ty + 2)) b += txt(X(x), ty, num(x), { c: 'fn' });
    }
    for (let y = Math.ceil(y0 / sy) * sy; y <= y1 + 1e-9; y += sy) {
      b += `<line x1="${ml}" y1="${r2(Y(y))}" x2="${W - mr}" y2="${r2(Y(y))}" class="fg"/>`;
      const tx = x0 <= 0 && x1 >= 0 ? Math.max(X(0) - 5, ml - 5) : ml - 5, tw = num(y).length * 5.5;
      if ((Math.abs(y) > 1e-9 || x0 > 0 || x1 < 0) && !choca(tx - tw, tx, Y(y) - 5.5, Y(y) + 5.5)) b += txt(tx, Y(y) + 3.5, num(y), { c: 'fn', a: 'end' });
    }
    const ax = y0 <= 0 && y1 >= 0 ? Y(0) : H - mb, ay = x0 <= 0 && x1 >= 0 ? X(0) : ml;
    b += `<line x1="${ml}" y1="${r2(ax)}" x2="${W - mr}" y2="${r2(ax)}" class="fe"/><line x1="${r2(ay)}" y1="${mt}" x2="${r2(ay)}" y2="${H - mb}" class="fe"/>`;
    if (o.ejes) { b += txt(W - mr, H - 6, o.ejes.x || '', { c: 'fl', a: 'end' }); b += txt(4, 13, o.ejes.y || '', { c: 'fl', a: 'start' }); }
    for (const a of o.areas || []) b += `<polygon points="${a.pts.map(p => r2(X(p[0])) + ',' + r2(Y(p[1]))).join(' ')}" fill="${col(a.c)}" opacity="${a.op || 0.2}"/>`;
    for (const c of o.curvas || []) {
      let d = '', pen = false; const n = 120, a = c.de != null ? c.de : x0, z = c.a != null ? c.a : x1;
      for (let i = 0; i <= n; i++) {
        const x = a + (z - a) * i / n, y = c.f(x);
        if (!isFinite(y) || y < y0 - (y1 - y0) || y > y1 + (y1 - y0)) { pen = false; continue; }
        d += (pen ? 'L' : 'M') + r2(X(x)) + ' ' + r2(Y(y)); pen = true;
      }
      b += `<path d="${d}" fill="none" stroke="${col(c.c)}" class="fc"${c.dash ? ' stroke-dasharray="5 4"' : ''} clip-path="inset(0)"/>`;
      if (c.t) { const xt = c.tx != null ? c.tx : z - (z - a) * 0.12; b += txt(X(xt) + (c.dx || 0), Y(c.f(xt)) + (c.dy || -8), c.t, { c: 'fl', f: c.c }); }
    }
    for (const p of o.poli || []) b += `<polyline points="${p.pts.map(q => r2(X(q[0])) + ',' + r2(Y(q[1]))).join(' ')}" fill="none" stroke="${col(p.c)}" class="fc"${p.dash ? ' stroke-dasharray="5 4"' : ''}/>`;
    for (const l of o.guias || []) b += `<line x1="${r2(X(l[0]))}" y1="${r2(Y(l[1]))}" x2="${r2(X(l[2]))}" y2="${r2(Y(l[3]))}" class="fd"/>`;
    for (const p of o.puntos || []) {
      b += `<circle cx="${r2(X(p.x))}" cy="${r2(Y(p.y))}" r="4" fill="${p.hueco ? 'var(--tarjeta)' : col(p.c || 'sec')}" stroke="${col(p.c || 'sec')}" stroke-width="2"/>`;
      if (p.t) { const e = rot(p); b += txt(e.lx, e.ly, p.t, { c: 'fl', a: e.a }); }
    }
    return `<svg class="fig" viewBox="0 0 ${W} ${H}" role="img" aria-label="${esc(o.alt || 'Gráfica')}" xmlns="http://www.w3.org/2000/svg"><defs><clipPath id="cp${W}${H}"><rect x="${ml}" y="${mt}" width="${W - ml - mr}" height="${H - mt - mb}"/></clipPath></defs>${b.replace(/clip-path="inset\(0\)"/g, `clip-path="url(#cp${W}${H})"`)}</svg>`;
  }

  // ---- recta numérica con intervalos (conjunto solución)
  function recta(o) {
    const W = 320, H = 74, ml = 22, mr = 22, y = 40;
    const X = x => ml + (x - o.min) / (o.max - o.min) * (W - ml - mr);
    let b = `<line x1="${ml - 12}" y1="${y}" x2="${W - mr + 12}" y2="${y}" class="fe"/><path d="M${W - mr + 12} ${y}l-7 -4v8z M${ml - 12} ${y}l7 -4v8z" fill="var(--tinta)"/>`;
    const st = o.paso || paso(o.max - o.min, 8);
    for (let x = Math.ceil(o.min / st) * st; x <= o.max + 1e-9; x += st) b += `<line x1="${r2(X(x))}" y1="${y - 4}" x2="${r2(X(x))}" y2="${y + 4}" class="fe"/>` + txt(X(x), y + 19, num(x), { c: 'fn' });
    for (const it of o.intervalos || []) {
      const a = it.a === -Infinity ? o.min - 0.6 * st : it.a, z = it.b === Infinity ? o.max + 0.6 * st : it.b;
      b += `<line x1="${r2(Math.max(X(a), ml - 12))}" y1="${y}" x2="${r2(Math.min(X(z), W - mr + 12))}" y2="${y}" stroke="${col(it.c)}" stroke-width="5" stroke-linecap="round" opacity=".9"/>`;
      for (const [v, cerr] of [[it.a, it.ca], [it.b, it.cb]]) if (isFinite(v)) b += `<circle cx="${r2(X(v))}" cy="${y}" r="5.5" fill="${cerr ? col(it.c) : 'var(--tarjeta)'}" stroke="${col(it.c)}" stroke-width="2.4"/>`;
    }
    for (const p of o.puntos || []) b += `<circle cx="${r2(X(p.x))}" cy="${y}" r="5" fill="${col(p.c || 'sec')}"/>` + (p.t ? txt(X(p.x), y - 12, p.t, { c: 'fl' }) : '');
    if (o.t) b += txt(W / 2, 14, o.t, { c: 'fl' });
    return svg(W, H, b, o.alt || 'Recta numérica');
  }

  // ---- diagrama de Venn de dos conjuntos
  function venn2(o) {
    const W = 300, H = 170;
    let b = `<rect x="6" y="6" width="${W - 12}" height="${H - 12}" rx="12" class="fr"/>` + txt(18, H - 16, o.U || 'U', { c: 'fn2', a: 'start' });
    b += `<circle cx="118" cy="90" r="62" fill="var(--pri)" opacity=".16"/><circle cx="182" cy="90" r="62" fill="var(--nar)" opacity=".18"/>`;
    b += `<circle cx="118" cy="90" r="62" fill="none" stroke="var(--pri)" stroke-width="2"/><circle cx="182" cy="90" r="62" fill="none" stroke="var(--nar)" stroke-width="2"/>`;
    b += txt(58, 24, o.A || 'A', { c: 'fl', f: 'pri', a: 'middle' }) + txt(242, 24, o.B || 'B', { c: 'fl', f: 'sec', a: 'middle' });
    b += txt(88, 95, o.soloA, { c: 'fv' }) + txt(150, 95, o.ambos, { c: 'fv' }) + txt(212, 95, o.soloB, { c: 'fv' });
    if (o.fuera != null) b += txt(272, 152, o.fuera, { c: 'fv' });
    return svg(W, H, b, o.alt || 'Diagrama de Venn');
  }

  // ---- triángulo con rótulos. P: {A:[x,y],B:[x,y],C:[x,y]} en unidades propias (y hacia arriba)
  function tri(o) {
    const ks = Object.keys(o.P), pts = ks.map(k => o.P[k]).concat((o.extra || []).map(e => e.a)).concat((o.pts || []).map(p => p.p));
    const xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
    const xmin = Math.min(...xs), xmax = Math.max(...xs), ymin = Math.min(...ys), ymax = Math.max(...ys);
    const W = 300, pad = 34, sc = Math.min((W - 2 * pad) / (xmax - xmin || 1), 170 / (ymax - ymin || 1)), H = (ymax - ymin) * sc + 2 * pad;
    const off = (W - (xmax - xmin) * sc) / 2;
    const T = p => [off + (p[0] - xmin) * sc, H - pad - (p[1] - ymin) * sc];
    const cen = T([xs.slice(0, ks.length).reduce((a, v) => a + v, 0) / ks.length, ys.slice(0, ks.length).reduce((a, v) => a + v, 0) / ks.length]);
    const out = (p, d) => { const q = T(p), vx = q[0] - cen[0], vy = q[1] - cen[1], n = Math.hypot(vx, vy) || 1; return [q[0] + vx / n * d, q[1] + vy / n * d]; };
    let b = `<polygon points="${ks.map(k => T(o.P[k]).map(r2).join(',')).join(' ')}" fill="var(--pri)" fill-opacity=".10" stroke="var(--pri)" stroke-width="2.2" stroke-linejoin="round"/>`;
    for (const e of o.extra || []) {
      const p = T(o.P[e.de] || e.de), q = T(e.a);
      b += `<line x1="${r2(p[0])}" y1="${r2(p[1])}" x2="${r2(q[0])}" y2="${r2(q[1])}" stroke="${col(e.c || 'sec')}" stroke-width="2"${e.dash === false ? '' : ' stroke-dasharray="5 4"'}/>`;
      if (e.t) b += txt((p[0] + q[0]) / 2 + (e.dx || 9), (p[1] + q[1]) / 2 + (e.dy || 0), e.t, { c: 'fl', f: e.c || 'sec' });
    }
    for (const k of ks) { const q = out(o.P[k], 13); b += txt(q[0], q[1] + 4, k, { c: 'fl' }); }
    for (const k in (o.lados || {})) {
      const p = T(o.P[k[0]]), q = T(o.P[k[1]]), m = [(p[0] + q[0]) / 2, (p[1] + q[1]) / 2], vx = m[0] - cen[0], vy = m[1] - cen[1], n = Math.hypot(vx, vy) || 1;
      b += txt(m[0] + vx / n * 15, m[1] + vy / n * 15 + 4, o.lados[k], { c: 'fv2' });
    }
    for (const k in (o.ang || {})) {
      const q = T(o.P[k]), vx = cen[0] - q[0], vy = cen[1] - q[1], n = Math.hypot(vx, vy) || 1;
      b += txt(q[0] + vx / n * 27, q[1] + vy / n * 27 + 4, o.ang[k], { c: 'fn2', f: 'sec' });
    }
    for (const k of o.rectos || []) {
      const i = ks.indexOf(k), q = T(o.P[k]), u = T(o.P[ks[(i + 1) % ks.length]]), v = T(o.P[ks[(i + ks.length - 1) % ks.length]]);
      const a = [u[0] - q[0], u[1] - q[1]], c = [v[0] - q[0], v[1] - q[1]], na = Math.hypot(...a), nc = Math.hypot(...c), s = 10;
      const p1 = [q[0] + a[0] / na * s, q[1] + a[1] / na * s], p3 = [q[0] + c[0] / nc * s, q[1] + c[1] / nc * s], p2 = [p1[0] + c[0] / nc * s, p1[1] + c[1] / nc * s];
      b += `<polyline points="${[p1, p2, p3].map(p => p.map(r2).join(',')).join(' ')}" fill="none" stroke="var(--tinta)" stroke-width="1.4"/>`;
    }
    for (const p of o.pts || []) { const q = T(p.p); b += `<circle cx="${r2(q[0])}" cy="${r2(q[1])}" r="3.2" fill="var(--nar)"/>` + txt(q[0] + (p.dx || 0), q[1] + (p.dy || 16), p.t, { c: 'fl' }); }
    return svg(W, H, b, o.alt || 'Triángulo');
  }

  // ---- vectores desde el origen sobre una cuadrícula; res: dibuja la resultante
  function vectores(o) {
    const vs = o.v.slice(); if (o.res) vs.push({ x: o.v.reduce((a, v) => a + v.x, 0), y: o.v.reduce((a, v) => a + v.y, 0), t: o.res === true ? 'R' : o.res, c: 'ok', r: true });
    const m = Math.max(1, ...vs.map(v => Math.max(Math.abs(v.x), Math.abs(v.y)))) * 1.25;
    const W = 260, H = 230, X = x => W / 2 + x / m * (W / 2 - 16), Y = y => H / 2 - y / m * (H / 2 - 16);
    let b = '<defs>' + ['pri', 'sec', 'ok', 'tin'].map(c => `<marker id="fl-${c}" viewBox="0 0 10 10" refX="8.5" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="${col(c)}"/></marker>`).join('') + '</defs>';
    const st = paso(2 * m, 8);
    for (let x = -Math.floor(m / st) * st; x <= m; x += st) b += `<line x1="${r2(X(x))}" y1="8" x2="${r2(X(x))}" y2="${H - 8}" class="fg"/><line x1="8" y1="${r2(Y(x))}" x2="${W - 8}" y2="${r2(Y(x))}" class="fg"/>`;
    b += `<line x1="8" y1="${H / 2}" x2="${W - 8}" y2="${H / 2}" class="fe"/><line x1="${W / 2}" y1="8" x2="${W / 2}" y2="${H - 8}" class="fe"/>`;
    if (o.res && o.v.length === 2) { const [p, q] = o.v; b += `<line x1="${r2(X(p.x))}" y1="${r2(Y(p.y))}" x2="${r2(X(p.x + q.x))}" y2="${r2(Y(p.y + q.y))}" class="fd"/><line x1="${r2(X(q.x))}" y1="${r2(Y(q.y))}" x2="${r2(X(p.x + q.x))}" y2="${r2(Y(p.y + q.y))}" class="fd"/>`; }
    vs.forEach((v, i) => {
      const c = v.c || (i === 0 ? 'pri' : 'sec'), k = COL[c] ? c : 'tin';
      b += `<line x1="${W / 2}" y1="${H / 2}" x2="${r2(X(v.x))}" y2="${r2(Y(v.y))}" stroke="${col(c)}" stroke-width="${v.r ? 3 : 2.4}" marker-end="url(#fl-${k})"/>`;
      const n = Math.hypot(v.x, v.y) || 1;
      if (v.r) b += txt(X(v.x) + v.x / n * 14, Y(v.y) - v.y / n * 14 + 4, v.t || '', { c: 'fl', f: c });
      else { const R = vs[vs.length - 1], lado = (v.x * R.y - v.y * R.x) > 0 ? -1 : 1, px = -v.y / n * lado, py = v.x / n * lado;   // perpendicular en el plano
        b += txt(X(v.x * 0.55) + px * 16 + (v.dx || 0), Y(v.y * 0.55) - py * 16 + 4 + (v.dy || 0), v.t || '', { c: 'fl', f: c }); }
    });
    return svg(W, H, b, o.alt || 'Vectores');
  }

  // ---- circunferencia trigonométrica con un ángulo en posición normal
  function circulo(o) {
    const W = 240, H = 230, cx = 120, cy = 115, R = 84, a = o.ang * Math.PI / 180, px = cx + R * Math.cos(a), py = cy - R * Math.sin(a);
    let b = `<line x1="14" y1="${cy}" x2="${W - 14}" y2="${cy}" class="fe"/><line x1="${cx}" y1="12" x2="${cx}" y2="${H - 12}" class="fe"/><circle cx="${cx}" cy="${cy}" r="${R}" fill="none" stroke="var(--suave)" stroke-width="1.6"/>`;
    b += txt(cx + R * 0.55, cy - R * 0.55 - 26, 'I', { c: 'fn' }) + txt(cx - R * 0.55, cy - R * 0.55 - 26, 'II', { c: 'fn' }) + txt(cx - R * 0.55, cy + R * 0.55 + 34, 'III', { c: 'fn' }) + txt(cx + R * 0.55, cy + R * 0.55 + 34, 'IV', { c: 'fn' });
    const large = ((o.ang % 360) + 360) % 360 > 180 ? 1 : 0, ra = 18;
    b += `<path d="M${cx + ra} ${cy}A${ra} ${ra} 0 ${large} 0 ${r2(cx + ra * Math.cos(a))} ${r2(cy - ra * Math.sin(a))}" fill="none" stroke="var(--nar)" stroke-width="2"/>`;
    b += `<line x1="${cx}" y1="${cy}" x2="${r2(px)}" y2="${r2(py)}" stroke="var(--pri)" stroke-width="2.4"/>`;
    b += `<line x1="${r2(px)}" y1="${r2(py)}" x2="${r2(px)}" y2="${cy}" stroke="var(--ok)" stroke-width="2.4"/><line x1="${cx}" y1="${cy}" x2="${r2(px)}" y2="${cy}" stroke="var(--nar)" stroke-width="2.4"/>`;
    b += `<circle cx="${r2(px)}" cy="${r2(py)}" r="4" fill="var(--pri)"/>`;
    b += txt(px + (Math.cos(a) >= 0 ? 12 : -12), (py + cy) / 2 + 4, o.sen || 'sen', { c: 'fl', f: 'ok', a: Math.cos(a) >= 0 ? 'start' : 'end' });
    b += txt((px + cx) / 2, cy + (Math.sin(a) >= 0 ? 15 : -7), o.cos || 'cos', { c: 'fl', f: 'sec' });
    b += txt(cx + 42 * Math.cos(a / 2), cy - 42 * Math.sin(a / 2) + 4, o.t || (o.ang + '°'), { c: 'fn2', f: 'sec' });
    return svg(W, H, b, o.alt || 'Circunferencia trigonométrica');
  }

  // ---- dos rectas paralelas y una línea quebrada entre ellas (ángulos a arriba, b abajo, x en el vértice)
  function paralelas(o) {
    const W = 300, H = 170, y1 = 32, y2 = 138, vx = 120, vy = 85;
    const ta = Math.tan((o.a || 40) * Math.PI / 180), tb = Math.tan((o.b || 30) * Math.PI / 180);
    const x1 = Math.min(W - 40, vx + (vy - y1) / ta), x2 = Math.min(W - 40, vx + (y2 - vy) / tb);
    let b = `<line x1="14" y1="${y1}" x2="${W - 14}" y2="${y1}" class="fe" style="stroke-width:2"/><line x1="14" y1="${y2}" x2="${W - 14}" y2="${y2}" class="fe" style="stroke-width:2"/>`;
    b += txt(W - 18, y1 - 7, 'L₁', { c: 'fl', a: 'end' }) + txt(W - 18, y2 + 17, 'L₂', { c: 'fl', a: 'end' });
    b += `<polyline points="${r2(x1)},${y1} ${vx},${vy} ${r2(x2)},${y2}" fill="none" stroke="var(--pri)" stroke-width="2.4" stroke-linejoin="round"/>`;
    if (o.aux) b += `<line x1="40" y1="${vy}" x2="${W - 40}" y2="${vy}" class="fd"/>`;
    // Cada rótulo va dentro de su ángulo, tan lejos del vértice como haga falta para no tocar la línea quebrada.
    const ea = (vy - y1) / (x1 - vx), eb = (y2 - vy) / (x2 - vx), an = t => String(t).length * 7.4;
    b += txt(x1 - 19 / ea - an(o.ta) / 2, y1 + 14, o.ta, { c: 'fn2', f: 'sec' }) + txt(x2 - 19 / eb - an(o.tb) / 2, y2 - 6, o.tb, { c: 'fn2', f: 'sec' });
    if (o.aux) b += txt(vx + 19 / ea + an(o.ta) / 2, vy - 6, o.ta, { c: 'fn2', f: 'sec' }) + txt(vx + 19 / eb + an(o.tb) / 2, vy + 14, o.tb, { c: 'fn2', f: 'sec' });
    else b += txt(vx + 24, vy + 4, o.tx, { c: 'fn2', f: 'ok' });
    return svg(W, H, b, o.alt || 'Rectas paralelas');
  }


  // ---- puntos sobre una recta con tramos rotulados. pts: [{t:'A', x:0}], tramos: [{de:0, a:5, t:'5', n:1}] (n = nivel)
  function segmento(o) {
    const xs = o.pts.map(p => p.x), mn = Math.min(...xs), mx = Math.max(...xs), W = 310, ml = 24, y = 34;
    const X = x => ml + (x - mn) / (mx - mn || 1) * (W - 2 * ml), niveles = Math.max(1, ...(o.tramos || []).map(t => t.n || 1)), H = y + 26 + niveles * 24;
    let b = `<line x1="${ml - 12}" y1="${y}" x2="${W - ml + 12}" y2="${y}" class="fe" style="stroke-width:2"/>`;
    for (const p of o.pts) b += `<circle cx="${r2(X(p.x))}" cy="${y}" r="4" fill="var(--pri)"/>` + txt(X(p.x), y - 11, p.t, { c: 'fl' });
    for (const t of o.tramos || []) {
      const yy = y + 4 + (t.n || 1) * 24, a = X(t.de), z = X(t.a);
      b += `<path d="M${r2(a)} ${yy - 7}V${yy}H${r2(z)}V${yy - 7}" fill="none" stroke="${col(t.c || 'sec')}" stroke-width="1.6"/>` + `<rect x="${r2((a + z) / 2 - 16)}" y="${yy - 8}" width="32" height="15" fill="var(--tarjeta)"/>` + txt((a + z) / 2, yy + 4, t.t, { c: 'fn2', f: t.c || 'sec' });
    }
    return svg(W, H, b, o.alt || 'Puntos sobre una recta');
  }
  // ---- tabla HTML (tabla de verdad, cuadro de Punnett). hl: celdas resaltadas "f,c"
  function tabla(cab, filas, o) {
    o = o || {}; const hl = new Set(o.hl || []);
    return `<div class="tabla"><table>${cab ? '<tr>' + cab.map(c => `<th>${c}</th>`).join('') + '</tr>' : ''}${filas.map((f, i) => '<tr>' + f.map((c, j) => `<td class="${hl.has(i + ',' + j) || hl.has(i + ',*') || hl.has('*,' + j) ? 'hl' : ''}${o.th0 && j === 0 ? ' th0' : ''}">${c}</td>`).join('') + '</tr>').join('')}</table></div>`;
  }

  root.Fig = { plano, recta, venn2, tri, vectores, circulo, paralelas, segmento, tabla, num };
})(typeof window !== 'undefined' ? window : globalThis);
