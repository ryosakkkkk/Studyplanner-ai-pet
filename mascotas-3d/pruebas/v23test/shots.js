// Capturas exactas con el visor de diagnóstico: node shots.js <port> <jobs.json>
// jobs = [{pet, modo, w, h, dsf, shots:[{clip, t, ang, tilt, out, rect:[x,y,w,h]}]}]
const { chromium } = require('playwright');
const fs = require('fs');
const [port, jobsFile] = process.argv.slice(2);
const jobs = JSON.parse(fs.readFileSync(jobsFile, 'utf8'));
(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  for (const job of jobs) {
    const page = await browser.newPage({ viewport: { width: job.w || 420, height: job.h || 420 }, deviceScaleFactor: job.dsf || 1.5 });
    await page.goto(`http://127.0.0.1:${port}/pet-viewer.html?pet=${job.pet}&vivo=0&modo=${encodeURIComponent(job.modo || '')}`);
    await page.waitForFunction(() => window.PetViewer && PetViewer.state().loaded && PetViewer.state().firstFrame, null, { timeout: 120000 });
    await page.evaluate(() => { PetViewer.setPaused(true); });
    for (const s of job.shots) {
      await page.evaluate(([clip, t, ang, tilt]) => { PetViewer.spin(ang); PetViewer.tilt(tilt || 0); PetViewer.poseAt(clip, t); PetViewer.advance(0); }, [s.clip, s.t, s.ang, s.tilt || 0]);
      await page.waitForTimeout(60);
      const opt = { path: s.out };
      if (s.rect) opt.clip = { x: s.rect[0], y: s.rect[1], width: s.rect[2], height: s.rect[3] };
      await page.screenshot(opt);
    }
    await page.close();
    console.log('ok', job.pet, job.shots.length);
  }
  await browser.close();
})();
