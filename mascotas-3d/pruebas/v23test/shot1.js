// captura suelta: node shot1.js <puerto> <pet> <modo> <colors json|-> <clip:t> <ang,ang> <tilt> <salida> [w] [dsf]
const { chromium } = require('playwright');
const [port, pet, modo, colors, pose, angs, tilt, out, w, dsf] = process.argv.slice(2);
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const W = +(w || 360);
  const page = await b.newPage({ viewport: { width: W, height: W }, deviceScaleFactor: +(dsf || 2) });
  const cq = colors && colors !== '-' ? '&colors=' + encodeURIComponent(colors) : '';
  await page.goto(`http://127.0.0.1:${port}/pet-viewer.html?pet=${pet}&vivo=0&modo=${encodeURIComponent(modo)}${cq}`);
  await page.waitForFunction(() => window.PetViewer && PetViewer.state().loaded && PetViewer.state().firstFrame, null, { timeout: 120000 });
  await page.evaluate(() => PetViewer.setPaused(true));
  const [clip, t] = pose.split(':');
  let k = 0;
  for (const a of angs.split(',')) {
    await page.evaluate(([clip, t, a, tilt]) => { PetViewer.spin(a * Math.PI / 180); PetViewer.tilt(tilt); PetViewer.poseAt(clip, t); PetViewer.advance(0); }, [clip, +t, +a, +tilt]);
    await page.waitForTimeout(80);
    await page.screenshot({ path: out.replace('.png', `_${k++}.png`) });
  }
  await b.close();
})();
