// Prueba del repaso espaciado (cajas de Leitner) y del repaso intercalado de plan.js. Solo necesita Node.
globalThis.window = globalThis;
for (const f of ['fig', 'gen', 'gen2', 'gen3', 'gen4', 'nativo', 'datos', 'datos2', 'plan']) require('../app/src/main/assets/web/camino/' + f + '.js');
const { Plan: P } = globalThis; let fallas = 0; const mal = m => { fallas++; console.log('FALLA', m); };
const igual = (a, b, m) => { if (a !== b) mal(`${m}: esperaba ${b} y salió ${a}`); };

// fechas
igual(P.sumarDias('2026-11-13', 1), '2026-11-14', 'sumar un día');
igual(P.sumarDias('2026-11-30', 1), '2026-12-01', 'cambio de mes');
igual(P.sumarDias('2026-12-31', 30), '2027-01-30', 'cambio de año');
igual(/^\d{4}-\d{2}-\d{2}$/.test(P.hoy()), true, 'formato de hoy');

// cajas: 4 o 5 de 5 sube, 3 de 5 se queda, 2 o menos vuelve a la 1
const S = { grupo: 'A' }, dia = '2026-11-13';
let r = P.programar(S, 'rm-planteo', 5, 5, dia); igual(r.c, 2, 'sube a la caja 2'); igual(r.p, '2026-11-16', 'caja 2: en 3 días');
r = P.programar(S, 'rm-planteo', 4, 5, '2026-11-16'); igual(r.c, 3, 'sube a la caja 3'); igual(r.p, '2026-11-23', 'caja 3: en 7 días');
r = P.programar(S, 'rm-planteo', 3, 5, '2026-11-23'); igual(r.c, 3, '3 de 5 se queda'); igual(r.p, '2026-11-30', 'sigue a 7 días');
r = P.programar(S, 'rm-planteo', 1, 5, '2026-11-30'); igual(r.c, 1, 'fallar vuelve a la 1'); igual(r.p, '2026-12-01', 'caja 1: mañana');
for (let k = 0; k < 8; k++) r = P.programar(S, 'rm-edades', 1, 1, dia);
igual(r.c, 5, 'la caja no pasa de 5'); igual(r.p, P.sumarDias(dia, 30), 'caja 5: en 30 días');
r = P.programar({}, 'x', 0, 0, dia); igual(r.c, 1, 'sin preguntas no sube');

// días que faltan y temas vencidos
igual(P.diasParaRepaso(S, 'rm-planteo', '2026-11-30'), 1, 'falta un día');
igual(P.diasParaRepaso(S, 'rm-planteo', '2026-12-05'), 0, 'vencido cuenta como hoy');
igual(P.diasParaRepaso(S, 'nunca', dia), null, 'tema sin practicar');
const S2 = { grupo: 'A', rep: { 'rm-planteo': { c: 1, p: '2026-11-10' }, 'rm-edades': { c: 2, p: '2026-11-12' }, 'rm-operadores': { c: 3, p: '2026-11-20' }, 'bio-genetica': { c: 1, p: '2026-11-01' } } };
const v = P.vencidos(S2, dia).map(t => t.id);
igual(v.join(), 'rm-planteo,rm-edades', 'vencidos del grupo, el más atrasado primero (sin temas de otro grupo ni futuros)');
igual(P.vencidos({ grupo: 'A' }, dia).length, 0, 'sin repasos no hay vencidos');

// repaso intercalado: preguntas válidas, de temas vencidos, sin repetir tema seguido
const muchos = { grupo: 'A', rep: {} };
P.temasDe('A').filter(t => !t.nat && P.tieneFuente(t)).slice(0, 5).forEach((t, i) => { muchos.rep[t.id] = { c: 1, p: '2026-11-0' + (i + 1) }; });
for (let s = 1; s <= 200; s++) {
  const qs = P.repaso(muchos, dia, s * 7919, 6);
  if (qs.length !== 5 && qs.length !== 6) mal(`repaso de ${qs.length} preguntas (semilla ${s})`);
  for (let i = 0; i < qs.length; i++) {
    const q = qs[i];
    if (!muchos.rep[q.tema]) mal('pregunta de un tema no vencido: ' + q.tema);
    if (q.o.length !== 5 || new Set(q.o).size !== 5 || !(q.c >= 0 && q.c < 5) || !q.pasos.length) mal('pregunta mal formada en ' + q.tema);
    if (i > 0 && qs[i - 1].tema === q.tema) mal('dos seguidas del mismo tema: ' + q.tema);
  }
}
const uno = { grupo: 'A', rep: { 'rm-planteo': { c: 1, p: '2026-11-01' } } };
igual(P.repaso(uno, dia, 99, 6).length, 3, 'con un solo tema vencido salen 3 preguntas');
igual(P.repaso({ grupo: 'A' }, dia, 99, 6).length, 0, 'sin vencidos no hay repaso');
console.log('repaso: cajas, fechas e intercalado | fallas', fallas); process.exit(fallas ? 1 : 0);
