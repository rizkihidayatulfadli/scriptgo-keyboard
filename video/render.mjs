// Renders the ScriptGo promo videos: two stories (15 s, 30 s) in three sizes (9:16, 1:1, 16:9),
// plus a small 16:9 copy for the website. Every frame is a screenshot of scene.html at time t;
// the typing sounds are synthesized here (no audio files, nothing to license).
// Usage: npm run render [-- --story 15 --format 9x16 --fps 30]   Output: video/out/*.mp4
import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { extname, join, resolve } from "node:path";
import ffmpeg from "ffmpeg-static";
import { chromium } from "playwright";

const FORMATS = { "9x16": [1080, 1920], "1x1": [1080, 1080], "16x9": [1920, 1080] };
const args = Object.fromEntries(process.argv.slice(2).join(" ").split("--").filter(Boolean).map((a) => a.trim().split(/\s+/)));
const stories = args.story ? [Number(args.story)] : [15, 30];
const formats = args.format ? [args.format] : Object.keys(FORMATS);
const fps = Number(args.fps ?? 30);

// Serve the repo root so scene.html can load the site's fonts and logo.
const root = resolve("..");
const types = { ".html": "text/html", ".css": "text/css", ".js": "text/javascript", ".svg": "image/svg+xml", ".woff2": "font/woff2" };
const server = createServer((req, res) => {
  const file = join(root, decodeURIComponent(new URL(req.url, "http://x").pathname));
  if (!file.startsWith(root) || !existsSync(file)) return res.writeHead(404).end();
  res.writeHead(200, { "Content-Type": types[extname(file)] ?? "application/octet-stream" }).end(readFileSync(file));
});
await new Promise((r) => server.listen(0, "127.0.0.1", r));
const url = `http://127.0.0.1:${server.address().port}/video/scene.html`;

mkdirSync("out", { recursive: true });
const browser = await chromium.launch();

for (const length of stories) {
  const wav = `out/scriptgo-${length}s.wav`;
  for (const format of formats) {
    const [width, height] = FORMATS[format];
    const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: 1 });
    await page.goto(url);
    await page.evaluate(() => document.fonts.ready);
    await page.evaluate((l) => window.setupScene(l), length);
    if (!existsSync(wav)) writeFileSync(wav, synth(await page.evaluate((l) => window.soundCues(l), length), length));

    const out = `out/scriptgo-${length}s-${format}.mp4`;
    const encoder = spawn(ffmpeg, [
      "-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", String(fps), "-i", "-", "-i", wav,
      "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-r", String(fps),
      "-c:a", "aac", "-b:a", "160k", "-shortest", "-movflags", "+faststart", out,
    ], { stdio: ["pipe", "inherit", "inherit"] });
    const done = new Promise((ok, fail) => encoder.on("close", (code) => (code === 0 ? ok() : fail(new Error(`ffmpeg exited ${code}`)))));
    const frames = Math.round(length * fps);
    for (let i = 0; i < frames; i++) {
      await page.evaluate((t) => window.render(t), i / fps);
      const shot = await page.screenshot({ type: "jpeg", quality: 95 });
      if (!encoder.stdin.write(shot)) await new Promise((r) => encoder.stdin.once("drain", r));
    }
    encoder.stdin.end();
    await done;
    await page.close();
    console.log(`${out} (${frames} frames)`);
  }
  // Web copy of the 16:9 video: 1280×720, smaller file for scriptgo.id.
  const wide = `out/scriptgo-${length}s-16x9.mp4`;
  if (existsSync(wide)) {
    const web = `out/scriptgo-${length}s-web.mp4`;
    await run(ffmpeg, ["-y", "-loglevel", "error", "-i", wide, "-vf", "scale=1280:720", "-c:v", "libx264", "-preset", "slow", "-crf", "26", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart", web]);
    await run(ffmpeg, ["-y", "-loglevel", "error", "-ss", String(length === 15 ? 4.9 : 15), "-i", wide, "-frames:v", "1", "-vf", "scale=1280:720", "-q:v", "4", `out/scriptgo-${length}s-poster.jpg`]);
    console.log(web);
    // The 30 s web copy and its poster are what scriptgo.id plays ("Tonton 30 detik").
    if (length === 30) {
      mkdirSync("../site/static/video", { recursive: true });
      copyFileSync(web, "../site/static/video/scriptgo-30s.mp4");
      copyFileSync(`out/scriptgo-${length}s-poster.jpg`, "../site/static/video/scriptgo-30s-poster.jpg");
      console.log("site/static/video/scriptgo-30s.mp4");
    }
  }
}
await browser.close();
server.close();

function run(cmd, argv) {
  return new Promise((ok, fail) => spawn(cmd, argv, { stdio: "inherit" }).on("close", (c) => (c === 0 ? ok() : fail(new Error(`${cmd} exited ${c}`)))));
}

/** Mono 48 kHz WAV with the sounds at their cue times: soft key ticks, a tap, and chimes. */
function synth(cues, seconds) {
  const rate = 48000, n = Math.ceil(seconds * rate), buf = new Float32Array(n);
  const GAIN = 1.8; // peaks around -7 dBFS: audible under music added on upload
  let noise = 12345;
  const rnd = () => ((noise = (noise * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff) * 2 - 1;
  const add = (start, dur, fn) => {
    const s0 = Math.round(start * rate);
    for (let i = 0; i < dur * rate && s0 + i < n; i++) buf[s0 + i] += fn(i / rate);
  };
  const tone = (f, decay, amp) => (x) => amp * Math.sin(2 * Math.PI * f * x) * Math.exp(-x * decay);
  for (const { t, kind } of cues) {
    if (kind === "tick") {
      let last = 0; // soft key click: filtered noise with a quick decay
      add(t, 0.035, (x) => { last = last * 0.55 + rnd() * 0.45; return 0.22 * last * Math.exp(-x * 140); });
    } else if (kind === "tap") {
      add(t, 0.06, tone(1100, 70, 0.25));
    } else if (kind === "sent") {
      add(t, 0.35, tone(1318.5, 11, 0.18));
      add(t + 0.07, 0.4, tone(1760, 10, 0.16));
    } else if (kind === "in") {
      add(t, 0.3, tone(880, 14, 0.14));
      add(t + 0.09, 0.3, tone(1174.7, 14, 0.12));
    } else if (kind === "pop") {
      add(t, 0.08, (x) => 0.16 * Math.sin(2 * Math.PI * (380 + 5200 * x) * x) * Math.exp(-x * 45));
    } else if (kind === "end") {
      for (const [f, d] of [[523.3, 0], [659.3, 0.06], [784, 0.12], [1046.5, 0.18]]) add(t + d, 1.2, tone(f, 3.5, 0.07));
    }
  }
  const pcm = Buffer.alloc(44 + n * 2);
  pcm.write("RIFF", 0); pcm.writeUInt32LE(36 + n * 2, 4); pcm.write("WAVEfmt ", 8);
  pcm.writeUInt32LE(16, 16); pcm.writeUInt16LE(1, 20); pcm.writeUInt16LE(1, 22); pcm.writeUInt32LE(rate, 24);
  pcm.writeUInt32LE(rate * 2, 28); pcm.writeUInt16LE(2, 32); pcm.writeUInt16LE(16, 34); pcm.write("data", 36); pcm.writeUInt32LE(n * 2, 40);
  for (let i = 0; i < n; i++) pcm.writeInt16LE(Math.round(Math.max(-1, Math.min(1, buf[i] * GAIN)) * 32767), 44 + i * 2);
  return pcm;
}
