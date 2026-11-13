// Generadores, parte 3: Física, Química y Razonamiento matemático.
(function (root) {
  'use strict';
  const F = root.Fig, Gen = root.Gen, G = Gen.G, { armar, numeros } = Gen, { gcd, lcm, nn, tx, M, sg, fr, rad, T } = Gen.util;

  // =====================================================================================  FÍSICA
  const DIM = { velocidad: [0, 1, -1], aceleración: [0, 1, -2], fuerza: [1, 1, -2], trabajo: [1, 2, -2], energía: [1, 2, -2], potencia: [1, 2, -3], presión: [1, -1, -2], densidad: [1, -3, 0], 'cantidad de movimiento': [1, 1, -1], frecuencia: [0, 0, -1], 'área': [0, 2, 0], volumen: [0, 3, 0], masa: [1, 0, 0], longitud: [0, 1, 0], tiempo: [0, 0, 1] };
  const dimTx = d => { const s = ['M', 'L', 'T'].map((b, i) => d[i] === 0 ? '' : b + (d[i] === 1 ? '' : `^{${d[i]}}`)).join(''); return s || '1'; };
  const DEF = { velocidad: T`v = \dfrac{d}{t}`, aceleración: T`a = \dfrac{v}{t}`, fuerza: T`F = m \cdot a`, trabajo: T`W = F \cdot d`, energía: T`E = \tfrac{1}{2} m v^{2}`, potencia: T`P = \dfrac{W}{t}`, presión: T`p = \dfrac{F}{A}`, densidad: T`\rho = \dfrac{m}{V}`, 'cantidad de movimiento': T`p = m \cdot v`, frecuencia: T`f = \dfrac{1}{t}` };
  G['fis-dimensional'] = (R, d) => {
    if (d <= 1 || R.bool()) {
      const q = R.pick(Object.keys(DEF)), ok = '$' + dimTx(DIM[q]) + '$', todos = [...new Set(Object.values(DIM).map(x => '$' + dimTx(x) + '$'))].filter(x => x !== ok && x !== '$M$' && x !== '$L$' && x !== '$T$');
      const camino = { velocidad: T`$[v] = \dfrac{L}{T} = LT^{-1}$`, aceleración: T`$[a] = \dfrac{LT^{-1}}{T} = LT^{-2}$`, fuerza: T`$[F] = M \cdot LT^{-2} = MLT^{-2}$`, trabajo: T`$[W] = MLT^{-2} \cdot L = ML^{2}T^{-2}$`, energía: T`$[E] = M \cdot (LT^{-1})^{2} = ML^{2}T^{-2}$ (el número $\tfrac{1}{2}$ no tiene dimensión)`, potencia: T`$[P] = \dfrac{ML^{2}T^{-2}}{T} = ML^{2}T^{-3}$`, presión: T`$[p] = \dfrac{MLT^{-2}}{L^{2}} = ML^{-1}T^{-2}$`, densidad: T`$[\rho] = \dfrac{M}{L^{3}} = ML^{-3}$`, 'cantidad de movimiento': T`$[p] = M \cdot LT^{-1} = MLT^{-1}$`, frecuencia: T`$[f] = \dfrac{1}{T} = T^{-1}$` }[q];
      return armar(R, { e: `La ecuación dimensional de la ${q} es:`, ok, mal: R.sh(todos),
        pasos: [T`Se parte de la fórmula que define la magnitud: $${DEF[q]}$.`, T`Se reemplaza cada magnitud por su dimensión (masa $M$, longitud $L$, tiempo $T$): ` + camino + '.', T`Respuesta: <b>${ok}</b>.`], ref: 'Exámenes CPU 2023 (Física, análisis dimensional)' });
    }
    const plantillas = [
      { f: T`F = k \cdot v`, A: 'fuerza', B: [['velocidad', 1]], de: T`$F$ es fuerza y $v$ es velocidad` },
      { f: T`F = k \cdot v^{2}`, A: 'fuerza', B: [['velocidad', 2]], de: T`$F$ es fuerza y $v$ es velocidad` },
      { f: T`F = k \cdot x`, A: 'fuerza', B: [['longitud', 1]], de: T`$F$ es fuerza y $x$ es longitud` },
      { f: T`P = k \cdot v^{3}`, A: 'potencia', B: [['velocidad', 3]], de: T`$P$ es potencia y $v$ es velocidad` },
      { f: T`E = k \cdot v^{2}`, A: 'energía', B: [['velocidad', 2]], de: T`$E$ es energía y $v$ es velocidad` },
      { f: T`p = k \cdot \rho \cdot h`, A: 'presión', B: [['densidad', 1], ['longitud', 1]], de: T`$p$ es presión, $\rho$ es densidad y $h$ es altura` },
      { f: T`W = k \cdot m \cdot h`, A: 'trabajo', B: [['masa', 1], ['longitud', 1]], de: T`$W$ es trabajo, $m$ es masa y $h$ es altura` },
      { f: T`F = k \cdot \dfrac{m_1 \cdot m_2}{d^{2}}`, A: 'fuerza', B: [['masa', 2], ['longitud', -2]], de: T`$F$ es fuerza, $m_1$ y $m_2$ son masas y $d$ es distancia` },
      { f: T`P = k \cdot F \cdot v`, A: 'potencia', B: [['fuerza', 1], ['velocidad', 1]], de: T`$P$ es potencia, $F$ es fuerza y $v$ es velocidad` },
    ];
    const p = R.pick(plantillas), dB = [0, 1, 2].map(i => p.B.reduce((s, [q, e]) => s + DIM[q][i] * e, 0)), dk = DIM[p.A].map((v, i) => v - dB[i]), ok = '$' + dimTx(dk) + '$';
    const mal = [dB, DIM[p.A], dk.map(v => -v), [dk[0], dk[1] + 1, dk[2]], [dk[0], dk[1], dk[2] - 1], [dk[0] + 1, dk[1], dk[2]], [dk[0], dk[1] - 1, dk[2] + 1]].map(x => '$' + dimTx(x) + '$');
    return armar(R, { e: T`La ecuación $${p.f}$ es dimensionalmente correcta, donde ${p.de}. Halla la dimensión de $k$.`, ok, mal,
      pasos: [T`Principio de homogeneidad: los dos lados de la ecuación deben tener la misma dimensión.`, T`Lado izquierdo: $${dimTx(DIM[p.A])}$. Lo que acompaña a $k$: ` + p.B.map(([q, e]) => T`$[\text{${q}}]${e === 1 ? '' : '^{' + e + '}'} = ${e === 1 ? dimTx(DIM[q]) : '(' + dimTx(DIM[q]) + ')^{' + e + '}'}$`).join(', ') + T`, que en conjunto da $${dimTx(dB)}$.`,
        T`Se despeja: $[k] = \dfrac{${dimTx(DIM[p.A])}}{${dimTx(dB)}} = ${dimTx(dk)}$` + (dimTx(dk) === '1' ? ' (no tiene dimensión).' : '.'), T`Respuesta: <b>${ok}</b>.`], ref: 'Exámenes CPU 2023 (Física, análisis dimensional)' });
  };

  G['fis-vectores'] = (R, d) => {
    const t = d >= 2 ? R.int(0, 2) : 0, u = v => M(v) + ' N';
    if (t === 0) { const [a, b, c] = R.pick([[3, 4, 5], [6, 8, 10], [5, 12, 13], [9, 12, 15], [8, 15, 17], [12, 16, 20], [7, 24, 25]]), n = numeros(R, c, [a + b, Math.abs(a - b), a * b, (a + b) / 2, c + 1, c - 2], { fmt: u });
      return armar(R, { e: `Dos fuerzas de ${a} N y ${b} N forman un ángulo de 90°. Halla el módulo de su resultante.`, ok: n.ok, mal: n.mal,
        pasos: ['Se dibujan las fuerzas una a continuación de la otra; la resultante cierra el triángulo:' + F.vectores({ v: [{ x: b, y: 0, t: b + ' N' }, { x: 0, y: a, t: a + ' N' }], res: 'R' }), T`Como son perpendiculares, se aplica Pitágoras: $R = \sqrt{${a}^{2} + ${b}^{2}} = \sqrt{${a * a} + ${b * b}} = \sqrt{${c * c}} = ${c}$.`, T`Respuesta: <b>${c} N</b>.`], ref: 'Primer Examen CPU 2023 (Física, vectores)' }); }
    if (t === 1) { const a = R.int(2, 12), ang = R.pick([60, 120]), ok = ang === 60 ? '$' + rad(a, 3) + '$ N' : '$' + a + '$ N', c = ang === 60 ? 0.5 : -0.5;
      return armar(R, { e: `Dos fuerzas de ${a} N cada una forman un ángulo de ${ang}°. Halla el módulo de la resultante.`, ok, mal: ['$' + 2 * a + '$ N', '$' + rad(a, 2) + '$ N', ang === 60 ? '$' + a + '$ N' : '$' + rad(a, 3) + '$ N', '$' + rad(2 * a, 3) + '$ N', '$0$ N', '$' + rad(a, 5) + '$ N'],
        pasos: [T`Ley del paralelogramo: $R = \sqrt{A^{2} + B^{2} + 2AB\cos\theta}$.` + F.vectores({ v: [{ x: a, y: 0, t: 'A' }, { x: a * c, y: a * Math.sqrt(3) / 2, t: 'B' }], res: 'R' }), T`$R = \sqrt{${a}^{2} + ${a}^{2} + 2(${a})(${a})\cos ${ang}^{\circ}} = \sqrt{${2 * a * a} ${ang === 60 ? '+' : '-'} ${a * a}} = \sqrt{${ang === 60 ? 3 * a * a : a * a}}$, porque $\cos ${ang}^{\circ} = ${ang === 60 ? '' : '-'}\tfrac{1}{2}$.`, T`$R = ${ang === 60 ? rad(a, 3) : a}$. Respuesta: <b>${ok}</b>.`], ref: 'Primer Examen CPU 2023 (Física, vectores)' }); }
    const [a, b, c] = R.pick([[3, 4, 5], [6, 8, 10], [5, 12, 13], [8, 15, 17]]), n = numeros(R, c, [Math.abs(a - b), a + b, a * b, c + 2, c - 1], { fmt: u });
    return armar(R, { e: T`Los vectores $\vec{A}$ y $\vec{B}$ son perpendiculares y sus módulos son ${a} N y ${b} N. Halla el módulo del vector diferencia $\vec{D} = \vec{A} - \vec{B}$.`, ok: n.ok, mal: n.mal,
      pasos: [T`Restar $\vec{B}$ es sumar su opuesto $-\vec{B}$, que tiene el mismo módulo:` + F.vectores({ v: [{ x: a, y: 0, t: 'A' }, { x: 0, y: -b, t: '−B' }], res: 'D' }), T`$\vec{A}$ y $-\vec{B}$ siguen siendo perpendiculares: $D = \sqrt{${a}^{2} + ${b}^{2}} = \sqrt{${c * c}} = ${c}$.`, T`Con vectores perpendiculares, la suma y la diferencia tienen el mismo módulo. Respuesta: <b>${c} N</b>.`], ref: 'Primer Examen CPU 2023 (Física, vectores)' });
  };

  // Gráfica velocidad–tiempo: la distancia es el área bajo la gráfica
  G['fis-vt'] = (R, d) => {
    const t1 = 2 * R.int(1, 3), L = R.int(2, 6), t3d = 2 * R.int(1, 3), v = 2 * R.int(2, 10), t2 = t1 + L, t3 = t2 + t3d;
    const a1 = v * t1 / 2, a2 = v * L, a3 = v * t3d / 2, tot = a1 + a2 + a3, pide = d >= 2 ? R.int(0, 2) : 0;
    const fig = F.plano({ x: [0, t3 + 1], y: [0, v * 1.25], ejes: { x: 't (s)', y: 'v (m/s)' }, areas: [{ pts: [[0, 0], [t1, v], [t2, v], [t3, 0]], c: 'pri' }], poli: [{ pts: [[0, 0], [t1, v], [t2, v], [t3, 0]], c: 'pri' }], guias: [[t1, 0, t1, v], [t2, 0, t2, v]], puntos: [{ x: t1, y: v, t: `(${t1}; ${v})`, dy: -8, dx: -4, a: 'end' }, { x: t2, y: v, t: `(${t2}; ${v})`, dy: -8 }, { x: t3, y: 0, t: `(${t3}; 0)`, dy: -8, dx: 4 }], alt: 'Gráfica velocidad contra tiempo' });
    const base = T`Un móvil parte del reposo. Su velocidad cambia como muestra la gráfica.` + fig;
    if (pide === 0) { const n = numeros(R, tot, [v * t3, a2, a1 + a2, v * t3 / 2, tot + v, tot - a3], { u: 'm' });
      return armar(R, { e: base + 'Halla la distancia total recorrida.', ok: n.ok, mal: n.mal,
        pasos: [T`En una gráfica velocidad–tiempo, la distancia recorrida es el <b>área bajo la gráfica</b>. Se divide en un triángulo, un rectángulo y otro triángulo.`, T`Primer triángulo: $\dfrac{${t1} \cdot ${v}}{2} = ${a1}$. Rectángulo: $${L} \cdot ${v} = ${a2}$. Segundo triángulo: $\dfrac{${t3d} \cdot ${v}}{2} = ${a3}$.`, T`$d = ${a1} + ${a2} + ${a3} = ${tot}$. Respuesta: <b>${tot} m</b>.`], ref: 'Segundo Examen CPU 2023 (Física, gráficas del movimiento)' }); }
    if (pide === 1) { const ok = v / t1, n = numeros(R, ok, [v, v * t1, v / t2, t1, v / t3d, ok + 1], { fmt: x => M(x) + ' m/s²' });
      return armar(R, { e: base + 'Halla la aceleración en el primer tramo.', ok: n.ok, mal: n.mal,
        pasos: [T`En una gráfica velocidad–tiempo, la aceleración es la <b>pendiente</b> de la recta.`, T`En el primer tramo la velocidad pasa de 0 a ${v} m/s en ${t1} s: $a = \dfrac{\Delta v}{\Delta t} = \dfrac{${v} - 0}{${t1} - 0} = ${tx(ok)}$.`, T`Respuesta: <b>${nn(ok)} m/s²</b>.`], ref: 'Segundo Examen CPU 2023 (Física, gráficas del movimiento)' }); }
    const n = numeros(R, a3, [v * t3d, a1, tot, a2, a3 + v], { u: 'm' });
    return armar(R, { e: base + 'Halla la distancia que recorre mientras frena.', ok: n.ok, mal: n.mal,
      pasos: [T`Frena en el último tramo, desde $t = ${t2}$ s hasta $t = ${t3}$ s, donde la velocidad baja de ${v} m/s a 0.`, T`La distancia es el área de ese triángulo: $\dfrac{(${t3} - ${t2}) \cdot ${v}}{2} = \dfrac{${t3d} \cdot ${v}}{2} = ${a3}$.`, T`Respuesta: <b>${a3} m</b>.`], ref: 'Segundo Examen CPU 2023 (Física, gráficas del movimiento)' });
  };

  G['fis-vmedia'] = (R) => {
    const [v1, v2, vm] = R.pick([[30, 60, 40], [20, 30, 24], [40, 60, 48], [60, 90, 72], [30, 45, 36], [20, 60, 30], [40, 120, 60], [10, 15, 12], [60, 120, 80], [45, 90, 60], [12, 24, 16], [15, 30, 20]]);
    const n = numeros(R, vm, [(v1 + v2) / 2, v1 + v2, v2 - v1, Math.round(Math.sqrt(v1 * v2)), vm + 2, vm - 4], { u: 'km/h', ent: false });
    return armar(R, { e: `Un automóvil va de la ciudad A a la ciudad B. Recorre la mitad del camino a ${v1} km/h y la otra mitad a ${v2} km/h. Halla su rapidez media en todo el viaje.`, ok: n.ok, mal: n.mal,
      pasos: [T`La rapidez media es la distancia total entre el tiempo total. No es el promedio de las rapideces, porque el auto pasa más tiempo en el tramo lento.`, T`Sea $d$ la longitud de cada mitad. Tiempos: $t_1 = \dfrac{d}{${v1}}$ y $t_2 = \dfrac{d}{${v2}}$.`,
        T`$v_m = \dfrac{2d}{\dfrac{d}{${v1}} + \dfrac{d}{${v2}}} = \dfrac{2 \cdot ${v1} \cdot ${v2}}{${v1} + ${v2}} = \dfrac{${2 * v1 * v2}}{${v1 + v2}} = ${vm}$.`, T`Respuesta: <b>${vm} km/h</b>.`], ref: 'Primer Examen CPU 2023 (Física, cinemática)' });
  };

  // =====================================================================================  QUÍMICA
  G['qui-gases'] = (R, d) => {
    const t = d >= 2 ? R.int(0, 2) : 0;
    if (t === 0) { const P1 = R.pick([1, 2, 3, 4, 6]), V1 = R.pick([6, 8, 10, 12, 20, 24]), P2 = R.pick([2, 3, 4, 5, 6, 8, 12].filter(p => p !== P1 && (P1 * V1) % p === 0)), V2 = P1 * V1 / P2;
      const k = P1 * V1, vm = Math.max(V1, V2) * 1.25, pm = Math.max(P1, P2) * 1.3;
      const fig = F.plano({ x: [0, vm], y: [0, pm], ejes: { x: 'V (L)', y: 'P (atm)' }, curvas: [{ f: x => k / x, c: 'pri', de: k / pm, a: vm }], puntos: [{ x: V1, y: P1, t: `(${V1} L; ${P1} atm)`, dy: -9 }, { x: V2, y: P2, t: `(${nn(V2)} L; ${P2} atm)`, dy: -9, c: 'ok' }], alt: 'Isoterma: presión contra volumen' });
      const n = numeros(R, V2, [V1 * P2 / P1, V1 + P2 - P1, V1 * P2, V1 / 2, V2 + 2], { u: 'L', ent: false });
      return armar(R, { e: `Un gas ocupa ${V1} L a ${P1} atm. Si la temperatura no cambia, ¿qué volumen ocupa a ${P2} atm?`, ok: n.ok, mal: n.mal,
        pasos: [T`A temperatura constante se cumple la ley de Boyle: $P_1 V_1 = P_2 V_2$. Presión y volumen son inversamente proporcionales.`, T`$${P1} \cdot ${V1} = ${P2} \cdot V_2 \Rightarrow V_2 = \dfrac{${k}}{${P2}} = ${nn(V2)}$.`, 'En la gráfica P–V esa relación es una curva (isoterma): ' + (P2 > P1 ? 'al subir la presión, el volumen baja.' : 'al bajar la presión, el volumen sube.') + fig, T`Respuesta: <b>${nn(V2)} L</b>.`], ref: 'Exámenes CPU 2023 (Química, gases)' }); }
    if (t === 1) { const [c1, k1] = R.pick([[27, 300], [-73, 200], [127, 400]]), [c2, k2] = R.pick([[127, 400], [227, 500], [327, 600], [27, 300]].filter(x => x[1] !== k1)), V1 = R.pick([2, 3, 4, 6]) * (k1 / 100), V2 = V1 * k2 / k1;
      const n = numeros(R, V2, [Math.round(V1 * c2 / c1 * 10) / 10, V1 * k1 / k2, V1 + (k2 - k1) / 100, V1 * 2, V2 + 1], { u: 'L', ent: false });
      return armar(R, { e: `Un gas ocupa ${V1} L a ${c1} °C. A presión constante se lleva a ${c2} °C. ¿Qué volumen ocupa?`, ok: n.ok, mal: n.mal,
        pasos: [T`A presión constante se cumple la ley de Charles: $\dfrac{V_1}{T_1} = \dfrac{V_2}{T_2}$, con la temperatura en kelvin.`, T`$T_1 = ${c1} + 273 = ${k1}$ K y $T_2 = ${c2} + 273 = ${k2}$ K.`, T`$V_2 = \dfrac{V_1 \cdot T_2}{T_1} = \dfrac{${V1} \cdot ${k2}}{${k1}} = ${nn(V2)}$.`, T`Respuesta: <b>${nn(V2)} L</b>. Usar los grados Celsius sin convertir da un resultado equivocado.`], ref: 'Exámenes CPU 2023 (Química, gases)' }); }
    const P1 = R.pick([1, 2, 3]), V1 = R.pick([4, 6, 8, 10]), T1 = 300, T2 = R.pick([450, 600, 900]), P2 = R.pick([1, 2, 3, 4, 6].filter(p => (P1 * V1 * T2) % (p * T1) === 0)), V2 = P1 * V1 * T2 / (P2 * T1);
    const n = numeros(R, V2, [P1 * V1 * T1 / (P2 * T2), P2 * V1 * T2 / (P1 * T1), V1 * T2 / T1, V1 * P1 / P2, V2 + 1], { u: 'L', ent: false });
    return armar(R, { e: `Un gas ocupa ${V1} L a ${P1} atm y 27 °C. ¿Qué volumen ocupa a ${P2} atm y ${T2 - 273} °C?`, ok: n.ok, mal: n.mal,
      pasos: [T`Cambian las tres variables, así que se usa la ley general de los gases: $\dfrac{P_1 V_1}{T_1} = \dfrac{P_2 V_2}{T_2}$.`, T`Temperaturas en kelvin: $T_1 = 27 + 273 = 300$ K y $T_2 = ${T2 - 273} + 273 = ${T2}$ K.`, T`$V_2 = \dfrac{P_1 V_1 T_2}{P_2 T_1} = \dfrac{${P1} \cdot ${V1} \cdot ${T2}}{${P2} \cdot 300} = ${nn(V2)}$.`, T`Respuesta: <b>${nn(V2)} L</b>.`], ref: 'Exámenes CPU 2023 (Química, gases)' });
  };

  // =====================================================================================  RAZONAMIENTO MATEMÁTICO
  G['rm-planteo'] = (R, d) => {
    const t = R.int(0, 2);
    if (t === 0) { const [p, q] = R.pick([[10, 50], [20, 50], [10, 20], [20, 100], [50, 100]]), x = R.int(4, 14), y = R.int(4, 14), n0 = x + y, S = p * x + q * y, n = numeros(R, y, [x, n0 - y + 1, Math.round(S / q), n0 / 2, y + 2, y - 1]);
      return armar(R, { e: `En un sobre hay ${n0} billetes que suman S/ ${nn(S)}. Si solo hay billetes de S/ ${p} y de S/ ${q}, ¿cuántos billetes de S/ ${q} hay?`, ok: n.ok, mal: n.mal,
        pasos: [T`Sea $x$ la cantidad de billetes de S/ ${q}. Entonces hay $${n0} - x$ billetes de S/ ${p}.`, T`El dinero suma: $${q}x + ${p}(${n0} - x) = ${S}$.`, T`$${q}x + ${p * n0} - ${p}x = ${S} \Rightarrow ${q - p}x = ${S - p * n0} \Rightarrow x = ${y}$.`, T`Comprobación: $${y} \cdot ${q} + ${x} \cdot ${p} = ${y * q} + ${x * p} = ${S}$. Respuesta: <b>${y} billetes</b>.`], ref: 'Primer Examen CPU 2023 (Aritmética y razonamiento, planteo de ecuaciones)' }); }
    if (t === 1) { const [a, b] = R.pick([[3, 4], [2, 3], [1, 4], [2, 5], [3, 5], [1, 2], [3, 2]]), k = R.int(2, 12) * 10, no = b * k, gasto = a * k, S = no + gasto, n = numeros(R, gasto, [no, S * a / b, S - S * a / b, S / 2, gasto + 10, gasto - 10], { fmt: v => 'S/ ' + nn(v), paso: 10, ent: false });
      return armar(R, { e: `Un padre le dio S/ ${S} a su hijo. Al preguntarle cuánto gastó, el hijo responde: «Gasté los ${a}/${b} de lo que no gasté». ¿Cuánto gastó?`, ok: n.ok, mal: n.mal,
        pasos: [T`Sea $x$ lo que <b>no</b> gastó. Entonces gastó $\dfrac{${a}}{${b}}x$.`, T`Lo gastado más lo no gastado es el total: $\dfrac{${a}}{${b}}x + x = ${S} \Rightarrow \dfrac{${a + b}}{${b}}x = ${S} \Rightarrow x = ${no}$.`, T`Gastó $${S} - ${no} = ${gasto}$. Respuesta: <b>S/ ${gasto}</b>.`], ref: 'Segundo Examen CPU 2023 (Álgebra, planteo de ecuaciones)' }); }
    const c = R.int(5, 20), g = R.int(5, 25), H = c + g, P = 4 * c + 2 * g, n = numeros(R, c, [g, P / 4, H / 2, P / 2 - H + 2, c + 3, c - 2], { ent: true });
    return armar(R, { e: `En una granja hay gallinas y conejos. En total se cuentan ${H} cabezas y ${P} patas. ¿Cuántos conejos hay?`, ok: n.ok, mal: n.mal,
      pasos: [T`Sea $c$ el número de conejos (4 patas) y $${H} - c$ el de gallinas (2 patas).`, T`Patas: $4c + 2(${H} - c) = ${P} \Rightarrow 2c + ${2 * H} = ${P} \Rightarrow c = ${c}$.`, T`Otra forma: si todos fueran gallinas habría $${2 * H}$ patas. Sobran $${P} - ${2 * H} = ${P - 2 * H}$, y cada conejo aporta 2 patas más: $${P - 2 * H} \div 2 = ${c}$.`, T`Respuesta: <b>${c} conejos</b>.`], ref: 'Exámenes CPU 2023 (Razonamiento matemático, planteo de ecuaciones)' });
  };

  G['rm-edades'] = (R, d) => {
    const nom = R.sh(['Ana', 'Luis', 'Rosa', 'José', 'Carmen', 'Omar', 'Julia', 'Marco']);
    if (d <= 1 || R.bool()) { const k = R.pick([3, 4, 5]), x = R.int(4, 12), S = (k + 1) * x, t = (k - 2) * x, n = numeros(R, t, [x, k * x, S - t, t + x, 2 * x, t - 2]);
      return armar(R, { e: `La edad de ${nom[0]} es ${k} veces la edad de ${nom[1]} y juntos suman ${S} años. ¿Dentro de cuántos años la edad de ${nom[0]} será el doble de la de ${nom[1]}?`, ok: n.ok, mal: n.mal,
        pasos: [T`Edades de hoy: ${nom[1]} tiene $x$ y ${nom[0]} tiene $${k}x$. Suman $${k + 1}x = ${S}$, así que $x = ${x}$.` + F.tabla(['', 'Hoy', 'Dentro de t años'], [[nom[0], k * x, T`$${k * x} + t$`], [nom[1], x, T`$${x} + t$`]], { th0: true }), T`Condición: $${k * x} + t = 2(${x} + t) \Rightarrow ${k * x} + t = ${2 * x} + 2t \Rightarrow t = ${t}$.`, T`Comprobación: tendrán ${k * x + t} y ${x + t} años, y $${k * x + t} = 2 \cdot ${x + t}$. Respuesta: <b>${t} años</b>.`], ref: 'Segundo Examen CPU 2023 (Razonamiento matemático, edades)' }); }
    const k = R.pick([2, 3, 4]), y = R.int(3, 10), h = R.int(2, 8), A = k * y + h, B = y + h, S = A + B, n = numeros(R, A, [B, k * y, S - h, A + h, A - h, S / 2], { ent: true });
    return armar(R, { e: `Hace ${h} años la edad de ${nom[0]} era ${k} veces la edad de ${nom[1]}. Si hoy sus edades suman ${S} años, ¿qué edad tiene ${nom[0]}?`, ok: n.ok, mal: n.mal,
      pasos: [T`Se parte del pasado: hace ${h} años ${nom[1]} tenía $y$ y ${nom[0]} tenía $${k}y$.` + F.tabla(['', `Hace ${h} años`, 'Hoy'], [[nom[0], T`$${k}y$`, T`$${k}y + ${h}$`], [nom[1], '$y$', T`$y + ${h}$`]], { th0: true }), T`Hoy suman ${S}: $(${k}y + ${h}) + (y + ${h}) = ${S} \Rightarrow ${k + 1}y = ${S - 2 * h} \Rightarrow y = ${y}$.`, T`Edad de ${nom[0]} hoy: $${k}(${y}) + ${h} = ${A}$. Respuesta: <b>${A} años</b>.`], ref: 'Primer Examen CPU 2023 (Razonamiento matemático, edades)' });
  };

  G['rm-operadores'] = (R, d) => {
    const ops = [
      { t: T`2a^{2} - 3b`, f: (a, b) => 2 * a * a - 3 * b, s: (a, b) => T`2(${a})^{2} - 3(${b})` },
      { t: T`3a - 2b`, f: (a, b) => 3 * a - 2 * b, s: (a, b) => T`3(${a}) - 2(${b})` },
      { t: T`a^{2} - b`, f: (a, b) => a * a - b, s: (a, b) => T`(${a})^{2} - (${b})` },
      { t: T`ab + a - b`, f: (a, b) => a * b + a - b, s: (a, b) => T`(${a})(${b}) + (${a}) - (${b})` },
      { t: T`2a + b`, f: (a, b) => 2 * a + b, s: (a, b) => T`2(${a}) + (${b})` },
    ];
    if (d <= 1 || R.bool()) { const o = R.pick(ops), a = R.int(2, 4), b = R.int(1, 5), c = R.int(1, 4), r1 = o.f(a, b), r2 = o.f(r1, c), n = numeros(R, r2, [o.f(a, o.f(b, c)), r1, o.f(c, r1), r1 + c, r2 + 3, r2 - 2], { neg: true, cero: true });
      return armar(R, { e: T`Se define $a * b = ${o.t}$. Calcula $(${a} * ${b}) * ${c}$.`, ok: n.ok, mal: n.mal,
        pasos: [T`El operador es una regla: el primer número ocupa el lugar de $a$ y el segundo el de $b$. Primero el paréntesis.`, T`$${a} * ${b} = ${o.s(a, b)} = ${r1}$.`, T`$${r1} * ${c} = ${o.s(r1, c)} = ${r2}$.`, T`Respuesta: <b>${nn(r2)}</b>.`], ref: 'Segundo Examen CPU 2023 (Razonamiento matemático, operadores)' }); }
    const [ca, cb] = R.pick([[3, -2], [2, 1], [2, -3], [4, -1], [5, 2]]), x = R.int(2, 9), c = R.int(1, 6), v = ca * x + cb * c, n = numeros(R, x, [v, v - cb * c, Math.round(v / ca), x + c, x + 1, x - 1], { ent: true });
    return armar(R, { e: T`Se define $m * n = ${ca}m ${sg(cb)}n$. Halla $x$ si $x * ${c} = ${v}$.`.replace('+ 1n', '+ n').replace('- 1n', '- n'), ok: n.ok, mal: n.mal,
      pasos: [T`Se aplica la regla con $m = x$ y $n = ${c}$: $\;${ca}x ${sg(cb * c)} = ${v}$.`, T`$${ca}x = ${v - cb * c} \Rightarrow x = ${x}$.`, T`Respuesta: <b>${x}</b>.`], ref: 'Segundo Examen CPU 2023 (Razonamiento matemático, operadores)' });
  };

  G['rm-fracciones'] = (R) => {
    const sets = [[[1, 2], [1, 4], [1, 5]], [[1, 3], [1, 4], [1, 6]], [[1, 2], [1, 3]], [[2, 5], [1, 3]], [[1, 2], [1, 5], [1, 10]], [[3, 8], [1, 4], [1, 8]], [[1, 3], [1, 6]], [[1, 4], [1, 2], [1, 8]]];
    const fs = R.pick(sets), den = fs.reduce((a, f) => lcm(a, f[1]), 1), usado = fs.reduce((a, f) => a + f[0] * den / f[1], 0), restoN = den - usado, m = R.int(1, 6), total = den * m, resto = restoN * m;
    const cosa = R.pick([['Un granjero reparte sus gallinas entre sus hijos', 'gallinas', 'recibe'], ['Una persona gasta su sueldo', 'soles', 'gasta en'], ['Un comerciante vende su mercadería', 'cajas', 'vende']]);
    const ord = ['primero', 'segundo', 'tercero'], partes = fs.map((f, i) => cosa[2] === 'recibe' ? `el ${ord[i]} recibe ${f[0]}/${f[1]}` : `${i ? 'luego ' : 'primero '}${cosa[2] === 'vende' ? 'vende' : 'gasta'} ${f[0]}/${f[1]}`).join(', ');
    const n = numeros(R, total, [resto * 2, total - resto, resto * den, total + den, total / 2, total * 2], { ent: true });
    return armar(R, { e: `${cosa[0]}: ${partes} del total y ${cosa[2] === 'recibe' ? 'el último recibe' : 'le quedan'} ${resto} ${cosa[1]}. ¿Cuántos ${cosa[1]} había en total?`, ok: n.ok, mal: n.mal,
      pasos: [T`Se suman las fracciones ya repartidas: $` + fs.map(f => T`\dfrac{${f[0]}}{${f[1]}}`).join(' + ') + T` = \dfrac{${usado}}{${den}}$.`, T`Lo que falta es $1 - \dfrac{${usado}}{${den}} = \dfrac{${restoN}}{${den}}$ del total, y eso equivale a ${resto}.`, T`$\dfrac{${restoN}}{${den}} \cdot x = ${resto} \Rightarrow x = \dfrac{${resto} \cdot ${den}}{${restoN}} = ${total}$.`, T`Respuesta: <b>${total}</b>.`], ref: 'Examen de Becas CPU 2024 (Aritmética, fracciones)' });
  };

  G['rm-trabajo'] = (R, d) => {
    const pares = [[3, 6, 2], [4, 12, 3], [6, 12, 4], [10, 15, 6], [12, 24, 8], [20, 30, 12], [10, 40, 8], [15, 30, 10], [5, 20, 4], [9, 18, 6], [8, 24, 6], [6, 30, 5], [21, 28, 12], [12, 36, 9]];
    const [a, b, j] = R.pick(pares);
    if (d <= 1 || R.bool()) { const n = numeros(R, j, [(a + b) / 2, a + b, b - a, Math.round(a * b / (b - a)), j + 1, j - 1], { u: 'días', ent: false });
      return armar(R, { e: `Un albañil hace una obra en ${a} días y su ayudante hace la misma obra en ${b} días. ¿En cuántos días la hacen trabajando juntos?`, ok: n.ok, mal: n.mal,
        pasos: [T`Se mira lo que avanza cada uno en <b>un día</b>: el albañil hace $\dfrac{1}{${a}}$ de la obra y el ayudante $\dfrac{1}{${b}}$.`, T`Juntos, en un día: $\dfrac{1}{${a}} + \dfrac{1}{${b}} = \dfrac{${b / gcd(a, b)} + ${a / gcd(a, b)}}{${lcm(a, b)}} = \dfrac{1}{${j}}$ de la obra.`, T`Si cada día hacen $\dfrac{1}{${j}}$, terminan en ${j} días. Respuesta: <b>${j} días</b>.`], ref: 'Primer Examen CPU 2023 (Razonamiento matemático, trabajo conjunto)' }); }
    const n = numeros(R, b, [a + j, a - j, 2 * j, Math.round(a * j / (a + j)), b + 2, b - 3], { u: 'días', ent: true });
    return armar(R, { e: `Un albañil y su ayudante hacen juntos una obra en ${j} días. El albañil solo la haría en ${a} días. ¿En cuántos días la haría el ayudante solo?`, ok: n.ok, mal: n.mal,
      pasos: [T`En un día, juntos hacen $\dfrac{1}{${j}}$ de la obra y el albañil solo hace $\dfrac{1}{${a}}$.`, T`El ayudante hace la diferencia: $\dfrac{1}{${j}} - \dfrac{1}{${a}} = \dfrac{${a} - ${j}}{${a * j}} = \dfrac{1}{${b}}$.`, T`Si cada día hace $\dfrac{1}{${b}}$, solo tardaría ${b} días. Respuesta: <b>${b} días</b>.`], ref: 'Primer Examen CPU 2023 (Razonamiento matemático, trabajo conjunto)' });
  };

  // Móviles: encuentro y alcance, con la gráfica posición–tiempo
  G['rm-moviles'] = (R, d) => {
    if (d <= 1 || R.bool()) { const v1 = R.int(3, 9) * 10, v2 = R.int(2, 8) * 10, t = R.int(2, 6), D = (v1 + v2) * t, n = numeros(R, t, [D / v1, D / Math.abs(v1 - v2 || 10), D / v2, t + 1, t - 1, 2 * t], { u: 'h', ent: true });
      const fig = F.plano({ x: [0, t * 1.5], y: [0, D * 1.1], ejes: { x: 't (h)', y: 'posición (km)' }, curvas: [{ f: x => v1 * x, c: 'pri' }, { f: x => D - v2 * x, c: 'sec' }], puntos: [{ x: t, y: v1 * t, t: `t = ${t} h`, dy: -12, c: 'ok' }], guias: [[t, 0, t, v1 * t]], alt: 'Posición contra tiempo de dos móviles que se encuentran' });
      return armar(R, { e: `Dos autos parten al mismo tiempo de dos ciudades separadas ${D} km y van uno al encuentro del otro, a ${v1} km/h y ${v2} km/h. ¿Después de cuánto tiempo se encuentran?`, ok: n.ok, mal: n.mal,
        pasos: [T`Como se acercan, la distancia entre ellos se acorta con la suma de las rapideces: $${v1} + ${v2} = ${v1 + v2}$ km cada hora.`, T`Tiempo de encuentro: $t = \dfrac{d}{v_1 + v_2} = \dfrac{${D}}{${v1 + v2}} = ${t}$.`, 'En la gráfica, cada recta es un auto; se cruzan en el momento del encuentro:' + fig, T`Respuesta: <b>${t} h</b>.`], ref: 'Exámenes CPU 2014 y 2023 (Razonamiento matemático, móviles)' }); }
    const v2 = R.int(3, 7) * 10, dv = R.pick([10, 20, 30]), v1 = v2 + dv, t = R.int(2, 6), D = dv * t, n = numeros(R, t, [D / v2, Math.round(D / (v1 + v2) * 10) / 10, D / v1, t + 1, t - 1, 2 * t], { u: 'h', ent: true });
    const fig = F.plano({ x: [0, t * 1.5], y: [0, v1 * t * 1.3], ejes: { x: 't (h)', y: 'posición (km)' }, curvas: [{ f: x => v1 * x, c: 'pri' }, { f: x => D + v2 * x, c: 'sec' }], puntos: [{ x: t, y: v1 * t, t: `t = ${t} h`, dy: -12, dx: -6, a: 'end', c: 'ok' }], guias: [[t, 0, t, v1 * t]], alt: 'Posición contra tiempo: un móvil alcanza a otro' });
    return armar(R, { e: `Un auto va a ${v1} km/h y persigue a un camión que va a ${v2} km/h y le lleva ${D} km de ventaja. ¿En cuánto tiempo lo alcanza?`, ok: n.ok, mal: n.mal,
      pasos: [T`Van en el mismo sentido, así que el auto recorta la ventaja con la diferencia de rapideces: $${v1} - ${v2} = ${dv}$ km cada hora.`, T`Tiempo de alcance: $t = \dfrac{d}{v_1 - v_2} = \dfrac{${D}}{${dv}} = ${t}$.`, 'En la gráfica, la recta más inclinada es el auto; alcanza al camión donde se cruzan:' + fig, T`Respuesta: <b>${t} h</b>.`], ref: 'Exámenes CPU 2014 y 2023 (Razonamiento matemático, móviles)' });
  };

  G['rm-calendario'] = (R, d) => {
    const P = [['ayer', -1], ['anteayer', -2], ['mañana', 1], ['pasado mañana', 2]], dias = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'];
    const n = d >= 2 ? 3 : 2, ws = Array.from({ length: n }, () => R.pick(P)), tot = ws.reduce((a, w) => a + w[1], 0), hoy = R.int(0, 6), res = ((hoy + tot) % 7 + 7) % 7;
    const frase = ws.map((w, i) => (i ? 'del ' : 'el ') + w[0]).join(' ');
    const mal = R.sh(dias.filter((_, i) => i !== res));
    return armar(R, { e: `Si hoy es ${dias[hoy]}, ¿qué día es ${frase}?`, ok: dias[res], mal,
      pasos: [T`Cada palabra mueve la fecha: ayer $= -1$, anteayer $= -2$, mañana $= +1$, pasado mañana $= +2$.`, T`Se suman los movimientos: $` + ws.map(w => (w[1] > 0 ? '+' : '') + w[1]).join(' ') + T` = ${tot > 0 ? '+' : ''}${tot}$.`, tot === 0 ? `El resultado es 0: es el mismo día de hoy.` : `Hay que ${tot > 0 ? 'avanzar' : 'retroceder'} ${Math.abs(tot)} día${Math.abs(tot) === 1 ? '' : 's'} desde el ${dias[hoy]}.`, `Respuesta: <b>${dias[res]}</b>.`], ref: 'Segundo Examen CPU 2023 (Razonamiento matemático, razonamiento lógico)' });
  };
})(typeof window !== 'undefined' ? window : globalThis);
