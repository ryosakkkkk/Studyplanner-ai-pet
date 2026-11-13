// Generadores, parte 2: Geometría y Trigonometría.
(function (root) {
  'use strict';
  const F = root.Fig, Gen = root.Gen, G = Gen.G, { armar, numeros } = Gen, { gcd, nn, M, sg, fr, rad, T } = Gen.util;
  const fpi = (a, b, u) => { const g = gcd(a, b) || 1; a /= g; b /= g; return '$' + (b === 1 ? (a === 1 ? '' : a) + T`\pi` : T`\dfrac{${a === 1 ? '' : a}\pi}{${b}}`) + '$' + (u ? ' ' + u : ''); };

  // =====================================================================================  GEOMETRÍA
  G['geo-segmentos'] = (R, d) => {
    if (d <= 1 || R.bool()) {
      const x = R.int(2, 9), y = R.int(2, 9), z = R.int(2, 9), p = x + y, q = y + z, r = x + y + z;
      const fig = F.segmento({ pts: [{ t: 'A', x: 0 }, { t: 'B', x }, { t: 'C', x: x + y }, { t: 'D', x: r }], tramos: [{ de: 0, a: x + y, t: 'AC = ' + p, n: 1, c: 'pri' }, { de: x, a: r, t: 'BD = ' + q, n: 2, c: 'sec' }, { de: 0, a: r, t: 'AD = ' + r, n: 3, c: 'ok' }] });
      const n = numeros(R, y, [r - p, r - q, p - q, p + q, Math.abs(p - q) + 1, x]);
      return armar(R, { e: T`Sobre una recta se ubican los puntos consecutivos $A$, $B$, $C$ y $D$. Si $AC = ${p}$, $BD = ${q}$ y $AD = ${r}$, halla $BC$.`, ok: n.ok, mal: n.mal,
        pasos: ['Se dibuja la recta con los datos:' + fig, T`$AC$ y $BD$ se montan justo en el tramo $BC$. Al sumarlos, $BC$ se cuenta dos veces: $AC + BD = AD + BC$.`, T`$${p} + ${q} = ${r} + BC \Rightarrow BC = ${p + q} - ${r} = ${y}$.`, T`Respuesta: <b>${y}</b>.`], ref: 'Exámenes CPU 2023 (Geometría, segmentos)' });
    }
    const a = 2 * R.int(2, 7), b = R.int(2, 9), c = 2 * R.int(2, 7), mn = a / 2 + b + c / 2;
    const fig = F.segmento({ pts: [{ t: 'A', x: 0 }, { t: 'M', x: a / 2 }, { t: 'B', x: a }, { t: 'C', x: a + b }, { t: 'N', x: a + b + c / 2 }, { t: 'D', x: a + b + c }], tramos: [{ de: 0, a: a, t: '' + a, n: 1, c: 'pri' }, { de: a, a: a + b, t: '' + b, n: 1, c: 'sec' }, { de: a + b, a: a + b + c, t: '' + c, n: 1, c: 'pri' }, { de: a / 2, a: a + b + c / 2, t: 'MN', n: 2, c: 'ok' }] });
    const n = numeros(R, mn, [a + b + c, (a + b + c) / 2, a / 2 + b, b + c / 2, a + b + c / 2, mn + b]);
    return armar(R, { e: T`En una recta están los puntos consecutivos $A$, $B$, $C$ y $D$, con $AB = ${a}$, $BC = ${b}$ y $CD = ${c}$. Si $M$ es punto medio de $AB$ y $N$ es punto medio de $CD$, halla $MN$.`, ok: n.ok, mal: n.mal,
      pasos: ['Se ubican los puntos medios:' + fig, T`$MB = \dfrac{AB}{2} = ${a / 2}$ y $CN = \dfrac{CD}{2} = ${c / 2}$.`, T`$MN = MB + BC + CN = ${a / 2} + ${b} + ${c / 2} = ${mn}$.`, T`Respuesta: <b>${mn}</b>.`], ref: 'Primer Examen CPU 2023 (Geometría, segmentos)' });
  };

  G['geo-paralelas'] = (R, d) => {
    const a = R.int(4, 13) * 5, b = R.int(4, 12) * 5, x = a + b, pide = d >= 2 && R.bool();
    const fig = aux => F.paralelas({ a: Math.min(70, Math.max(30, a)), b: Math.min(70, Math.max(30, b)), ta: pide ? '?' : a + '°', tb: b + '°', tx: pide ? x + '°' : 'x', aux });
    if (!pide) { const n = numeros(R, x, [Math.abs(a - b), 180 - x, 180 - a, 90 + b, 360 - x, x + 10], { fmt: v => '$' + v + '^{\\circ}$', paso: 5 });
      return armar(R, { e: T`En la figura, $L_1 \parallel L_2$. Halla $x$.` + fig(false), ok: n.ok, mal: n.mal,
        pasos: ['Por el vértice del ángulo $x$ se traza una paralela a $L_1$ y $L_2$ (línea punteada):' + fig(true), T`Esa paralela parte a $x$ en dos ángulos. Cada uno es alterno interno con el ángulo de su lado: arriba $${a}^{\circ}$ y abajo $${b}^{\circ}$.`, T`$x = ${a}^{\circ} + ${b}^{\circ} = ${x}^{\circ}$.`, T`Respuesta: <b>${x}°</b>.`], ref: 'Exámenes CPU 2014 y 2023 (Geometría, rectas paralelas)' }); }
    const n = numeros(R, a, [x + b, b, 180 - x, x, 180 - a, a + 5], { fmt: v => '$' + v + '^{\\circ}$', paso: 5 });
    return armar(R, { e: T`En la figura, $L_1 \parallel L_2$. Halla el ángulo marcado con «?».` + fig(false), ok: n.ok, mal: n.mal,
      pasos: ['Se traza por el vértice una paralela a las dos rectas:' + fig(true), T`El ángulo del vértice es la suma de los dos ángulos alternos internos: $${x}^{\circ} = \,? + ${b}^{\circ}$.`, T`$? = ${x}^{\circ} - ${b}^{\circ} = ${a}^{\circ}$. Respuesta: <b>${a}°</b>.`], ref: 'Exámenes CPU 2014 y 2023 (Geometría, rectas paralelas)' });
  };

  // Relaciones métricas en el triángulo rectángulo (altura sobre la hipotenusa)
  G['geo-metricas'] = (R, d) => {
    const [a, b, c] = R.pick([[3, 4, 5], [4, 3, 5], [3, 4, 5], [4, 3, 5], [5, 12, 13], [12, 5, 13]]), m = c === 5 ? R.pick([1, 2, 3]) : 1;
    const AB = a * c * m, BC = b * c * m, AC = c * c * m, AH = a * a * m, HC = b * b * m, BH = a * b * m, t = d >= 2 ? R.int(0, 2) : 0;
    const fig = (l) => F.tri({ P: { A: [0, 0], B: [AH, BH], C: [AC, 0] }, rectos: ['B'], extra: [{ de: 'B', a: [AH, 0], t: l.h || 'h' }], pts: [{ p: [AH, 0], t: 'H' }], lados: l.lados || {}, alt: 'Triángulo rectángulo con la altura sobre la hipotenusa' });
    const u = v => M(v) + ' u';
    if (t === 0) { const n = numeros(R, HC, [AC, AB - AH, BH, AC + AH, Math.round(AB * AB / AH) - 2 * AH, HC + AH], { fmt: u });
      return armar(R, { e: T`En un triángulo $ABC$, recto en $B$, se traza la altura $BH$. Si $AB = ${AB}$ u y $AH = ${AH}$ u, halla $HC$.`, ok: n.ok, mal: n.mal,
        pasos: ['Se dibuja el triángulo con su altura:' + fig({ lados: { AB: '' + AB } }), T`Un cateto al cuadrado es igual a la hipotenusa por su proyección: $AB^{2} = AC \cdot AH$.`, T`$${AB}^{2} = AC \cdot ${AH} \Rightarrow AC = \dfrac{${AB * AB}}{${AH}} = ${AC}$.`, T`$HC = AC - AH = ${AC} - ${AH} = ${HC}$. Respuesta: <b>${HC} u</b>.`], ref: 'Segundo Examen CPU 2023 (Geometría, relaciones métricas)' }); }
    if (t === 1) { const n = numeros(R, BH, [AH + HC, (AH + HC) / 2, AH * HC, Math.abs(HC - AH), BH + m, BH * 2], { fmt: u });
      return armar(R, { e: T`En un triángulo $ABC$, recto en $B$, la altura $BH$ divide a la hipotenusa en $AH = ${AH}$ u y $HC = ${HC}$ u. Halla $BH$.`, ok: n.ok, mal: n.mal,
        pasos: ['Se dibuja el triángulo:' + fig({}), T`La altura al cuadrado es el producto de las proyecciones: $BH^{2} = AH \cdot HC$.`, T`$BH^{2} = ${AH} \cdot ${HC} = ${AH * HC} \Rightarrow BH = \sqrt{${AH * HC}} = ${BH}$.`, T`Respuesta: <b>${BH} u</b>.`], ref: 'Segundo Examen CPU 2023 (Geometría, relaciones métricas)' }); }
    const n = numeros(R, BH, [AC, AB + BC - AC, (AB + BC) / 2, AC / 2, BH + c, BH - a], { fmt: u, ent: false });
    return armar(R, { e: T`Los catetos de un triángulo rectángulo miden ${AB} u y ${BC} u. Halla la altura relativa a la hipotenusa.`, ok: n.ok, mal: n.mal,
      pasos: [T`Hipotenusa por Pitágoras: $AC = \sqrt{${AB}^{2} + ${BC}^{2}} = \sqrt{${AB * AB + BC * BC}} = ${AC}$.` + fig({ lados: { AB: '' + AB, BC: '' + BC } }), T`El área se calcula de dos formas: $\dfrac{AB \cdot BC}{2} = \dfrac{AC \cdot h}{2}$. Entonces $AB \cdot BC = AC \cdot h$.`, T`$h = \dfrac{${AB} \cdot ${BC}}{${AC}} = \dfrac{${AB * BC}}{${AC}} = ${BH}$. Respuesta: <b>${BH} u</b>.`], ref: 'Segundo Examen CPU 2023 (Geometría, relaciones métricas)' });
  };

  G['geo-poligonos'] = (R, d) => {
    const t = d >= 2 ? R.int(0, 4) : R.int(0, 2), g = v => '$' + v + '^{\\circ}$';
    if (t === 0) { const n0 = R.int(5, 14), D = n0 * (n0 - 3) / 2, n = numeros(R, D, [n0 * (n0 - 3), n0 * (n0 - 1) / 2, n0 * (n0 - 2) / 2, n0 - 3, n0 * 2]);
      return armar(R, { e: `¿Cuántas diagonales tiene un polígono de ${n0} lados?`, ok: n.ok, mal: n.mal, pasos: [T`De cada vértice salen $n - 3$ diagonales (no hay diagonal hacia él mismo ni hacia sus dos vecinos).`, T`Con $n$ vértices serían $n(n - 3)$, pero cada diagonal se contó dos veces: $D = \dfrac{n(n - 3)}{2}$.`, T`$D = \dfrac{${n0}(${n0} - 3)}{2} = \dfrac{${n0 * (n0 - 3)}}{2} = ${D}$. Respuesta: <b>${D}</b>.`], ref: 'Segundo Examen CPU 2023 (Geometría, polígonos)' }); }
    if (t === 1) { const n0 = R.pick([5, 6, 8, 9, 10, 12, 15, 18, 20, 24]), e = 360 / n0, n = numeros(R, n0, [180 / e, 360 - e, n0 + 2, n0 - 1, Math.round(e)]);
      return armar(R, { e: T`El ángulo exterior de un polígono regular mide $${e}^{\circ}$. ¿Cuántos lados tiene?`, ok: n.ok, mal: n.mal, pasos: [T`En todo polígono convexo los ángulos exteriores suman $360^{\circ}$.`, T`Si es regular, todos son iguales: $e = \dfrac{360^{\circ}}{n}$.`, T`$n = \dfrac{360^{\circ}}{${e}^{\circ}} = ${n0}$. Respuesta: <b>${n0} lados</b>.`], ref: 'Segundo Examen CPU 2023 (Geometría, polígonos regulares)' }); }
    if (t === 2) { const n0 = R.int(5, 12), S = 180 * (n0 - 2), n = numeros(R, S, [180 * n0, 360, 180 * (n0 - 3), 90 * (n0 - 2), 180 * (n0 - 1)], { fmt: g, paso: 180 });
      return armar(R, { e: `Halla la suma de los ángulos interiores de un polígono de ${n0} lados.`, ok: n.ok, mal: n.mal, pasos: [T`Desde un vértice el polígono se divide en $n - 2$ triángulos, y cada triángulo suma $180^{\circ}$.`, T`$S = 180^{\circ}(n - 2) = 180^{\circ}(${n0} - 2) = 180^{\circ} \cdot ${n0 - 2} = ${S}^{\circ}$.`, T`Respuesta: <b>${S}°</b>.`], ref: 'Exámenes CPU 2014 y 2023 (Geometría, polígonos)' }); }
    if (t === 3) { const n0 = R.int(5, 13), D = n0 * (n0 - 3) / 2, n = numeros(R, n0, [n0 + 1, n0 - 1, n0 + 3, D - n0, n0 + 2]);
      return armar(R, { e: `Un polígono tiene ${D} diagonales. ¿Cuántos lados tiene?`, ok: n.ok, mal: n.mal, pasos: [T`$D = \dfrac{n(n - 3)}{2} = ${D} \Rightarrow n(n - 3) = ${2 * D}$.`, T`Se buscan dos números que se diferencian en 3 y cuyo producto es ${2 * D}: $${n0} \cdot ${n0 - 3} = ${2 * D}$.`, T`$n = ${n0}$. Respuesta: <b>${n0} lados</b>.`], ref: 'Segundo Examen CPU 2023 (Geometría, polígonos)' }); }
    const n0 = R.pick([5, 6, 8, 9, 10, 12, 15, 18, 20]), i = 180 * (n0 - 2) / n0, n = numeros(R, i, [360 / n0, 180 - i / 2, 180 * (n0 - 2), i + 10, i - 12], { fmt: g, paso: 6 });
    return armar(R, { e: `¿Cuánto mide cada ángulo interior de un polígono regular de ${n0} lados?`, ok: n.ok, mal: n.mal, pasos: [T`Suma de ángulos interiores: $180^{\circ}(${n0} - 2) = ${180 * (n0 - 2)}^{\circ}$.`, T`Como es regular, los ${n0} ángulos son iguales: $\dfrac{${180 * (n0 - 2)}^{\circ}}{${n0}} = ${i}^{\circ}$.`, T`Otra forma: el exterior mide $\dfrac{360^{\circ}}{${n0}} = ${360 / n0}^{\circ}$ y el interior es su suplemento. Respuesta: <b>${i}°</b>.`], ref: 'Exámenes CPU 2014 y 2023 (Geometría, polígonos regulares)' });
  };

  G['geo-areas'] = (R, d) => {
    const t = d >= 2 ? R.int(0, 3) : R.int(0, 1), u2 = v => M(v) + ' u²';
    if (t === 0) { const [a, b, c] = R.pick([[3, 4, 5], [6, 8, 10], [5, 12, 13], [9, 12, 15], [8, 15, 17], [12, 16, 20]]), A = a * b / 2, n = numeros(R, A, [a * b, a + b + c, c * a / 2, (a + b) / 2, A + c], { fmt: u2 });
      return armar(R, { e: `La hipotenusa de un triángulo rectángulo mide ${c} u y uno de sus catetos mide ${a} u. Halla el área del triángulo.`, ok: n.ok, mal: n.mal,
        pasos: [T`Falta el otro cateto. Por Pitágoras: $b = \sqrt{${c}^{2} - ${a}^{2}} = \sqrt{${c * c - a * a}} = ${b}$.` + F.tri({ P: { A: [0, 0], B: [0, a], C: [b, 0] }, rectos: ['A'], lados: { AB: '' + a, AC: '' + b, BC: '' + c } }), T`En un triángulo rectángulo los catetos sirven de base y altura: $A = \dfrac{${a} \cdot ${b}}{2} = ${A}$.`, T`Respuesta: <b>${A} u²</b>.`], ref: 'Exámenes CPU 2014 y 2023 (Geometría, áreas)' }); }
    if (t === 1) { const d1 = 2 * R.int(3, 10), d2 = 2 * R.int(2, 8), A = d1 * d2 / 2, n = numeros(R, A, [d1 * d2, d1 + d2, A / 2, 2 * (d1 + d2), A + d1], { fmt: u2 });
      return armar(R, { e: `Las diagonales de un rombo miden ${d1} u y ${d2} u. Halla su área.`, ok: n.ok, mal: n.mal, pasos: [T`Las diagonales de un rombo son perpendiculares y lo dividen en 4 triángulos rectángulos iguales.`, T`$A = \dfrac{D \cdot d}{2} = \dfrac{${d1} \cdot ${d2}}{2} = ${A}$.`, T`Respuesta: <b>${A} u²</b>.`], ref: 'Exámenes CPU 2014 (Geometría, áreas)' }); }
    if (t === 2) { const B = R.int(8, 18), b = R.int(3, B - 2), h = 2 * R.int(2, 6), A = (B + b) * h / 2, n = numeros(R, A, [(B + b) * h, B * h, (B - b) * h / 2, B * b, A + h], { fmt: u2 });
      return armar(R, { e: `Las bases de un trapecio miden ${B} u y ${b} u, y su altura mide ${h} u. Halla su área.`, ok: n.ok, mal: n.mal, pasos: [T`El área del trapecio es la semisuma de las bases por la altura: $A = \dfrac{(B + b)\,h}{2}$.`, T`$A = \dfrac{(${B} + ${b}) \cdot ${h}}{2} = \dfrac{${B + b} \cdot ${h}}{2} = ${A}$.`, T`Respuesta: <b>${A} u²</b>.`], ref: 'Exámenes CPU 2014 (Geometría, áreas)' }); }
    const r = R.pick([3, 4, 6, 8, 9, 12]), ang = R.pick([30, 45, 60, 90, 120]), num = ang * r * r, den = 360, op = (a, b) => fpi(a, b, 'u²');
    return armar(R, { e: T`Halla el área de un sector circular de radio ${r} u y ángulo central de $${ang}^{\circ}$.`, ok: op(num, den), mal: [op(ang * 2 * r, den), op(r * r, 1), op(num, 180), op(ang * r, 180), op(num * 2, den * 3), op(r, 1), op(num + 360, den)],
      pasos: [T`El sector es una fracción del círculo: la fracción es $\dfrac{${ang}^{\circ}}{360^{\circ}} = ${fr(ang, 360)}$.`, T`Área del círculo: $\pi r^{2} = \pi \cdot ${r}^{2} = ${r * r}\pi$.`, T`Área del sector: $${fr(ang, 360)} \cdot ${r * r}\pi = ${op(num, den).replace(/\$/g, '').replace(' u²', '')}$. Respuesta: <b>${op(num, den)}</b>.`], ref: 'Segundo Examen CPU 2023 (Geometría, áreas circulares)' });
  };

  G['geo-solidos'] = (R, d) => {
    const t = d >= 2 ? R.int(0, 3) : R.int(0, 1), u3 = (a, b) => fpi(a, b, 'u³');
    if (t === 0) { const r = R.int(2, 6), h = R.int(3, 10), V = r * r * h;
      return armar(R, { e: `Halla el volumen de un cilindro recto de radio ${r} u y altura ${h} u.`, ok: u3(V, 1), mal: [u3(2 * r * h, 1), u3(V, 3), u3(r * h, 1), u3(2 * V, 1), u3(r * r, 1), u3(V + r, 1)], pasos: [T`Volumen del cilindro: área de la base por la altura, $V = \pi r^{2} h$.`, T`$V = \pi \cdot ${r}^{2} \cdot ${h} = \pi \cdot ${r * r} \cdot ${h} = ${V}\pi$.`, T`Respuesta: <b>${u3(V, 1)}</b>.`], ref: 'Examen Extraordinario CPU 2014 (Geometría, sólidos)' }); }
    if (t === 1) { const a = R.int(2, 7), V = a * a * a, n = numeros(R, V, [6 * a * a, a * a, 3 * a, 12 * a, V + a * a], { fmt: v => M(v) + ' u³' });
      return armar(R, { e: `El área total de un cubo es ${6 * a * a} u². Halla su volumen.`, ok: n.ok, mal: n.mal, pasos: [T`El cubo tiene 6 caras cuadradas iguales: $6a^{2} = ${6 * a * a} \Rightarrow a^{2} = ${a * a} \Rightarrow a = ${a}$.`, T`Volumen: $V = a^{3} = ${a}^{3} = ${V}$.`, T`Respuesta: <b>${V} u³</b>.`], ref: 'Examen Extraordinario CPU 2014 (Geometría, sólidos)' }); }
    if (t === 2) { const r = R.int(2, 6), h = 3 * R.int(1, 4), V = r * r * h / 3;
      return armar(R, { e: `Halla el volumen de un cono recto de radio ${r} u y altura ${h} u.`, ok: u3(V, 1), mal: [u3(3 * V, 1), u3(r * h, 1), u3(V, 2), u3(2 * V, 1), u3(r * r, 1), u3(V + 1, 1)], pasos: [T`El cono ocupa la tercera parte del cilindro con la misma base y altura: $V = \dfrac{\pi r^{2} h}{3}$.`, T`$V = \dfrac{\pi \cdot ${r}^{2} \cdot ${h}}{3} = \dfrac{${r * r * h}\pi}{3} = ${V}\pi$.`, T`Respuesta: <b>${u3(V, 1)}</b>.`], ref: 'Examen Extraordinario CPU 2014 (Geometría, sólidos)' }); }
    const r = 3 * R.int(1, 3), V = 4 * r * r * r / 3;
    return armar(R, { e: `Halla el volumen de una esfera de radio ${r} u.`, ok: u3(V, 1), mal: [u3(4 * r * r, 1), u3(r * r * r, 1), u3(3 * V / 4 * 3, 1), u3(V / 2, 1), u3(4 * r, 1), u3(V + r, 1)], pasos: [T`Volumen de la esfera: $V = \dfrac{4}{3}\pi r^{3}$.`, T`$V = \dfrac{4}{3}\pi \cdot ${r}^{3} = \dfrac{4 \cdot ${r * r * r}}{3}\pi = ${V}\pi$.`, T`Respuesta: <b>${u3(V, 1)}</b>.`], ref: 'Examen Extraordinario CPU 2014 (Geometría, sólidos)' });
  };

  // =====================================================================================  TRIGONOMETRÍA
  G['tri-sistemas'] = (R, d) => {
    const g = v => '$' + v + '^{\\circ}$', t = d >= 2 ? R.int(0, 2) : R.int(0, 1);
    if (t === 0) { const [a, b] = R.pick([[1, 6], [1, 4], [1, 3], [2, 3], [3, 4], [5, 6], [1, 5], [2, 5], [3, 5], [1, 9], [5, 9], [7, 6], [5, 4], [1, 12], [5, 12]]), gr = 180 * a / b, n = numeros(R, gr, [360 * a / b, 90 * a / b, 180 * b / a, gr + 15, gr - 10], { fmt: g, paso: 5 });
      return armar(R, { e: T`Convierte $\dfrac{${a === 1 ? '' : a}\pi}{${b}}$ rad a grados sexagesimales.`, ok: n.ok, mal: n.mal, pasos: [T`La equivalencia base es $\pi \text{ rad} = 180^{\circ}$.`, T`Se reemplaza $\pi$ por $180^{\circ}$: $\dfrac{${a === 1 ? '' : a + T` \cdot `}180^{\circ}}{${b}} = ${gr}^{\circ}$.`, T`Respuesta: <b>${gr}°</b>.`], ref: 'Segundo Examen CPU 2023 (Trigonometría, sistemas de medida)' }); }
    if (t === 1) { const [a, b] = R.pick([[3, 4], [6, 4], [5, 3], [9, 6], [5, 4], [10, 5], [12, 3], [6, 3], [9, 4], [10, 4]]), A = 180 / a, B = 180 / b, C = 180 - A - B, n = numeros(R, C, [A + B, 180 - A, 180 - B, 90 - A, C + 15, C - 10], { fmt: g, paso: 5 });
      return armar(R, { e: T`Dos ángulos de un triángulo miden $\dfrac{\pi}{${a}}$ rad y $\dfrac{\pi}{${b}}$ rad. ¿Cuánto mide el tercer ángulo en grados sexagesimales?`, ok: n.ok, mal: n.mal, pasos: [T`Se pasa cada ángulo a grados con $\pi \text{ rad} = 180^{\circ}$: $\dfrac{180^{\circ}}{${a}} = ${A}^{\circ}$ y $\dfrac{180^{\circ}}{${b}} = ${B}^{\circ}$.`, T`Los tres ángulos de un triángulo suman $180^{\circ}$: $x = 180^{\circ} - ${A}^{\circ} - ${B}^{\circ} = ${C}^{\circ}$.`, T`Respuesta: <b>${C}°</b>.`], ref: 'Segundo Examen CPU 2023 (Trigonometría, sistemas de medida)' }); }
    const gr = R.pick([20, 36, 40, 54, 72, 75, 100, 108, 135, 150, 210, 225, 240, 300]), q = gcd(gr, 180), a = gr / q, b = 180 / q, op = (x, y) => fpi(x, y, 'rad');
    return armar(R, { e: T`Convierte $${gr}^{\circ}$ a radianes.`, ok: op(a, b), mal: [op(b, a), op(2 * a, b), op(a, 2 * b), op(a + 1, b), op(a, b + 1), op(gr, 360 * 2)], pasos: [T`Se multiplica por $\dfrac{\pi \text{ rad}}{180^{\circ}}$: $\;${gr}^{\circ} \cdot \dfrac{\pi}{180^{\circ}} = \dfrac{${gr}\pi}{180}$.`, T`Se simplifica entre ${q}: $\dfrac{${gr}\pi}{180} = ${op(a, b).replace(/\$/g, '').replace(' rad', '')}$.`, T`Respuesta: <b>${op(a, b)}</b>.`], ref: 'Segundo Examen CPU 2023 (Trigonometría, sistemas de medida)' });
  };

  G['tri-identidades'] = (R, d) => {
    const t = d >= 2 ? R.int(0, 2) : R.int(0, 1);
    if (t === 0) { const k = R.pick([2, 3, 5, 6, 7, 10]), b = R.int(1, 4), ok = (1 + b) * (k - 1), n = numeros(R, ok, [k + b, k - 1, b * (k - 1), (1 + b) * k, k * (k - 1), ok + b]);
      return armar(R, { e: T`Si $\sec\theta = \sqrt{${k}}$, con $\theta$ agudo, calcula $P = ${k}\,\text{sen}^{2}\theta + ${b === 1 ? '' : b}\tan^{2}\theta$.`, ok: n.ok, mal: n.mal,
        pasos: [T`De $\sec\theta = \sqrt{${k}}$ sale $\sec^{2}\theta = ${k}$ y $\cos^{2}\theta = \dfrac{1}{${k}}$.`, T`Identidad pitagórica: $\tan^{2}\theta = \sec^{2}\theta - 1 = ${k} - 1 = ${k - 1}$.`, T`$\text{sen}^{2}\theta = 1 - \cos^{2}\theta = 1 - \dfrac{1}{${k}} = \dfrac{${k - 1}}{${k}}$.`, T`$P = ${k} \cdot \dfrac{${k - 1}}{${k}} + ${b}(${k - 1}) = ${k - 1} + ${b * (k - 1)} = ${ok}$. Respuesta: <b>${ok}</b>.`], ref: 'Primer Examen CPU 2023 (Trigonometría, identidades)' }); }
    if (t === 1) { const [o, a, h] = R.pick([[3, 4, 5], [4, 3, 5], [5, 12, 13], [12, 5, 13], [8, 15, 17], [15, 8, 17], [7, 24, 25]]), q = R.bool(), ok = q ? [h + o, a] : [h + a, o], op = (x, y) => '$' + fr(x, y) + '$';
      const fig = F.tri({ P: { A: [0, 0], B: [a, o], C: [a, 0] }, rectos: ['C'], lados: { AB: '' + h, BC: '' + o, AC: '' + a }, ang: { A: 'θ' } });
      return armar(R, { e: T`Si $\text{sen}\,\theta = \dfrac{${o}}{${h}}$, con $\theta$ agudo, calcula $E = ${q ? T`\sec\theta + \tan\theta` : T`\csc\theta + \cot\theta`}$.`, ok: op(ok[0], ok[1]), mal: [op(ok[1], ok[0]), op(h + o, h), op(o + a, h), op(h, q ? a : o), op(q ? h + a : h + o, q ? o : a), op(ok[0] + 1, ok[1]), op(ok[0], ok[1] + 1)],
        pasos: [T`Seno es cateto opuesto sobre hipotenusa. Se arma el triángulo: opuesto ${o}, hipotenusa ${h}. El adyacente sale por Pitágoras: $\sqrt{${h}^{2} - ${o}^{2}} = ${a}$.` + fig, q ? T`$\sec\theta = \dfrac{\text{hip}}{\text{ady}} = \dfrac{${h}}{${a}}$ y $\tan\theta = \dfrac{\text{op}}{\text{ady}} = \dfrac{${o}}{${a}}$.` : T`$\csc\theta = \dfrac{\text{hip}}{\text{op}} = \dfrac{${h}}{${o}}$ y $\cot\theta = \dfrac{\text{ady}}{\text{op}} = \dfrac{${a}}{${o}}$.`, T`$E = \dfrac{${h} + ${q ? o : a}}{${q ? a : o}} = ${fr(ok[0], ok[1])}$. Respuesta: <b>${op(ok[0], ok[1])}</b>.`], ref: 'Primer Examen CPU 2023 (Trigonometría, razones trigonométricas)' }); }
    const banco = [
      { e: T`(\text{sen}\,x + \cos x)^{2} + (\text{sen}\,x - \cos x)^{2}`, ok: '2', p: [T`Se desarrollan los binomios: $(\text{sen}\,x + \cos x)^{2} = \text{sen}^{2}x + 2\,\text{sen}\,x\cos x + \cos^{2}x$ y $(\text{sen}\,x - \cos x)^{2} = \text{sen}^{2}x - 2\,\text{sen}\,x\cos x + \cos^{2}x$.`, T`Al sumar, los términos del medio se cancelan: $2\,\text{sen}^{2}x + 2\cos^{2}x = 2(\text{sen}^{2}x + \cos^{2}x) = 2(1) = 2$.`] },
      { e: T`(1 - \text{sen}^{2}x)\,\sec^{2}x`, ok: '1', p: [T`Por la identidad pitagórica, $1 - \text{sen}^{2}x = \cos^{2}x$.`, T`Como $\sec x = \dfrac{1}{\cos x}$: $\;\cos^{2}x \cdot \dfrac{1}{\cos^{2}x} = 1$.`] },
      { e: T`\tan x \cdot \cos x \cdot \csc x`, ok: '1', p: [T`Todo se pasa a senos y cosenos: $\tan x = \dfrac{\text{sen}\,x}{\cos x}$ y $\csc x = \dfrac{1}{\text{sen}\,x}$.`, T`$\dfrac{\text{sen}\,x}{\cos x} \cdot \cos x \cdot \dfrac{1}{\text{sen}\,x} = 1$.`] },
      { e: T`\sec^{2}x - \tan^{2}x + \csc^{2}x - \cot^{2}x`, ok: '2', p: [T`Identidades pitagóricas: $\sec^{2}x - \tan^{2}x = 1$ y $\csc^{2}x - \cot^{2}x = 1$.`, T`La suma es $1 + 1 = 2$.`] },
      { e: T`\dfrac{1 - \cos^{2}x}{\text{sen}\,x \cdot \cos x} \cdot \cot x`, ok: '1', p: [T`$1 - \cos^{2}x = \text{sen}^{2}x$, así que la fracción queda $\dfrac{\text{sen}^{2}x}{\text{sen}\,x\cos x} = \dfrac{\text{sen}\,x}{\cos x} = \tan x$.`, T`$\tan x \cdot \cot x = 1$.`] },
      { e: T`\text{sen}^{4}x - \cos^{4}x + 2\cos^{2}x`, ok: '1', p: [T`Diferencia de cuadrados: $\text{sen}^{4}x - \cos^{4}x = (\text{sen}^{2}x + \cos^{2}x)(\text{sen}^{2}x - \cos^{2}x) = \text{sen}^{2}x - \cos^{2}x$.`, T`Se suma $2\cos^{2}x$: $\;\text{sen}^{2}x - \cos^{2}x + 2\cos^{2}x = \text{sen}^{2}x + \cos^{2}x = 1$.`] },
    ];
    const it = R.pick(banco);
    return armar(R, { e: T`Simplifica: $E = ${it.e}$`, ok: '$' + it.ok + '$', mal: R.sh(['$0$', '$1$', '$2$', T`$\text{sen}\,x$`, T`$\cos x$`, T`$\tan x$`, '$-1$', T`$2\,\text{sen}\,x$`].filter(x => x !== '$' + it.ok + '$')), pasos: it.p.concat([T`Respuesta: <b>$${it.ok}$</b>.`]), ref: 'Exámenes CPU 2014 y 2023 (Trigonometría, identidades)' });
  };

  // Reducción al primer cuadrante con la circunferencia trigonométrica
  G['tri-cuadrantes'] = (R) => {
    const V = { sen: { 30: [1, 2, 1], 45: [1, 2, 2], 60: [1, 2, 3] }, cos: { 30: [1, 2, 3], 45: [1, 2, 2], 60: [1, 2, 1] }, tan: { 30: [1, 3, 3], 45: [1, 1, 1], 60: [1, 1, 3] } }; // [num, den, radicando]
    const f = R.pick(['sen', 'cos', 'tan']), ref = R.pick([30, 45, 60]), q = R.int(2, 4), ang = q === 2 ? 180 - ref : q === 3 ? 180 + ref : 360 - ref;
    const sig = { sen: [1, 1, -1, -1], cos: [1, -1, -1, 1], tan: [1, -1, 1, -1] }[f][q - 1], nom = { sen: T`\text{sen}`, cos: T`\cos`, tan: T`\tan` }[f];
    const val = (v, s) => '$' + (s < 0 ? '-' : '') + (v[1] === 1 ? rad(v[0], v[2]) : T`\dfrac{${rad(v[0], v[2])}}{${v[1]}}`) + '$';
    const ok = val(V[f][ref], sig), otros = [];
    for (const g of ['sen', 'cos', 'tan']) for (const r of [30, 45, 60]) for (const s of [1, -1]) otros.push(val(V[g][r], s));
    const mal = [val(V[f][ref], -sig)].concat(R.sh(otros.filter(x => x !== ok && x !== val(V[f][ref], -sig))));
    const cuad = ['', 'I', 'II', 'III', 'IV'][q], regla = q === 2 ? T`180^{\circ} - ${ang}^{\circ}` : q === 3 ? T`${ang}^{\circ} - 180^{\circ}` : T`360^{\circ} - ${ang}^{\circ}`;
    return armar(R, { e: T`Calcula $${nom}\,${ang}^{\circ}$.`, ok, mal,
      pasos: [T`$${ang}^{\circ}$ está en el cuadrante ${cuad}.` + F.circulo({ ang, sen: 'sen', cos: 'cos' }), T`Ángulo de referencia: $${regla} = ${ref}^{\circ}$.`, T`En el cuadrante ${cuad}, ${f === 'sen' ? 'el seno (altura del punto)' : f === 'cos' ? 'el coseno (posición horizontal del punto)' : 'la tangente (seno entre coseno)'} es <b>${sig > 0 ? 'positivo' : 'negativo'}</b>.`, T`$${nom}\,${ang}^{\circ} = ${sig < 0 ? '-' : ''}${nom}\,${ref}^{\circ} = ${ok.replace(/\$/g, '')}$. Respuesta: <b>${ok}</b>.`], ref: 'Exámenes CPU 2014 y 2023 (Trigonometría, reducción al primer cuadrante)' });
  };

  // Ley de senos con ángulos notables
  G['tri-senos'] = (R) => {
    // b = a · sen B / sen A  ->  [A, B, múltiplo de a, coeficiente, radicando]: a = mult·k  =>  b = coef·k·√rad
    const casos = [[30, 45, 1, 1, 2], [30, 60, 1, 1, 3], [30, 90, 1, 2, 1], [45, 30, 2, 1, 2], [45, 60, 2, 1, 6], [45, 90, 1, 1, 2], [60, 45, 3, 1, 6], [60, 30, 3, 1, 3], [60, 90, 3, 2, 3]];
    const [A, B, mult, coef, rd] = R.pick(casos), k = R.int(2, 6), a = mult * k, C = 180 - A - B;
    const S = { 30: T`\dfrac{1}{2}`, 45: T`\dfrac{\sqrt{2}}{2}`, 60: T`\dfrac{\sqrt{3}}{2}`, 90: '1' }, sv = x => Math.sin(x * Math.PI / 180);
    const op = (c, r) => '$' + rad(c, r) + '$ u', ok = op(coef * k, rd), cc = sv(C), ca = cc * Math.cos(A * Math.PI / 180), cb = cc * Math.sin(A * Math.PI / 180);
    const fig = F.tri({ P: { A: [0, 0], B: [sv(C) * 10, 0], C: [sv(B) * 10 * Math.cos(A * Math.PI / 180), sv(B) * 10 * Math.sin(A * Math.PI / 180)] }, ang: { A: A + '°', B: B + '°' }, lados: { BC: 'a = ' + a, AC: 'b' } });
    const mal = [op(a, 1), op(coef * k, rd === 2 ? 3 : 2), op(2 * coef * k, rd), op(a, rd === 1 ? 2 : rd), op(coef * k + 1, rd), op(a, 3), op(2 * a, 1), op(coef * k, 6)];
    return armar(R, { e: T`En un triángulo $ABC$ se cumple $\angle A = ${A}^{\circ}$, $\angle B = ${B}^{\circ}$ y $a = ${a}$ u. Halla $b$.`, ok, mal,
      pasos: ['El lado $a$ está frente al ángulo $A$ y el lado $b$ frente al ángulo $B$:' + fig, T`Ley de senos: $\dfrac{a}{\text{sen}\,A} = \dfrac{b}{\text{sen}\,B}$, así que $b = \dfrac{a \cdot \text{sen}\,B}{\text{sen}\,A}$.`, T`$b = \dfrac{${a} \cdot \text{sen}\,${B}^{\circ}}{\text{sen}\,${A}^{\circ}} = \dfrac{${a} \cdot ${S[B]}}{${S[A]}} = ${rad(coef * k, rd)}$.`, T`Respuesta: <b>${ok}</b>.`], ref: 'Exámenes CPU 2023 (Trigonometría, resolución de triángulos)' });
  };
})(typeof window !== 'undefined' ? window : globalThis);
