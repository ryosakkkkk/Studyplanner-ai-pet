// Generadores, parte 4: Biología (genética mendeliana) y Economía (oferta y demanda).
(function (root) {
  'use strict';
  const F = root.Fig, Gen = root.Gen, G = Gen.G, { armar, numeros } = Gen, { nn, M, sg, T } = Gen.util;

  // Cruce monohíbrido con cuadro de Punnett
  G['bio-genetica'] = (R) => {
    const r = R.pick([
      { L: 'A', dom: 'tallo alto', rec: 'tallo enano', ser: 'plantas de arveja', gen: 'el tallo alto (A) domina sobre el tallo enano (a)' },
      { L: 'N', dom: 'pelaje negro', rec: 'pelaje blanco', ser: 'cuyes', gen: 'el pelaje negro (N) domina sobre el blanco (n)' },
      { L: 'R', dom: 'semilla lisa', rec: 'semilla rugosa', ser: 'plantas de arveja', gen: 'la semilla lisa (R) domina sobre la rugosa (r)' },
      { L: 'V', dom: 'semilla amarilla', rec: 'semilla verde', ser: 'plantas de arveja', gen: 'la semilla amarilla (V) domina sobre la verde (v)' },
    ]);
    const D = r.L, d = r.L.toLowerCase(), cruces = [[D + d, D + d], [D + d, d + d], [D + D, D + d], [D + D, d + d]], [p1, p2] = R.pick(cruces);
    const nombre = g => g === D + D ? 'homocigoto dominante' : g === d + d ? 'homocigoto recesivo' : 'heterocigoto';
    const hijos = []; for (const a of p1) for (const b of p2) hijos.push([a, b].sort().join(''));   // mayúscula primero
    const cuenta = g => hijos.filter(h => h === g).length, nRec = cuenta(d + d), nHet = cuenta(D + d), nDom = 4 - nRec;
    const pide = R.int(0, 2), val = [nRec, nDom, nHet][pide] * 25, que = [`tendrá ${r.rec}`, `tendrá ${r.dom}`, 'será heterocigota'][pide];
    const tabla = F.tabla(['', p2[0], p2[1]], [[`<b>${p1[0]}</b>`, hijos[0], hijos[1]], [`<b>${p1[1]}</b>`, hijos[2], hijos[3]]], { th0: true });
    const op = v => v + '\u00a0%';
    return armar(R, { e: `En ${r.ser}, ${r.gen}. Se cruza un individuo ${p1} con uno ${p2}. ¿Qué porcentaje de la descendencia ${que}?`, ok: op(val), mal: [0, 25, 50, 75, 100].filter(v => v !== val).map(op),
      pasos: [`Cada progenitor entrega uno de sus dos alelos. ` + (p1 === p2 ? `Los dos progenitores son ${nombre(p1)}s (${p1}).` : `${p1} es ${nombre(p1)} y ${p2} es ${nombre(p2)}.`), 'Cuadro de Punnett: arriba los alelos de un progenitor, a la izquierda los del otro, y en cada casilla un hijo posible:' + tabla,
        `Genotipos: ${[D + D, D + d, d + d].filter(g => cuenta(g)).map(g => cuenta(g) + ' de 4 son ' + g).join(', ')}. El rasgo recesivo (${r.rec}) solo aparece con dos alelos recesivos (${d + d}).`,
        `${['Con ' + r.rec, 'Con ' + r.dom + ' (al menos un alelo dominante)', 'Heterocigotos (' + D + d + ')'][pide]}: ${[nRec, nDom, nHet][pide]} de 4, es decir <b>${val}\u00a0%</b>.`], ref: 'Exámenes CPU del grupo B (Biología, genética mendeliana)' });
  };

  // Oferta y demanda: equilibrio con su gráfica
  G['ecn-mercado'] = (R, d) => {
    const b = R.int(1, 4), s = R.int(1, 4), P = R.int(3, 12), Q = s * P + R.int(0, 4) * 5, a = Q + b * P, c = Q - s * P;   // Qd = a - bP ; Qo = c + sP
    const dem = T`Q_d = ${a} - ${b === 1 ? '' : b}P`, ofe = T`Q_o = ${c ? c + ' + ' : ''}${s === 1 ? '' : s}P`;
    const qm = a * 1.05, pm = Math.max(a / b, P * 1.8) * 1.02;
    const fig = (extra) => F.plano({ x: [0, qm], y: [0, Math.min(pm, P * 2.4)], ejes: { x: 'Cantidad', y: 'Precio' }, curvas: [{ f: q => (a - q) / b, c: 'pri', t: 'Demanda', tx: Q * 0.35, dy: -8 }, { f: q => (q - c) / s, c: 'sec', t: 'Oferta', tx: Math.min(qm * 0.9, Q + (qm - Q) * 0.75), dy: 14, de: Math.max(c, 0) }], puntos: [{ x: Q, y: P, t: `E (${Q}; ${P})`, dy: -10, c: 'ok' }].concat(extra || []), guias: [[Q, 0, Q, P], [0, P, Q, P]], alt: 'Curvas de oferta y demanda con el punto de equilibrio' });
    const base = [T`En el equilibrio la cantidad demandada es igual a la ofrecida: $Q_d = Q_o$.`, T`$${a} - ${b === 1 ? '' : b}P = ${c ? c + ' + ' : ''}${s === 1 ? '' : s}P \Rightarrow ${a - c} = ${b + s}P \Rightarrow P = ${P}$.`, T`Se reemplaza en cualquiera de las dos: $Q = ${a} - ${b}(${P}) = ${Q}$.` + fig()];
    const t = d >= 2 ? R.int(0, 2) : R.int(0, 1);
    if (t === 0) { const n = numeros(R, P, [Q, a - c, Math.round((a + c) / (b + s)), Math.round(a / b), P + 1, P - 1], { fmt: v => 'S/ ' + nn(v) });
      return armar(R, { e: T`En un mercado, la demanda es $${dem}$ y la oferta es $${ofe}$, con $P$ en soles. Halla el precio de equilibrio.`, ok: n.ok, mal: n.mal, pasos: base.concat([T`Respuesta: <b>S/ ${P}</b>.`]), ref: 'Exámenes CPU 2014 de los grupos A y C (Economía, oferta y demanda)' }); }
    if (t === 1) { const n = numeros(R, Q, [P, a, a - c, Q + b, Q - s, Q + 5], { u: 'unidades' });
      return armar(R, { e: T`En un mercado, la demanda es $${dem}$ y la oferta es $${ofe}$. Halla la cantidad de equilibrio.`, ok: n.ok, mal: n.mal, pasos: base.concat([T`Respuesta: <b>${Q} unidades</b>.`]), ref: 'Exámenes CPU 2014 de los grupos A y C (Economía, oferta y demanda)' }); }
    const P0 = P - R.int(1, Math.min(2, P - 1)), qd = a - b * P0, qo = c + s * P0, falta = qd - qo;
    return armar(R, { e: T`La demanda es $${dem}$ y la oferta es $${ofe}$. Si el precio se fija en S/ ${P0}, por debajo del equilibrio, ¿qué ocurre?`, ok: `Hay escasez de ${falta} unidades`, mal: [`Hay excedente de ${falta} unidades`, `Hay escasez de ${qd} unidades`, 'El mercado queda en equilibrio', `Hay excedente de ${qo} unidades`, `Hay escasez de ${falta + b + s} unidades`, `Hay excedente de ${qd + qo} unidades`, `Hay escasez de ${qd + qo} unidades`],
      pasos: [T`Con $P = ${P0}$: demanda $Q_d = ${a} - ${b}(${P0}) = ${qd}$ y oferta $Q_o = ${c ? c + " + " : ""}${s}(${P0}) = ${qo}$.`, T`Se quiere comprar más de lo que se ofrece: faltan $${qd} - ${qo} = ${falta}$ unidades. Eso es escasez (exceso de demanda).` + fig([{ x: qd, y: P0, t: 'demandan ' + qd, dy: 16, c: 'pri' }, { x: qo, y: P0, t: 'ofrecen ' + qo, dy: 16, dx: -8, a: 'end', c: 'sec' }]), T`El precio de equilibrio es S/ ${P}; por debajo de él siempre hay escasez. Respuesta: <b>escasez de ${falta} unidades</b>.`], ref: 'Exámenes CPU 2014 de los grupos A y C (Economía, oferta y demanda)' });
  };
})(typeof window !== 'undefined' ? window : globalThis);
