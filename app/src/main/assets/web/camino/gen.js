// Generadores de preguntas del tipo de los exámenes CPU de la UNAMBA. Cada llamada cambia los datos; la respuesta
// se CALCULA y la explicación sale paso a paso (texto con fórmulas entre $...$ para KaTeX y figuras de fig.js).
// Uso: Gen.crear(temaId, semilla, dificultad) -> { e, o[5], c, pasos[], ref }
(function (root) {
  'use strict';
  const F = root.Fig, T = String.raw;

  // ---------- azar con semilla (xorshift32): la misma semilla da la misma pregunta
  function rng(seed) {
    let s = (seed >>> 0) || 88172645;
    const n = () => { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; };
    for (let i = 0; i < 6; i++) n();
    const R = { n, int: (a, b) => a + Math.floor(n() * (b - a + 1)), pick: a => a[Math.floor(n() * a.length)], bool: () => n() < 0.5 };
    R.sh = a => { a = a.slice(); for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(n() * (i + 1)); const t = a[i]; a[i] = a[j]; a[j] = t; } return a; };
    return R;
  }
  // ---------- números y formato
  const gcd = (a, b) => { a = Math.abs(a); b = Math.abs(b); while (b) { const t = a % b; a = b; b = t; } return a; };
  const lcm = (a, b) => a / gcd(a, b) * b;
  const tx = v => String(v).replace('.', '{,}').replace('-', '-');            // decimal con coma dentro de $...$
  const nn = v => String(Math.round(v * 1000) / 1000).replace('.', ',').replace('-', '−'); // número en texto
  const M = v => '$' + tx(v) + '$';
  const sg = v => (v < 0 ? '- ' + tx(-v) : '+ ' + tx(v));
  function fr(a, b) { // fracción simplificada en TeX
    if (b < 0) { a = -a; b = -b; } const g = gcd(a, b) || 1; a /= g; b /= g;
    return b === 1 ? String(a) : (a < 0 ? '-' : '') + T`\dfrac{${Math.abs(a)}}{${b}}`;
  }
  function pol(cs, v) { // polinomio en TeX, coeficientes de mayor a menor grado
    v = v || 'x'; let s = ''; const n = cs.length - 1;
    cs.forEach((c, i) => {
      if (!c) return; const g = n - i, a = Math.abs(c);
      const cuerpo = (g === 0 ? String(a) : (a === 1 ? '' : a) + v + (g > 1 ? '^{' + g + '}' : ''));
      s += (s ? (c < 0 ? ' - ' : ' + ') : (c < 0 ? '-' : '')) + cuerpo;
    });
    return s || '0';
  }
  const rad = (k, r) => r === 1 ? String(k) : (k === 1 ? '' : k) + T`\sqrt{${r}}`;   // k√r
  // ---------- armado de alternativas
  function numeros(R, ok, cands, o) { // respuesta numérica con distractores de errores típicos y relleno
    o = o || {}; const fmt = o.fmt || (v => M(v) + (o.u ? ' ' + o.u : '')), vistos = new Set([ok]), mal = [];
    for (const c of cands) { if (c == null || !isFinite(c) || vistos.has(c) || (!o.neg && c < 0) || (o.ent !== false && c !== Math.round(c)) || (!o.cero && c === 0 && ok !== 0)) continue; vistos.add(c); mal.push(c); if (mal.length === 4) break; }
    const st = o.paso || 1;
    for (let k = 1; mal.length < 4 && k < 60; k++) for (const v of [ok + k * st, ok - k * st]) if (mal.length < 4 && !vistos.has(v) && (o.neg || v > 0)) { vistos.add(v); mal.push(v); }
    return { ok: fmt(ok), mal: mal.map(fmt) };
  }
  function armar(R, q) { // q: {e, ok, mal[], pasos[], ref}
    const mal = []; const vistos = new Set([q.ok]);
    for (const m of q.mal) if (!vistos.has(m)) { vistos.add(m); mal.push(m); }
    if (mal.length < 4) throw new Error('faltan alternativas: ' + q.e);
    const orden = R.sh([q.ok].concat(mal.slice(0, 4)));
    const pasos = q.pasos.slice(); pasos[pasos.length - 1] = pasos[pasos.length - 1].replace(/<\/b>\.\s*$/, '</b>');   // sin punto suelto tras una fórmula
    return { e: q.e, o: orden, c: orden.indexOf(q.ok), pasos, ref: q.ref || '' };
  }
  const G = {};

  // =====================================================================================  ARITMÉTICA
  // Lógica proposicional: una proposición compuesta falsa fija el valor de cada variable.
  G['ari-logica'] = (R) => {
    const formas = [
      { t: T`(p \wedge \sim q) \rightarrow (r \vee s)`, v: { p: 1, q: 0, r: 0, s: 0 }, por: T`el antecedente $p \wedge \sim q$ es verdadero y el consecuente $r \vee s$ es falso` },
      { t: T`(p \wedge q) \rightarrow (\sim r \vee s)`, v: { p: 1, q: 1, r: 1, s: 0 }, por: T`el antecedente $p \wedge q$ es verdadero y el consecuente $\sim r \vee s$ es falso` },
      { t: T`(\sim p \vee q) \vee (r \rightarrow s)`, v: { p: 1, q: 0, r: 1, s: 0 }, por: T`las dos partes son falsas: $\sim p \vee q$ es falsa y $r \rightarrow s$ es falsa` },
      { t: T`(p \rightarrow q) \vee (\sim r \rightarrow s)`, v: { p: 1, q: 0, r: 0, s: 0 }, por: T`las dos condicionales son falsas` },
      { t: T`\sim s \rightarrow [(p \wedge r) \rightarrow q]`, v: { p: 1, q: 0, r: 1, s: 0 }, por: T`$\sim s$ es verdadera y $(p \wedge r) \rightarrow q$ es falsa` },
    ];
    const f = R.pick(formas), v = f.v, L = ['p', 'q', 'r', 's'];
    const lit = () => { const x = R.pick(L), neg = R.n() < 0.35; return { t: (neg ? T`\sim ` : '') + x, val: neg ? 1 - v[x] : v[x] }; };
    const ops = [{ s: T`\wedge`, f: (a, b) => a & b }, { s: T`\vee`, f: (a, b) => a | b }, { s: T`\rightarrow`, f: (a, b) => (a && !b) ? 0 : 1 }, { s: T`\leftrightarrow`, f: (a, b) => a === b ? 1 : 0 }];
    const ex = []; const usados = new Set();
    while (ex.length < 3) { const a = lit(), b = lit(), o = R.pick(ops); const t = a.t + ' ' + o.s + ' ' + b.t; if (a.t.slice(-1) === b.t.slice(-1) || usados.has(t)) continue; usados.add(t); ex.push({ t, val: o.f(a.val, b.val), a, b, o }); }
    const VF = x => x ? 'V' : 'F', ok = ex.map(x => VF(x.val)).join('');
    const todos = ['VVV', 'VVF', 'VFV', 'VFF', 'FVV', 'FVF', 'FFV', 'FFF'].filter(x => x !== ok);
    return armar(R, {
      e: T`Si la proposición $${f.t}$ es <b>falsa</b>, halla los valores de verdad de:<br>I. $${ex[0].t}$ &nbsp; II. $${ex[1].t}$ &nbsp; III. $${ex[2].t}$`,
      ok, mal: R.sh(todos),
      pasos: [
        T`Una condicional $A \rightarrow B$ solo es falsa cuando $A$ es verdadera y $B$ es falsa. Una disyunción $A \vee B$ solo es falsa cuando las dos son falsas.`,
        T`Como $${f.t}$ es falsa, ${f.por}.`,
        T`De ahí sale cada variable: ` + L.map(x => T`$${x} = ${VF(v[x])}$`).join(', ') + '.',
        'Ahora se reemplaza en cada expresión:' + F.tabla(['Expresión', 'Valores', 'Resultado'], ex.map((x, i) => ['$' + x.t + '$', T`$${VF(x.a.val)} \; ${x.o.s} \; ${VF(x.b.val)}$`, '<b>' + VF(x.val) + '</b>'])),
        `Respuesta: <b>${ok}</b>.`,
      ],
      ref: 'Examen de Becas CPU 2024 y Primer Examen CPU 2009 (Aritmética, lógica proposicional)',
    });
  };

  // Conjuntos con dos grupos: diagrama de Venn
  G['ari-conjuntos'] = (R, d) => {
    const cosas = R.pick([['fútbol', 'vóley', 'practican', 'deportistas'], ['inglés', 'quechua', 'hablan', 'estudiantes'], ['Matemática', 'Física', 'aprobaron', 'postulantes'], ['radio', 'periódico', 'prefieren', 'personas'], ['café', 'té', 'toman', 'vecinos']]);
    const ambos = R.int(4, 15), soloA = R.int(6, 25), soloB = R.int(5, 22), fuera = R.int(3, 14), U = ambos + soloA + soloB + fuera, a = soloA + ambos, b = soloB + ambos;
    const tipo = d >= 2 ? R.int(0, 3) : R.int(0, 1), venn = F.venn2({ A: cosas[0], B: cosas[1], soloA, ambos, soloB, fuera, U: 'Total: ' + U });
    let e, ok, cands, pasos;
    const base = T`De ${U} ${cosas[3]}, ${a} ${cosas[2]} ${cosas[0]}, ${b} ${cosas[2]} ${cosas[1]}`;
    if (tipo === 0) {
      e = base + ` y ${ambos} las dos cosas. ¿Cuántos no ${cosas[2]} ninguna de las dos?`; ok = fuera; cands = [U - a - b, U - a - b + 2 * ambos, a + b - ambos, soloA + soloB, U - ambos];
      pasos = [T`Los que están en al menos un grupo: $n(A \cup B) = n(A) + n(B) - n(A \cap B) = ${a} + ${b} - ${ambos} = ${a + b - ambos}$. Se resta la intersección porque esas ${ambos} personas se contaron dos veces.`,
        'El diagrama lo muestra por regiones:' + venn, T`Fuera de los dos conjuntos quedan $${U} - ${a + b - ambos} = ${fuera}$.`];
    } else if (tipo === 1) {
      e = base + ` y ${fuera} ninguna de las dos. ¿Cuántos ${cosas[2]} las dos cosas?`; ok = ambos; cands = [a + b - U, U - a - b + fuera, Math.abs(a - b), a + b - U - fuera, soloA];
      pasos = [T`Los que están en al menos un grupo son $${U} - ${fuera} = ${U - fuera}$.`,
        T`Se usa $n(A \cup B) = n(A) + n(B) - n(A \cap B)$: $\;${U - fuera} = ${a} + ${b} - n(A \cap B)$.`, T`Entonces $n(A \cap B) = ${a + b} - ${U - fuera} = ${ambos}$.` + venn];
    } else if (tipo === 2) {
      e = base + ` y ${ambos} las dos cosas. ¿Cuántos ${cosas[2]} solo ${cosas[0]}?`; ok = soloA; cands = [a, soloB, a + ambos, a - fuera, U - b];
      pasos = [T`«Solo ${cosas[0]}» es la parte de $A$ que no está en $B$: $n(A) - n(A \cap B) = ${a} - ${ambos} = ${soloA}$.` + venn];
    } else {
      e = base + ` y ${ambos} las dos cosas. ¿Cuántos ${cosas[2]} solo una de las dos?`; ok = soloA + soloB; cands = [a + b, a + b - ambos, U - ambos, soloA, a + b - 2 * ambos + fuera];
      pasos = [T`Solo ${cosas[0]}: $${a} - ${ambos} = ${soloA}$. Solo ${cosas[1]}: $${b} - ${ambos} = ${soloB}$.` + venn, T`Solo una de las dos: $${soloA} + ${soloB} = ${soloA + soloB}$.`];
    }
    pasos.push(T`Respuesta: <b>${ok}</b>.`);
    const n = numeros(R, ok, cands);
    return armar(R, { e, ok: n.ok, mal: n.mal, pasos, ref: 'Examen de Becas CPU 2024 y exámenes CPU 2023 (Aritmética, conjuntos)' });
  };

  // Sistemas de numeración
  G['ari-numeracion'] = (R, d) => {
    if (d <= 1 || R.n() < 0.3) { // de base b a base 10
      const b = R.pick([3, 4, 5, 6, 7, 8]), ds = [R.int(1, b - 1), R.int(0, b - 1), R.int(0, b - 1)], N = ds[0] * b * b + ds[1] * b + ds[2], s = ds.join('');
      const n = numeros(R, N, [ds[0] * b + ds[1] * b + ds[2], ds[0] * 100 + ds[1] * 10 + ds[2], ds[0] * b * b + ds[1] + ds[2], ds[2] * b * b + ds[1] * b + ds[0], N + b, N - b]);
      return armar(R, { e: T`Expresa en base 10 el número $\overline{${s}}_{(${b})}$.`, ok: n.ok, mal: n.mal,
        pasos: [T`Descomposición polinómica: cada cifra se multiplica por una potencia de la base.`, T`$\overline{${s}}_{(${b})} = ${ds[0]} \cdot ${b}^{2} + ${ds[1]} \cdot ${b} + ${ds[2]}$`,
          T`$= ${ds[0]} \cdot ${b * b} + ${ds[1] * b} + ${ds[2]} = ${ds[0] * b * b} + ${ds[1] * b} + ${ds[2]} = ${N}$`, T`Respuesta: <b>${N}</b>.`], ref: 'Exámenes CPU 2009 y 2024 (Aritmética, sistemas de numeración)' });
    }
    // hallar la base: ab(n) = N con a n + b = N  ó  abc(n) = N
    const n0 = R.int(5, 9), a = R.int(1, 3), b = R.int(0, n0 - 1), c = R.int(0, n0 - 1), N = a * n0 * n0 + b * n0 + c, s = '' + a + b + c;
    const disc = b * b + 4 * a * (N - c), rz = Math.round(Math.sqrt(disc));
    const n = numeros(R, n0, [Math.max(a, b, c) + 1, n0 + 1, n0 - 1, n0 + 2, 10]);
    return armar(R, { e: T`Halla la base $n$ si $\overline{${s}}_{(n)} = ${N}$.`, ok: n.ok, mal: n.mal,
      pasos: [T`Descomposición polinómica en base $n$: $\;${a === 1 ? '' : a}n^{2} ${b ? '+ ' + (b === 1 ? '' : b) + 'n' : ''} ${c ? '+ ' + c : ''} = ${N}$.`,
        T`Se ordena como ecuación cuadrática: $\;${pol([a, b, c - N], 'n')} = 0$.`,
        T`Fórmula general: $n = \dfrac{-${b} \pm \sqrt{${b}^{2} + 4 \cdot ${a} \cdot ${N - c}}}{2 \cdot ${a}} = \dfrac{-${b} \pm ${rz}}{${2 * a}}$. La raíz positiva es $n = ${n0}$.`,
        T`Se comprueba que toda cifra sea menor que la base: la mayor cifra es ${Math.max(a, b, c)} y $${Math.max(a, b, c)} < ${n0}$. Respuesta: <b>${n0}</b>.`], ref: 'Exámenes CPU 2009 y 2024 (Aritmética, sistemas de numeración)' });
  };

  // Cantidad de divisores
  G['ari-divisores'] = (R, d) => {
    const pr = R.sh([2, 3, 5, 7]).slice(0, d >= 2 ? 3 : 2).sort((x, y) => x - y), ex = pr.map((p, i) => R.int(1, p === 2 ? 4 : p === 3 ? 3 : 2));
    const N = pr.reduce((a, p, i) => a * Math.pow(p, ex[i]), 1), CD = ex.reduce((a, e) => a * (e + 1), 1), k = pr.length;
    const canon = pr.map((p, i) => `${p}^{${ex[i]}}`).join(T` \cdot `), prod = ex.map(e => `(${e} + 1)`).join('');
    const tipo = d >= 2 ? R.int(0, 2) : 0, ok = tipo === 0 ? CD : tipo === 1 ? CD - k : CD - k - 1;
    const que = ['divisores', 'divisores que no son primos', 'divisores compuestos'][tipo];
    const n = numeros(R, ok, [ex.reduce((a, e) => a * e, 1), ex.reduce((a, e) => a + e, 0), CD, CD - k, CD - k - 1, CD + 1, CD - 1]);
    const pasos = [T`Descomposición canónica: $${N} = ${canon}$.`, T`Cantidad de divisores: se suma 1 a cada exponente y se multiplica. $CD = ${prod} = ${CD}$.`];
    if (tipo === 1) pasos.push(T`Los divisores primos son ${k} (${pr.join(', ')}). No primos: $${CD} - ${k} = ${CD - k}$ (aquí se cuenta también el 1).`);
    if (tipo === 2) pasos.push(T`Se quitan los ${k} divisores primos (${pr.join(', ')}) y el 1, que no es primo ni compuesto: $${CD} - ${k} - 1 = ${ok}$.`);
    pasos.push(T`Respuesta: <b>${ok}</b>.`);
    return armar(R, { e: T`¿Cuántos ${que} tiene el número ${N}?`, ok: n.ok, mal: n.mal, pasos, ref: 'Examen de Becas CPU 2024 (Aritmética, divisores)' });
  };

  // Razones y proporciones: mezclas y reparto proporcional
  G['ari-proporciones'] = (R, d) => {
    if (R.bool()) { // mezcla: de a:b a c:d agregando agua
      const [a, b] = R.pick([[5, 3], [3, 2], [4, 1], [7, 3], [2, 1], [5, 2], [3, 1]]), k = R.int(3, 12), alc = a * k, agua = b * k, V = alc + agua;
      const [c, e2] = R.pick([[1, 1], [5, 4], [3, 4], [2, 3], [4, 5], [1, 2], [9, 10]].filter(([c, e]) => c * b < a * e && (alc * e) % c === 0));
      const agua2 = alc * e2 / c, x = agua2 - agua;
      const n = numeros(R, x, [agua2, V * e2 / (c + e2) - agua, alc - agua, agua, Math.round(V / (c + e2))], { u: 'litros' });
      return armar(R, { e: T`En un recipiente hay ${V} litros de una mezcla de alcohol y agua en la relación de ${a} a ${b}. ¿Cuántos litros de agua hay que agregar para que la relación sea de ${c} a ${e2}?`, ok: n.ok, mal: n.mal,
        pasos: [T`Las partes son $${a}k$ de alcohol y $${b}k$ de agua: $${a}k + ${b}k = ${V} \Rightarrow k = ${k}$. Hay <b>${alc}</b> litros de alcohol y <b>${agua}</b> litros de agua.`,
          T`Al agregar $x$ litros de agua, el alcohol no cambia: $\dfrac{${alc}}{${agua} + x} = \dfrac{${c}}{${e2}}$.`,
          T`Producto en cruz: $${alc} \cdot ${e2} = ${c}(${agua} + x) \Rightarrow ${alc * e2} = ${c * agua} + ${c}x \Rightarrow x = ${x}$.`, T`Respuesta: <b>${x} litros</b>.`], ref: 'Examen Extraordinario CPU 2014 (Aritmética, razones y proporciones)' });
    }
    const ps = R.pick([[2, 3, 5], [1, 2, 3], [3, 4, 5], [2, 5, 7], [1, 3, 4], [4, 5, 6], [2, 3, 7]]), k = R.int(4, 30) * 5, S = k * (ps[0] + ps[1] + ps[2]), pide = R.int(0, 2);
    const ok = [ps[2] * k, ps[0] * k, (ps[2] - ps[0]) * k][pide], txt = ['la parte mayor', 'la parte menor', 'la diferencia entre la parte mayor y la menor'][pide];
    const n = numeros(R, ok, [ps[1] * k, ps[2] * k, ps[0] * k, S / 3, k, (ps[2] - ps[0]) * k], { fmt: v => 'S/ ' + nn(v) });
    return armar(R, { e: T`Se reparte S/ ${nn(S)} en partes directamente proporcionales a ${ps[0]}, ${ps[1]} y ${ps[2]}. Halla ${txt}.`, ok: n.ok, mal: n.mal,
      pasos: [T`Las partes son $${ps[0]}k$, $${ps[1]}k$ y $${ps[2]}k$. Su suma es el total: $${ps[0]}k + ${ps[1]}k + ${ps[2]}k = ${S}$.`, T`$${ps[0] + ps[1] + ps[2]}k = ${S} \Rightarrow k = ${k}$.`,
        T`Partes: $${ps[0]} \cdot ${k} = ${ps[0] * k}$, $\;${ps[1]} \cdot ${k} = ${ps[1] * k}$, $\;${ps[2]} \cdot ${k} = ${ps[2] * k}$.`, T`Respuesta: <b>S/ ${nn(ok)}</b>.`], ref: 'Exámenes CPU 2014 y 2023 (Aritmética, magnitudes proporcionales)' });
  };

  // =====================================================================================  ÁLGEBRA
  // Polinomios: valor numérico, P(P(x)), suma de coeficientes y término independiente
  G['alg-polinomios'] = (R, d) => {
    if (d <= 1 || R.n() < 0.4) {
      const a = R.int(2, 4), b = R.int(-5, 6) || 1, k = R.int(1, 4), p1 = a * k + b, p2 = a * p1 + b;
      const n = numeros(R, p2, [p1, a * k + b + a * k + b, a * a * k + b, p1 * p1, a * p1, p2 + a, p2 - a], { neg: true, cero: true });
      return armar(R, { e: T`Si $P(x) = ${pol([a, b])}$, calcula $P(P(${k}))$.`, ok: n.ok, mal: n.mal,
        pasos: [T`Se trabaja de adentro hacia afuera. Primero $P(${k}) = ${a}(${k}) ${sg(b)} = ${p1}$.`, T`Luego $P(P(${k})) = P(${p1}) = ${a}(${p1}) ${sg(b)} = ${p2}$.`, T`Respuesta: <b>${nn(p2)}</b>.`], ref: 'Examen de Becas CPU 2024 (Álgebra, valor numérico)' });
    }
    const cs = [R.int(1, 4), R.int(-6, 6), R.int(-6, 6), R.int(-7, 7) || 2], sc = cs.reduce((x, y) => x + y, 0), ti = cs[3], pide = R.bool();
    const ok = pide ? sc + ti : sc - ti;
    const n = numeros(R, ok, [sc, ti, sc * ti, pide ? sc - ti : sc + ti, cs[0] + ti, sc + 1], { neg: true, cero: true });
    return armar(R, { e: T`En $P(x) = ${pol(cs)}$, calcula la suma de coeficientes ${pide ? 'más' : 'menos'} el término independiente.`, ok: n.ok, mal: n.mal,
      pasos: [T`La suma de coeficientes es $P(1)$: $\;P(1) = ${cs.map((c, i) => (i ? (c < 0 ? '- ' + -c : '+ ' + c) : c)).join(' ')} = ${sc}$.`, T`El término independiente es $P(0) = ${ti}$.`,
        T`Entonces $${sc} ${pide ? '+' : '-'} (${ti}) = ${ok}$. Respuesta: <b>${nn(ok)}</b>.`], ref: 'Exámenes CPU 2023 y 2024 (Álgebra, polinomios)' });
  };

  // Productos notables: x + 1/x, a + b y ab
  G['alg-notables'] = (R, d) => {
    const t = d >= 2 ? R.int(0, 3) : R.int(0, 1);
    if (t === 0) { const k = R.int(3, 9), ok = k * k - 2; const n = numeros(R, ok, [k * k, k * k + 2, 2 * k, k * k - 1, k * k - 4]);
      return armar(R, { e: T`Si $x + \dfrac{1}{x} = ${k}$, calcula $x^{2} + \dfrac{1}{x^{2}}$.`, ok: n.ok, mal: n.mal,
        pasos: [T`Se eleva al cuadrado: $\left(x + \dfrac{1}{x}\right)^{2} = x^{2} + 2 \cdot x \cdot \dfrac{1}{x} + \dfrac{1}{x^{2}} = x^{2} + 2 + \dfrac{1}{x^{2}}$.`, T`Entonces $${k}^{2} = x^{2} + \dfrac{1}{x^{2}} + 2 \Rightarrow x^{2} + \dfrac{1}{x^{2}} = ${k * k} - 2 = ${ok}$.`, T`Respuesta: <b>${ok}</b>.`], ref: 'Examen de Becas CPU 2024 (Álgebra, productos notables)' }); }
    if (t === 1) { const s = R.int(5, 12), p = R.int(2, Math.floor(s * s / 4) - 1), ok = s * s - 2 * p; const n = numeros(R, ok, [s * s, s * s + 2 * p, s * s - p, 2 * s - p, s * s - 4 * p]);
      return armar(R, { e: T`Si $a + b = ${s}$ y $ab = ${p}$, calcula $a^{2} + b^{2}$.`, ok: n.ok, mal: n.mal,
        pasos: [T`Binomio al cuadrado: $(a + b)^{2} = a^{2} + 2ab + b^{2}$.`, T`Se despeja: $a^{2} + b^{2} = (a + b)^{2} - 2ab = ${s}^{2} - 2(${p}) = ${s * s} - ${2 * p} = ${ok}$.`, T`Respuesta: <b>${ok}</b>.`], ref: 'Exámenes CPU 2014 y 2024 (Álgebra, productos notables)' }); }
    if (t === 2) { const k = R.int(2, 5), ok = k * k + 2; const n = numeros(R, ok, [k * k, k * k - 2, 2 * k, k * k + 4, k * k + 1]);
      return armar(R, { e: T`Si $x - \dfrac{1}{x} = ${k}$, calcula $x^{2} + \dfrac{1}{x^{2}}$.`, ok: n.ok, mal: n.mal,
        pasos: [T`Al cuadrado: $\left(x - \dfrac{1}{x}\right)^{2} = x^{2} - 2 + \dfrac{1}{x^{2}}$. El término del medio es $-2$ porque $x \cdot \dfrac{1}{x} = 1$.`, T`$${k * k} = x^{2} + \dfrac{1}{x^{2}} - 2 \Rightarrow x^{2} + \dfrac{1}{x^{2}} = ${ok}$.`, T`Respuesta: <b>${ok}</b>.`], ref: 'Examen de Becas CPU 2024 (Álgebra, productos notables)' }); }
    const k = R.int(3, 6), ok = k * k * k - 3 * k; const n = numeros(R, ok, [k * k * k, k * k * k + 3 * k, k * k - 2, k * k * k - 3, k * k * k - k]);
    return armar(R, { e: T`Si $x + \dfrac{1}{x} = ${k}$, calcula $x^{3} + \dfrac{1}{x^{3}}$.`, ok: n.ok, mal: n.mal,
      pasos: [T`Binomio al cubo: $\left(x + \dfrac{1}{x}\right)^{3} = x^{3} + \dfrac{1}{x^{3}} + 3 \cdot x \cdot \dfrac{1}{x}\left(x + \dfrac{1}{x}\right)$.`, T`$${k}^{3} = x^{3} + \dfrac{1}{x^{3}} + 3(${k}) \Rightarrow x^{3} + \dfrac{1}{x^{3}} = ${k * k * k} - ${3 * k} = ${ok}$.`, T`Respuesta: <b>${ok}</b>.`], ref: 'Examen de Becas CPU 2024 (Álgebra, productos notables)' });
  };

  // Teorema del resto y división exacta
  G['alg-resto'] = (R, d) => {
    const a = R.pick([-3, -2, -1, 1, 2, 3]), nz = (lo, hi) => { let v = 0; while (!v) v = R.int(lo, hi); return v; }, cs = [R.int(1, 3), nz(-4, 4), nz(-5, 5), nz(-6, 6)], ev = c => c[0] * a * a * a + c[1] * a * a + c[2] * a + c[3];
    const div = a < 0 ? `x + ${-a}` : `x - ${a}`, sus = cs.map((c, i) => `${i ? (c < 0 ? '- ' : '+ ') : (c < 0 ? '-' : '')}${Math.abs(c)}${i < 3 ? `(${a})${i < 2 ? '^{' + (3 - i) + '}' : ''}` : ''}`).join(' ');
    if (d <= 1 || R.bool()) { const r = ev(cs); const otra = cs[0] * (-a) ** 3 + cs[1] * a * a + cs[2] * (-a) + cs[3];
      const n = numeros(R, r, [otra, cs.reduce((x, y) => x + y, 0), cs[3], r + a, r - a, -r], { neg: true, cero: true });
      return armar(R, { e: T`Halla el resto de dividir $P(x) = ${pol(cs)}$ entre $${div}$.`, ok: n.ok, mal: n.mal,
        pasos: [T`Teorema del resto: el resto de dividir $P(x)$ entre $x - a$ es $P(a)$. Aquí el divisor se anula con $x = ${a}$.`, T`$P(${a}) = ${sus}$`, T`$= ${cs[0] * a ** 3} ${sg(cs[1] * a * a)} ${sg(cs[2] * a)} ${sg(cs[3])} = ${r}$.`, T`Respuesta: <b>${nn(r)}</b>.`], ref: 'Exámenes CPU 2023 y 2024 (Álgebra, división de polinomios)' }); }
    const sin = cs[0] * a ** 3 + cs[1] * a * a + cs[2] * a, k = -sin, n = numeros(R, k, [sin, k + a, k - a, cs[0] + cs[1] + cs[2], -k + 1, k + 1], { neg: true, cero: true });
    return armar(R, { e: T`Halla $k$ para que la división de $P(x) = ${pol([cs[0], cs[1], cs[2], 0])} + k$ entre $${div}$ sea exacta.`, ok: n.ok, mal: n.mal,
      pasos: [T`División exacta significa resto cero. Por el teorema del resto, $P(${a}) = 0$.`, T`$P(${a}) = ${cs[0]}(${a})^{3} ${sg(cs[1])}(${a})^{2} ${sg(cs[2])}(${a}) + k = ${cs[0] * a ** 3} ${sg(cs[1] * a * a)} ${sg(cs[2] * a)} + k = ${sin} + k$.`, T`$${sin} + k = 0 \Rightarrow k = ${k}$. Respuesta: <b>${nn(k)}</b>.`], ref: 'Examen de Becas CPU 2024 (Álgebra, división exacta)' });
  };

  // Factorización de trinomios
  G['alg-factorizacion'] = (R, d) => {
    let p = R.int(-7, 8), q = R.int(-7, 8); if (!p) p = 3; if (!q) q = -2; if (p === q) q = p + 1 || 2;
    const b = p + q, c = p * q, f1 = pol([1, p]), f2 = pol([1, q]), suma = pol([2, b]);
    const mal = [pol([2, -b]), pol([2, c]), pol([1, b]), pol([2, b + 1]), pol([2, b - 1]), pol([1, c])].map(s => '$' + s + '$');
    return armar(R, { e: T`Factoriza $${pol([1, b, c])}$ e indica la suma de sus factores primos.`, ok: '$' + suma + '$', mal,
      pasos: [T`Aspa simple: se buscan dos números que multiplicados den $${c}$ y sumados den $${b}$.`, T`Esos números son $${p}$ y $${q}$: $\;(${p})(${q}) = ${c}$ y $\;${p} ${sg(q)} = ${b}$.`,
        T`Entonces $${pol([1, b, c])} = (${f1})(${f2})$.`, T`Suma de factores: $(${f1}) + (${f2}) = ${suma}$. Respuesta: <b>$${suma}$</b>.`], ref: 'Exámenes CPU 2023 y 2024 (Álgebra, factorización)' });
  };

  // Inecuaciones con valor absoluto y cuadráticas: recta numérica
  G['alg-inecuaciones'] = (R, d) => {
    const t = d >= 2 ? R.int(0, 2) : 0;
    if (t === 0) { const a = R.pick([-4, -3, -2, -1, 1, 2, 3, 4, 5, 6]), b = R.int(2, 9), lo = a - b, hi = a + b, cuenta = hi - lo - 1, pide = R.bool();
      const fig = F.recta({ min: lo - 2, max: hi + 2, intervalos: [{ a: lo, b: hi, c: 'pri' }], t: `x está entre ${nn(lo)} y ${nn(hi)}` });
      if (pide) { const n = numeros(R, cuenta, [cuenta + 2, cuenta + 1, 2 * b, b, cuenta - 1]);
        return armar(R, { e: T`¿Cuántos números enteros cumplen $|x ${sg(-a)}| < ${b}$?`, ok: n.ok, mal: n.mal,
          pasos: [T`$|u| < b$ equivale a $-b < u < b$. Aquí: $-${b} < x ${sg(-a)} < ${b}$.`, T`Se ${a >= 0 ? 'suma' : 'resta'} ${Math.abs(a)} en los tres lados: $${lo} < x < ${hi}$.` + fig, T`Los extremos no entran (desigualdad estricta). Enteros: desde ${nn(lo + 1)} hasta ${nn(hi - 1)}, que son $${hi - 1} - (${lo + 1}) + 1 = ${cuenta}$.`, T`Respuesta: <b>${cuenta}</b>.`], ref: 'Examen de Becas CPU 2024 (Álgebra, valor absoluto)' }); }
      const I = (x, y) => T`$\langle ${x};\, ${y} \rangle$`;
      return armar(R, { e: T`Resuelve $|x ${sg(-a)}| < ${b}$.`, ok: I(lo, hi), mal: [I(-hi, -lo), I(-b, b), I(lo, b), T`$[${lo};\, ${hi}]$`, I(a, hi), I(lo - 1, hi + 1)],
        pasos: [T`$|u| < b$ equivale a $-b < u < b$. Aquí: $-${b} < x ${sg(-a)} < ${b}$.`, T`Se ${a >= 0 ? 'suma' : 'resta'} ${Math.abs(a)} en los tres lados: $${lo} < x < ${hi}$.` + fig, T`Como la desigualdad es estricta, el intervalo es abierto. Respuesta: <b>${I(lo, hi)}</b>.`], ref: 'Examen de Becas CPU 2024 (Álgebra, valor absoluto)' }); }
    if (t === 1) { const a = R.pick([-3, -2, -1, 1, 2, 3, 4, 5]), b = R.int(2, 7), lo = a - b, hi = a + b;
      const fig = F.recta({ min: lo - 3, max: hi + 3, intervalos: [{ a: -Infinity, b: lo, cb: true, c: 'pri' }, { a: hi, b: Infinity, ca: true, c: 'pri' }] });
      const U = (x, y) => T`$\langle -\infty;\, ${x}] \cup [${y};\, +\infty \rangle$`;
      return armar(R, { e: T`Resuelve $|x ${sg(-a)}| \geq ${b}$.`, ok: U(lo, hi), mal: [T`$[${lo};\, ${hi}]$`, U(-hi, -lo), T`$\langle ${lo};\, ${hi} \rangle$`, U(-b, b), T`$[${hi};\, +\infty \rangle$`, U(lo + 1, hi - 1)],
        pasos: [T`$|u| \geq b$ equivale a $u \leq -b$ ó $u \geq b$.`, T`$x ${sg(-a)} \leq -${b} \Rightarrow x \leq ${lo}$ &nbsp; ó &nbsp; $x ${sg(-a)} \geq ${b} \Rightarrow x \geq ${hi}$.` + fig, T`Los extremos entran porque la desigualdad incluye el igual. Respuesta: <b>${U(lo, hi)}</b>.`], ref: 'Exámenes CPU 2014 y 2024 (Álgebra, valor absoluto)' }); }
    const r1 = R.int(-5, 3), r2 = r1 + R.int(2, 7), b = -(r1 + r2), c = r1 * r2, cuenta = r2 - r1 - 1, suma = (r1 + 1 + r2 - 1) * cuenta / 2;
    const fig = F.plano({ x: [r1 - 2, r2 + 2], y: [Math.min(-1, -((r2 - r1) ** 2) / 4 - 1), 5], curvas: [{ f: x => (x - r1) * (x - r2), c: 'pri' }], puntos: [{ x: r1, y: 0, t: nn(r1), dy: 16, dx: -4, a: 'end' }, { x: r2, y: 0, t: nn(r2), dy: 16 }], areas: [{ pts: Array.from({ length: 41 }, (_, i) => { const x = r1 + (r2 - r1) * i / 40; return [x, (x - r1) * (x - r2)]; }), c: 'ok' }], alt: 'Parábola: es negativa entre sus raíces' });
    const n = numeros(R, suma, [suma + r1 + r2, cuenta, r1 + r2, suma - (r1 + 1), suma + 1, -suma], { neg: true, cero: true });
    return armar(R, { e: T`Halla la suma de los enteros que cumplen $${pol([1, b, c])} < 0$.`, ok: n.ok, mal: n.mal,
      pasos: [T`Se factoriza: $${pol([1, b, c])} = (${pol([1, -r1])})(${pol([1, -r2])})$. Las raíces son $${r1}$ y $${r2}$.`,
        'La parábola abre hacia arriba, así que queda por debajo del eje (es negativa) solo entre sus raíces:' + fig, T`Solución: $${r1} < x < ${r2}$. Enteros: ${Array.from({ length: cuenta }, (_, i) => nn(r1 + 1 + i)).join(', ')}.`, T`Su suma es <b>${nn(suma)}</b>.`], ref: 'Exámenes CPU 2014 y 2024 (Álgebra, inecuaciones)' });
  };

  // Función cuadrática: vértice, mínimo o máximo, con su gráfica
  G['alg-funciones'] = (R, d) => {
    const h = R.int(-3, 4), k = R.int(-6, 5), a = d >= 2 ? R.pick([1, -1, 2, -2]) : 1, b = -2 * a * h, c = a * h * h + k, que = a > 0 ? 'mínimo' : 'máximo';
    const fig = F.plano({ x: [h - 4, h + 4], y: a > 0 ? [Math.min(k - 2, -1), k + 9] : [k - 9, Math.max(k + 2, 1)], curvas: [{ f: x => a * (x - h) * (x - h) + k, c: 'pri' }], puntos: [{ x: h, y: k, t: `V(${nn(h)}; ${nn(k)})`, dy: a > 0 ? 16 : -9, dx: h < 0 ? -8 : 8, a: h < 0 ? 'end' : 'start' }], guias: [[h, k, h, 0]], alt: 'Parábola con su vértice' });
    const pide = R.int(0, 2);
    const base = [T`En $f(x) = ax^{2} + bx + c$ el vértice está en $x_v = -\dfrac{b}{2a} = -\dfrac{${b}}{2(${a})} = ${h}$.`, T`Se reemplaza: $f(${h}) = ${a === 1 ? '' : a === -1 ? '-' : a}(${h})^{2} ${sg(b)}(${h}) ${sg(c)} = ${k}$. El vértice es $V(${h};\, ${k})$.` + fig];
    if (pide === 0) { const n = numeros(R, k, [c, h, -k, a * h * h + b * h, k + a, b], { neg: true, cero: true });
      return armar(R, { e: T`Halla el valor ${que} de $f(x) = ${pol([a, b, c])}$.`, ok: n.ok, mal: n.mal, pasos: base.concat([T`Como $a = ${a}$ es ${a > 0 ? 'positivo, la parábola abre hacia arriba y el vértice es el punto más bajo' : 'negativo, la parábola abre hacia abajo y el vértice es el punto más alto'}. Valor ${que}: <b>${nn(k)}</b>.`]), ref: 'Exámenes CPU 2023 y 2024 (Álgebra, funciones)' }); }
    if (pide === 1) { const ok = h + k, n = numeros(R, ok, [h - k, k - h, h * k, c + h, -h + k, b + c], { neg: true, cero: true });
      return armar(R, { e: T`Si $V(h;\, k)$ es el vértice de la parábola $y = ${pol([a, b, c])}$, calcula $h + k$.`, ok: n.ok, mal: n.mal, pasos: base.concat([T`$h + k = ${h} ${sg(k)} = ${ok}$. Respuesta: <b>${nn(ok)}</b>.`]), ref: 'Exámenes CPU 2023 y 2024 (Álgebra, funciones)' }); }
    const R1 = a > 0 ? T`$[${k};\, +\infty \rangle$` : T`$\langle -\infty;\, ${k}]$`;
    return armar(R, { e: T`Halla el rango de $f(x) = ${pol([a, b, c])}$.`, ok: R1, mal: [a > 0 ? T`$\langle -\infty;\, ${k}]$` : T`$[${k};\, +\infty \rangle$`, a > 0 ? T`$[${h};\, +\infty \rangle$` : T`$\langle -\infty;\, ${h}]$`, a > 0 ? T`$[${c};\, +\infty \rangle$` : T`$\langle -\infty;\, ${c}]$`, T`$\mathbb{R}$`, a > 0 ? T`$\langle ${k};\, +\infty \rangle$` : T`$\langle -\infty;\, ${k} \rangle$`, T`$[${Math.min(h, k) - 1};\, ${Math.max(h, k) + 1}]$`],
      pasos: base.concat([T`El rango son los valores que toma $y$. ${a > 0 ? 'La gráfica empieza en el vértice y sube sin límite' : 'La gráfica llega hasta el vértice y baja sin límite'}: <b>${R1}</b>.`]), ref: 'Exámenes CPU 2023 y 2024 (Álgebra, funciones)' });
  };

  // Factoriales
  G['alg-factorial'] = (R) => {
    const n0 = R.int(3, 14), t = R.int(0, 1);
    if (t === 0) { const v = (n0 + 2) * (n0 + 1); const n = numeros(R, n0, [n0 + 1, n0 + 2, n0 - 1, v, Math.round(Math.sqrt(v))]);
      return armar(R, { e: T`Halla $n$ si $\dfrac{(n + 2)!}{n!} = ${v}$.`, ok: n.ok, mal: n.mal,
        pasos: [T`Se desarrolla el factorial mayor hasta llegar al menor: $(n + 2)! = (n + 2)(n + 1) \cdot n!$.`, T`Se simplifica $n!$: $\;(n + 2)(n + 1) = ${v}$.`, T`Dos números consecutivos cuyo producto es ${v}: $${n0 + 1} \cdot ${n0 + 2} = ${v}$. Entonces $n + 1 = ${n0 + 1}$ y $n = ${n0}$.`, T`Respuesta: <b>${n0}</b>.`], ref: 'Segundo Examen CPU 2023 (Álgebra, factoriales)' }); }
    const v = n0 + 2; const n = numeros(R, n0, [n0 + 1, n0 + 2, n0 - 1, v + 1, 2 * n0]);
    return armar(R, { e: T`Halla $n$ si $\dfrac{(n + 1)! + n!}{n!} = ${v}$.`, ok: n.ok, mal: n.mal,
      pasos: [T`Como $(n + 1)! = (n + 1) \cdot n!$, el numerador es $n!\,[(n + 1) + 1] = n!\,(n + 2)$.`, T`Se simplifica $n!$: $\;n + 2 = ${v} \Rightarrow n = ${n0}$.`, T`Respuesta: <b>${n0}</b>.`], ref: 'Segundo Examen CPU 2023 (Álgebra, factoriales)' });
  };

  root.Gen = { G, rng, armar, numeros, util: { gcd, lcm, tx, nn, M, sg, fr, pol, rad, T }, crear(id, semilla, dif) { const g = G[id]; if (!g) throw new Error('sin generador: ' + id); return g(rng(semilla), dif || 2); } };
})(typeof window !== 'undefined' ? window : globalThis);
