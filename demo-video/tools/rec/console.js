// Records the Virtual Console page as timestamped JPEG frames through the Chrome DevTools screencast, until SIGTERM.
// usage: node console.js <outDir> <meta.json>   -> outDir/frames/<n>.jpg and outDir/frames.json [{file, t}]
const fs = require('fs');
const path = require('path');
const http = require('http');
const { chromium } = require('playwright-core');
(async () => {
  const [outDir, metaPath] = process.argv.slice(2);
  const frameDir = path.join(outDir, 'frames');
  fs.mkdirSync(frameDir, { recursive: true });
  const browser = await chromium.launch({
    // A real window draws the 3D road on the GPU; software WebGL in headless mode loaded the host enough to
    // bunch up the emulator's speed signal.
    executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: false,
    args: ['--window-position=0,0', '--window-size=1240,1180'],
  });
  const page = await browser.newPage({ viewport: { width: 1240, height: 1080 } });
  await page.goto('http://127.0.0.1:8796/', { waitUntil: 'networkidle' });
  const cdp = await page.context().newCDPSession(page);
  const frames = [];
  let n = 0;
  cdp.on('Page.screencastFrame', async (f) => {
    const file = path.join(frameDir, String(n++).padStart(6, '0') + '.jpg');
    fs.writeFileSync(file, Buffer.from(f.data, 'base64'));
    // metadata.timestamp is seconds since epoch when Chrome painted the frame.
    frames.push({ file, t: Math.round((f.metadata.timestamp || Date.now() / 1000) * 1000) });
    try { await cdp.send('Page.screencastFrameAck', { sessionId: f.sessionId }); } catch (_) {}
  });
  await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 82, maxWidth: 1240, maxHeight: 1080, everyNthFrame: 1 });
  const meta = { startedAtMs: Date.now() };
  fs.writeFileSync(metaPath, JSON.stringify(meta));

  // While the car moves, the page runs its own pedal physics and ignores speed sent to the server, so the demo
  // drives the page itself: pedals, a gentle cruise hold, the steering wheel and the gear buttons. What the
  // console shows is then what the car reports.
  await page.evaluate(() => {
    window.__mc = { cruise: null, climb: 15 };
    setInterval(() => {
      const c = window.__mc;
      if (c.cruise == null || !isDriveGear() || parkTransitionPending) return;
      const v = vehicle.speed_kph, t = c.cruise;
      vehicle.brake_pct = 0;
      if (v < t - 0.3) vehicle.accelerator_pct = c.climb;
      else if (v > t + 1.5) vehicle.accelerator_pct = 0;
      else vehicle.accelerator_pct = t * 0.012 / 19 * 100; // throttle that balances the page's drag at t
    }, 50);
  });
  const server = http.createServer(async (req, res) => {
    const q = Object.fromEntries(new URL(req.url, 'http://x').searchParams);
    try {
      const out = await page.evaluate((q) => {
        const c = window.__mc;
        if (q.gear) { const b = [...gearButtons.children].find((x) => x.title === q.gear); if (b) b.click(); }
        if ('cruise' in q) c.cruise = q.cruise === '' ? null : Number(q.cruise);
        if ('climb' in q) c.climb = Number(q.climb);
        if ('accel' in q) { c.cruise = null; vehicle.accelerator_pct = Number(q.accel); }
        if ('brake' in q) { vehicle.brake_pct = Number(q.brake); if (Number(q.brake) > 0) { c.cruise = null; vehicle.accelerator_pct = 0; } }
        if ('steer' in q) setSteeringAngle(Number(q.steer), true);
        if (!parkTransitionPending) { markLocalControl(); queueCommit(); }
        return { ...controlsSnapshot(), gear: currentGear };
      }, q);
      res.end(JSON.stringify(out));
    } catch (e) { res.statusCode = 500; res.end(String(e)); }
  });
  server.listen(Number(process.env.CONSOLE_CTL_PORT || 8797), '127.0.0.1');
  const telemetry = [];
  const sampler = setInterval(async () => {
    try {
      const v = await page.evaluate(() => ({ ...controlsSnapshot(), raw: vehicle.speed_kph, gear: currentGear }));
      telemetry.push({ t: Date.now(), ...v });
    } catch (_) {}
  }, 100);

  const stop = async () => {
    clearInterval(sampler); server.close();
    try { await cdp.send('Page.stopScreencast'); } catch (_) {}
    meta.stoppedAtMs = Date.now(); meta.frames = frames.length;
    fs.writeFileSync(path.join(outDir, 'telemetry.json'), JSON.stringify(telemetry));
    fs.writeFileSync(path.join(outDir, 'frames.json'), JSON.stringify(frames));
    fs.writeFileSync(metaPath, JSON.stringify(meta));
    await browser.close();
    process.exit(0);
  };
  process.on('SIGTERM', stop); process.on('SIGINT', stop);
})();
