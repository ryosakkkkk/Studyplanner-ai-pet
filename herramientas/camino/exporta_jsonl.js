// Exporta preguntas con su explicación paso a paso a JSONL (una por línea), como datos para ajustar a futuro un
// modelo abierto que explique. No entrena nada. uso: node exporta_jsonl.js <salida.jsonl> [semillas por tema y dificultad=30]
const fs = require('fs'); globalThis.window = globalThis;
const W = __dirname + '/../../app/src/main/assets/web/camino/';
for (const f of ['fig.js', 'gen.js', 'gen2.js', 'gen3.js', 'gen4.js', 'nativo.js', 'datos.js', 'datos2.js', 'plan.js']) require(W + f);
const out = process.argv[2], N = +(process.argv[3] || 30);
const limpio = h => String(h).replace(/<svg[\s\S]*?<\/svg>/g, ' ').replace(/<table[\s\S]*?<\/table>/g, m => ' ' + m.replace(/<\/t[dh]>/g, ' | ').replace(/<\/tr>/g, ' ; ').replace(/<[^>]+>/g, '') + ' ')
  .replace(/<br\s*\/?>/g, ' ').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&').replace(/\s+/g, ' ').trim();
const L = 'ABCDE'; const filas = []; const vistos = new Set();
const curso = id => { const t = Plan.tema(id); return t ? { curso: DATOS.cursos[t.curso].n, tema: t.t } : { curso: '', tema: id }; };
const pon = (id, q, origen) => {
  const e = limpio(q.e); const k = id + '|' + e + '|' + q.o.join('|'); if (vistos.has(k)) return; vistos.add(k);
  const c = curso(id);
  filas.push({ tema_id: id, curso: c.curso, tema: c.tema, origen, pregunta: e, alternativas: q.o.map((o, i) => L[i] + ') ' + limpio(o)), respuesta: L[q.c] + ') ' + limpio(q.o[q.c]),
    explicacion: (q.pasos || []).map((p, i) => (i + 1) + '. ' + limpio(p)).join(' ') });
};
for (const id of Object.keys(Gen.G)) for (let d = 1; d <= 3; d++) for (let s = 1; s <= N; s++) pon(id, Gen.crear(id, s * 7919 + d * 104729, d), 'generada');
for (const id of Object.keys(DATOS.pregs)) for (const q of DATOS.pregs[id]) pon(id, { e: q.e, o: q.o, c: q.c != null ? q.c : 0, pasos: [q.x] }, 'fija');
for (const id of Object.keys(NATIVO.banco)) for (const q of NATIVO.banco[id]) pon(id, { e: q.e, o: q.o, c: q.c, pasos: [q.x] }, 'banco de la app');
fs.writeFileSync(out, filas.map(f => JSON.stringify(f)).join('\n') + '\n');
const por = {}; filas.forEach(f => por[f.origen] = (por[f.origen] || 0) + 1);
console.log('filas', filas.length, JSON.stringify(por), 'vacías', filas.filter(f => !f.explicacion || !f.pregunta).length);
