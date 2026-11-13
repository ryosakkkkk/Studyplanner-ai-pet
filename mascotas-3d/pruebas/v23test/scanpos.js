// Barrido por POSICIÓN: en cada cuadro y vista, cada píxel de cara trasera visible lleva en el color la posición de
// reposo de esa parte del modelo. Se acumula en una rejilla de 1,6 cm: dice QUÉ zonas del modelo quedan expuestas por
// detrás (en la app esas caras no se dibujan y se ve el fondo), en qué clip, cuadro y vista ocurre lo peor.
// uso: node scanpos.js <puerto> <tag> <pet> <clips,|all> [tam=320] [vistas=full|az8|front] [paso de cuadros=1]
const { chromium } = require('playwright');
const fs = require('fs');
const [port, tag, pet, clipsArg, sizeArg, viewsArg, stepArg] = process.argv.slice(2);
const SIZE = +sizeArg || 320, FSTEP = +stepArg || 1;
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
  await page.goto(`http://127.0.0.1:${port}/pet-viewer.html?pet=${pet}&vivo=0&modo=${encodeURIComponent('basico dorso dorsopos fondo noaa sincara sinaccesorios')}`);
  await page.waitForFunction(() => window.PetViewer && PetViewer.state().loaded && PetViewer.state().firstFrame, null, { timeout: 180000 });
  await page.evaluate(() => {
    const c = document.querySelector('canvas'); const gl = c.getContext('webgl2') || c.getContext('webgl');
    let W = 0, H = 0, px;
    window.__vox = new Map();        // clave -> [total, máximo en una vista, clip, cuadro, vista]
    window.__measurePos = (clipIdx, f, v) => {
      const w = gl.drawingBufferWidth, h = gl.drawingBufferHeight, n = w * h;
      if (w !== W || h !== H) { W = w; H = h; px = new Uint8Array(n * 4); }
      gl.readPixels(0, 0, w, h, gl.RGBA, gl.UNSIGNED_BYTE, px);
      let back = 0, fg = 0; const loc = new Map();
      for (let i = 0; i < n * 4; i += 4) {
        const r = px[i], g = px[i + 1], b = px[i + 2];
        if (r === 0 && g === 255 && b === 0) continue;
        fg++;
        if (r === 0 && g === 0 && b === 0) continue;
        back++;
        const key = ((r >> 2) << 12) | ((g >> 2) << 6) | (b >> 2);
        loc.set(key, (loc.get(key) || 0) + 1);
      }
      for (const [key, cnt] of loc) {
        const e = window.__vox.get(key);
        if (!e) window.__vox.set(key, [cnt, cnt, clipIdx, f, v]);
        else { e[0] += cnt; if (cnt > e[1]) { e[1] = cnt; e[2] = clipIdx; e[3] = f; e[4] = v; } }
      }
      return [back, fg];
    };
    window.__runClipPos = (clip, clipIdx, isLoop, D, views, fstep) => {
      const step = 1 / 30;
      PetViewer.setPaused(true);
      PetViewer.poseAt('idle', 0);
      for (let k = 0; k < 6; k++) PetViewer.advance(step);
      PetViewer.play(clip, isLoop, 0.45);
      const nClip = Math.round((isLoop ? D * 1.25 + 0.5 : D) * 30);
      const nTail = Math.round(0.8 * 30);
      const rows = []; let worst = [0, 0, 0], shot = null; let tR = 0, tM = 0;
      for (let f = 0; f < nClip + nTail; f++) {
        if (isLoop && f === nClip) PetViewer.play('idle', true, 0.45);
        if (f % fstep) { PetViewer.advance(step); continue; }
        const row = [];
        for (let v = 0; v < views.length; v++) {
          PetViewer.spin(views[v][0] * Math.PI / 180); PetViewer.tilt(views[v][1]);
          let t0 = performance.now();
          PetViewer.advance(v === 0 ? step : 0);
          let t1 = performance.now();
          const m = window.__measurePos(clipIdx, f, v); row.push(m[0]);
          tR += t1 - t0; tM += performance.now() - t1;
          if (m[0] > worst[0]) { worst = [m[0], f, v]; shot = c.toDataURL('image/png'); }
        }
        rows.push(row);
      }
      return { rows, worst, shot, nClip, tR: Math.round(tR), tM: Math.round(tM) };
    };
  });
  const durs = Object.fromEntries(await page.evaluate(() => PetViewer.clips()));
  const clips = clipsArg === 'all' ? Object.keys(durs) : clipsArg.split(',');
  const out = { pet, size: SIZE, views, fstep: FSTEP, clipNames: clips, clips: {} };
  for (let ci = 0; ci < clips.length; ci++) {
    const clip = clips[ci];
    if (!durs[clip]) { console.log('sin clip', clip); continue; }
    const t0 = Date.now();
    const r = await page.evaluate(([clip, ci, isLoop, D, views, fstep]) => window.__runClipPos(clip, ci, isLoop, D, views, fstep), [clip, ci, LOOPS.has(clip), durs[clip], views, FSTEP]);
    let m = 0, cnt = 0, tot = 0; for (const row of r.rows) for (const v of row) { if (v > m) m = v; if (v > 0) cnt++; tot += v; }
    if (r.shot) fs.writeFileSync(`${outDir}/${pet}_${clip}_peor.png`, Buffer.from(r.shot.split(',')[1], 'base64'));
    out.clips[clip] = { rows: r.rows, worst: r.worst, nClip: r.nClip, dur: durs[clip] };
    console.log(tag, pet, clip, `cuadros ${r.rows.length} x vistas ${views.length}`, '| caras traseras: máx', m, 'px, vistas con alguna', cnt, 'de', r.rows.length * views.length, 'suma', tot,
      '| peor cuadro', r.worst[1], 'vista', JSON.stringify(views[r.worst[2]]), '|', ((Date.now() - t0) / 1000).toFixed(1) + ' s', `(dibujo ${r.tR} ms, medida ${r.tM} ms)`);
  }
  out.vox = await page.evaluate(() => [...window.__vox.entries()].map(([k, e]) => [k, ...e]));
  fs.writeFileSync(`${outDir}/${pet}_pos.json`, JSON.stringify(out));
  await browser.close();
  console.log('LISTO', tag, pet, ((Date.now() - t00) / 1000).toFixed(0) + ' s', 'vóxeles', out.vox.length);
})();
