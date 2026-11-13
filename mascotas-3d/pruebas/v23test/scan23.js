// Barrido cuadro por cuadro con el visor real (copia de diagnóstico), secuencia real de la app:
// reposo -> clip con la mezcla de entrada (0.45 s) -> vuelta al reposo (0.6 s). 30 cuadros por segundo.
// En cada cuadro y vista cuenta, sin suavizado de bordes:
//   mag   píxeles de caras TRASERAS de brazo visibles (en la app no se dibujan: se vería el fondo a través)
//   cyan  píxeles de caras traseras del cuerpo visibles
//   nfrag / afrag   piezas de silueta sueltas (no unidas a la principal): cantidad y área
//   thin  píxeles de fondo encerrados entre figura a 2 px a cada lado (rendijas)
//   nhole / ahole   agujeros chicos de fondo encerrados dentro de la figura (<= 40 px)
// uso: node scan23.js <puerto> <tag> <pet> <clips,|all> [tam=320] [vistas=full|az8|front]
const { chromium } = require('playwright');
const fs = require('fs');
const [port, tag, pet, clipsArg, sizeArg, viewsArg] = process.argv.slice(2);
const SIZE = +sizeArg || 320;
const LOOPS = new Set(['idle', 'talk_body', 'think', 'sleep', 'run', 'walk']);
const az = [0, 45, 90, 135, 180, 225, 270, 315];
let views = [];
if (viewsArg === 'az8') views = az.map(a => [a, 0]);
else if (viewsArg === 'front') views = [[0, 0], [45, 0], [315, 0], [0, -0.45], [0, 0.45]];
else for (const t of [0, -0.45, 0.45]) for (const a of az) views.push([a, t]);
const outDir = `/home/claude/w/v23test/scan/${tag}`;
fs.mkdirSync(outDir, { recursive: true });
(async () => {
  const t00 = Date.now();
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage({ viewport: { width: SIZE, height: SIZE }, deviceScaleFactor: 1 });
  page.on('pageerror', e => console.log('ERROR de página', e.message));
  await page.goto(`http://127.0.0.1:${port}/pet-viewer.html?pet=${pet}&vivo=0&modo=${encodeURIComponent('basico dorso fondo noaa')}`);
  await page.waitForFunction(() => window.PetViewer && PetViewer.state().loaded && PetViewer.state().firstFrame, null, { timeout: 180000 });
  await page.evaluate(() => {
    const c = document.querySelector('canvas'); const gl = c.getContext('webgl2') || c.getContext('webgl');
    let W = 0, H = 0, px, cls, lab, stack;
    window.__measure = () => {
      const w = gl.drawingBufferWidth, h = gl.drawingBufferHeight, n = w * h;
      if (w !== W || h !== H) { W = w; H = h; px = new Uint8Array(n * 4); cls = new Uint8Array(n); lab = new Int32Array(n); stack = new Int32Array(n); }
      gl.readPixels(0, 0, w, h, gl.RGBA, gl.UNSIGNED_BYTE, px);
      let mag = 0, cyan = 0, fg = 0;
      for (let i = 0, p = 0; p < n; i += 4, p++) {
        const r = px[i], g = px[i + 1], b = px[i + 2];
        if (r === 0 && g === 255 && b === 0) cls[p] = 0;
        else { fg++; if (r === 255 && g === 0 && b === 255) { cls[p] = 2; mag++; } else if (r === 0 && g === 255 && b === 255) { cls[p] = 3; cyan++; } else cls[p] = 1; }
      }
      // piezas de figura (8 vecinos)
      lab.fill(0); let nl = 0, best = 0; const areas = [0];
      for (let p0 = 0; p0 < n; p0++) {
        if (!cls[p0] || lab[p0]) continue;
        nl++; let sp = 0, a = 0; stack[sp++] = p0; lab[p0] = nl;
        while (sp) {
          const p = stack[--sp]; a++; const x = p % w, y = (p - x) / w;
          for (let dy = -1; dy <= 1; dy++) { const yy = y + dy; if (yy < 0 || yy >= h) continue;
            for (let dx = -1; dx <= 1; dx++) { const xx = x + dx; if (xx < 0 || xx >= w) continue; const q = yy * w + xx; if (cls[q] && !lab[q]) { lab[q] = nl; stack[sp++] = q; } } }
        }
        areas.push(a); if (a > best) best = a;
      }
      let nfrag = 0, afrag = 0;
      for (let k = 1; k <= nl; k++) if (areas[k] !== best) { nfrag++; afrag += areas[k]; }
      if (nl > 0 && nfrag === nl) { nfrag--; }
      // fondo encerrado (4 vecinos): piezas de fondo que no tocan el borde de la imagen
      let nhole = 0, ahole = 0, nbig = 0; const base = nl + 1; let nb = 0;
      for (let p0 = 0; p0 < n; p0++) {
        if (cls[p0] || lab[p0]) continue;
        nb++; const id = base + nb; let sp = 0, a = 0, touch = false; stack[sp++] = p0; lab[p0] = id;
        while (sp) {
          const p = stack[--sp]; a++; const x = p % w, y = (p - x) / w;
          if (x === 0 || y === 0 || x === w - 1 || y === h - 1) touch = true;
          if (x > 0) { const q = p - 1; if (!cls[q] && !lab[q]) { lab[q] = id; stack[sp++] = q; } }
          if (x < w - 1) { const q = p + 1; if (!cls[q] && !lab[q]) { lab[q] = id; stack[sp++] = q; } }
          if (y > 0) { const q = p - w; if (!cls[q] && !lab[q]) { lab[q] = id; stack[sp++] = q; } }
          if (y < h - 1) { const q = p + w; if (!cls[q] && !lab[q]) { lab[q] = id; stack[sp++] = q; } }
        }
        if (!touch) { if (a <= 40) { nhole++; ahole += a; } else nbig++; }
      }
      // rendijas: fondo con figura a 1-2 px a ambos lados (horizontal o vertical)
      let thin = 0;
      for (let y = 2; y < h - 2; y++) for (let x = 2; x < w - 2; x++) {
        const p = y * w + x; if (cls[p]) continue;
        const hz = (cls[p - 1] || cls[p - 2]) && (cls[p + 1] || cls[p + 2]);
        const vt = (cls[p - w] || cls[p - 2 * w]) && (cls[p + w] || cls[p + 2 * w]);
        if (hz || vt) thin++;
      }
      return [mag, cyan, nfrag, afrag, thin, nhole, ahole, nbig, fg];
    };
    window.__runClip = (clip, isLoop, D, views, keys) => {
      const step = 1 / 30;
      PetViewer.setPaused(true);
      PetViewer.poseAt('idle', 0);
      for (let k = 0; k < 6; k++) PetViewer.advance(step);
      PetViewer.play(clip, isLoop, 0.45);
      const nClip = Math.round((isLoop ? D * 1.25 + 0.5 : D) * 30);
      const nTail = Math.round(0.8 * 30);
      const rows = []; const worst = {}; const shots = {};
      for (let f = 0; f < nClip + nTail; f++) {
        if (isLoop && f === nClip) PetViewer.play('idle', true, 0.45);
        const row = [];
        for (let v = 0; v < views.length; v++) {
          PetViewer.spin(views[v][0] * Math.PI / 180); PetViewer.tilt(views[v][1]);
          PetViewer.advance(v === 0 ? step : 0);
          const m = window.__measure(); row.push(m);
          for (const [key, idx] of keys) {
            if (m[idx] > (worst[key] ? worst[key][0] : 0)) { worst[key] = [m[idx], f, v]; shots[key] = c.toDataURL('image/png'); }
          }
        }
        rows.push(row);
      }
      return { rows, worst, shots, nClip, state: PetViewer.state().clip };
    };
  });
  const durs = Object.fromEntries(await page.evaluate(() => PetViewer.clips()));
  const clips = clipsArg === 'all' ? Object.keys(durs) : clipsArg.split(',');
  const keys = [['mag', 0], ['cyan', 1], ['afrag', 3], ['thin', 4], ['ahole', 6]];
  const out = { pet, size: SIZE, views, clips: {} };
  for (const clip of clips) {
    if (!durs[clip]) { console.log('sin clip', clip); continue; }
    const t0 = Date.now();
    const r = await page.evaluate(([clip, isLoop, D, views, keys]) => window.__runClip(clip, isLoop, D, views, keys), [clip, LOOPS.has(clip), durs[clip], views, keys]);
    const mx = keys.map(([key, idx]) => { let m = 0, cnt = 0; for (const row of r.rows) for (const v of row) { if (v[idx] > m) m = v[idx]; if (v[idx] > 0) cnt++; } return `${key} máx ${m} (${cnt} vistas)`; });
    for (const key in r.shots) fs.writeFileSync(`${outDir}/${pet}_${clip}_${key}.png`, Buffer.from(r.shots[key].split(',')[1], 'base64'));
    out.clips[clip] = { rows: r.rows, worst: r.worst, nClip: r.nClip, dur: durs[clip] };
    console.log(tag, pet, clip, `cuadros ${r.rows.length} x vistas ${views.length}`, '|', mx.join(' | '), '|', ((Date.now() - t0) / 1000).toFixed(1) + ' s', 'fin en', r.state);
  }
  fs.writeFileSync(`${outDir}/${pet}.json`, JSON.stringify(out));
  await browser.close();
  console.log('LISTO', tag, pet, ((Date.now() - t00) / 1000).toFixed(0) + ' s');
})();
