// Timeline of the two promo stories. render(t) sets every element for time t (seconds), so each
// frame is exact and repeatable; soundCues() lists when the typing sounds play. Copy lives here.

const DEMOS = {
  ongkir: {
    key: "ongkir", title: "Info ongkir", ask: "Kak, ongkir ke Bandung berapa?",
    reply: 'Halo Kak <span class="hl">Rina</span>, ongkir ke <span class="hl">Bandung</span> Rp18.000 via JNE (2–3 hari). Mau langsung diproses? 🙏',
  },
  rekening: {
    key: "rekening", title: "Nomor rekening", ask: "Oke kak, transfernya ke mana?",
    reply: 'Siap Kak <span class="hl">Rina</span>! Transfer ke BCA 1234567890 a.n. Toko Rina ya. Kirim bukti transfer di sini 😊',
  },
};

const STORIES = {
  15: {
    length: 15,
    captions: [
      [0.2, 2.4, 'Pelanggan nanya <span class="grad">ongkir</span> lagi?'],
      [2.6, 6.2, 'Ketik <kbd>/ongkir</kbd>, balasan lengkap <span class="grad">langsung jadi</span>'],
      [6.4, 8.6, 'Nama &amp; kota pelanggan <span class="grad">terisi sendiri</span>'],
      [8.8, 11.4, 'Satu ketukan. <span class="grad">Terkirim.</span>'],
    ],
    phone: [1.6, 11.6],
    demos: [[2.0, "ongkir"], [8.0, "rekening"]],
    highlight: [5.0, 7.4],
    end: 11.6,
  },
  30: {
    length: 30,
    captions: [
      [0.2, 2.8, 'Chat yang sama, dibalas <span class="grad">50× sehari?</span>'],
      [3.0, 8.0, 'Ongkir berapa? Rekeningnya? Masih ada?<small>Jawabannya itu-itu lagi, diketik ulang terus.</small>'],
      [8.4, 10.6, 'Pakai <span class="grad">ScriptGo.</span><small>Keyboard dengan template balasan</small>'],
      [10.8, 14.2, 'Ketik <kbd>/ongkir</kbd>, balasan lengkap <span class="grad">langsung jadi</span>'],
      [14.4, 17.4, 'Nama &amp; kota pelanggan <span class="grad">terisi sendiri</span><small>dari kontak yang kamu simpan</small>'],
      [17.6, 21.6, 'Jalan di WhatsApp, Instagram, TikTok, Shopee<small>dan WhatsApp Web lewat Chrome extension</small>'],
      [22.0, 26.2, 'Balasan <span class="grad">8× lebih cepat</span>'],
    ],
    pile: [2.0, 8.2],
    phone: [9.8, 21.8],
    demos: [[10.4, "ongkir"], [17.2, "rekening"]],
    highlight: [13.2, 16.2],
    stat: [22.0, 26.2],
    end: 26.4,
  },
};

const PILE = ["Kak, ongkir ke Medan?", "Rekeningnya apa kak?", "Yang hitam masih ada?", "Bisa COD nggak?", "Paketku udah dikirim?",
  "Ongkir ke Surabaya?", "Ready size M?", "Transfer ke mana?", "Kapan sampai kak?", "Bisa kirim hari ini?", "Ada diskon?", "Ongkir ke Bandung?"];

// Typing rhythm of one demo, from the moment the customer's question arrives.
const CHAR = 0.11;
function demoTimes(at, demo) {
  const chars = `/${demo.key}`;
  const typeStart = at + 0.9;
  const typeEnd = typeStart + chars.length * CHAR;
  const tap = typeEnd + 0.45;
  return { at, chars, typeStart, typeEnd, tap, sent: tap + 0.35 };
}

const clamp = (x) => Math.max(0, Math.min(1, x));
const easeOut = (x) => 1 - (1 - clamp(x)) ** 3;
const easeBack = (x) => { x = clamp(x); const c = 1.7; return 1 + (c + 1) * (x - 1) ** 3 + c * (x - 1) ** 2; };
/** 0 → 1 over [from, from+inDur], 1 until `to`, then 1 → 0 over outDur. */
function presence(t, from, to, inDur = 0.35, outDur = 0.3) {
  if (t < from || t > to + outDur) return 0;
  return Math.min(easeOut((t - from) / inDur), 1 - clamp((t - to) / outDur));
}
const $ = (id) => document.getElementById(id);

let story, layout;

function setup(length) {
  story = STORIES[length];
  const { innerWidth: w, innerHeight: h } = window;
  layout = w / h > 1.3 ? "wide" : w / h < 0.8 ? "tall" : "square";
  document.body.className = layout;
  $("captions").innerHTML = story.captions.map(([, , html]) => `<div class="cap">${html}</div>`).join("");
  // Scale the 360×740 phone to its area.
  const wrap = $("phone-wrap").getBoundingClientRect();
  const s = layout === "square" ? wrap.height / 760 : Math.min(wrap.width / 400, wrap.height / 760);
  $("phone").dataset.scale = String(s);
  // Pile of questions: a 2-column grid of slots with a little jitter, filled in a shuffled order.
  $("pile").innerHTML = PILE.map((q) => `<div class="q">${q}</div>`).join("");
  let seed = 7;
  const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
  // [left column x, right column x, top y, bottom y] in % of the stage.
  const [xa, xb, y0, y1] = { tall: [4, 47, 30, 68], square: [4, 46, 25, 62], wide: [51, 71, 8, 70] }[layout];
  const rows = PILE.length / 2;
  const slots = PILE.map((_, i) => i).sort(() => rnd() - 0.5);
  [...$("pile").children].forEach((el, i) => {
    const slot = slots[i], row = slot % rows, col = Math.floor(slot / rows);
    el.style.left = `${(col ? xb : xa) + (rnd() - 0.5) * 6}%`;
    el.style.top = `${y0 + (row / (rows - 1)) * (y1 - y0) + (rnd() - 0.5) * 3}%`;
    el.dataset.rot = String((rnd() - 0.5) * 7);
  });
}

function render(t) {
  // Background drift.
  document.querySelector(".g1").style.transform = `translate(${Math.sin(t * 0.25) * 4}vmax, ${Math.cos(t * 0.2) * 3}vmax)`;
  document.querySelector(".g2").style.transform = `translate(${Math.cos(t * 0.22) * 4}vmax, ${Math.sin(t * 0.18) * 3}vmax)`;

  // Captions: fade and rise in, fade out.
  [...$("captions").children].forEach((el, i) => {
    const [from, to] = story.captions[i];
    const p = presence(t, from, to, 0.4, 0.28);
    el.style.opacity = String(p);
    el.style.transform = `translateY(${(1 - easeOut((t - from) / 0.4)) * 40}px)`;
  });

  // Pile of repeated questions and the counter.
  const [p0, p1] = story.pile ?? [-1, -1];
  [...$("pile").children].forEach((el, i) => {
    const at = p0 + 0.2 + i * 0.42;
    const pin = t >= at ? easeBack((t - at) / 0.35) : 0;
    const out = 1 - clamp((t - p1) / 0.35);
    el.style.opacity = String(t >= at && t <= p1 + 0.35 ? Math.min(1, pin) * out : 0);
    el.style.transform = `scale(${0.6 + 0.4 * pin}) rotate(${el.dataset.rot}deg)`;
  });
  const cp = presence(t, p0 + 0.2, p1, 0.4, 0.35);
  $("counter").style.opacity = String(cp);
  $("count").textContent = String(Math.round(50 * easeOut((t - p0 - 0.2) / (p1 - p0 - 1.2))));

  // Phone.
  const [f0, f1] = story.phone;
  const pp = presence(t, f0, f1, 0.5, 0.4);
  const s = Number($("phone").dataset.scale);
  $("phone").style.opacity = String(pp);
  $("phone").style.transform = `translateY(${(1 - easeOut((t - f0) / 0.6)) * 120 + clamp((t - f1) / 0.4) * 60}px) scale(${s * (0.92 + 0.08 * easeOut((t - f0) / 0.6))})`;
  renderChat(t);

  // Time-saved stat.
  if (story.stat) {
    const [s0, s1] = story.stat;
    $("stat").style.opacity = String(presence(t, s0 + 0.2, s1, 0.4, 0.3));
    const rows = document.querySelectorAll(".stat-row");
    rows.forEach((row, i) => {
      const at = s0 + 0.3 + i * 0.6;
      row.style.opacity = String(t >= at ? easeOut((t - at) / 0.35) : 0);
      row.style.transform = `translateX(${(1 - easeOut((t - at) / 0.35)) * -60}px)`;
    });
    const minutes = Math.round(29 * easeOut((t - s0 - 1.6) / 1.2));
    $("stat-saved").textContent = `± ${minutes} menit`;
  }

  // End card.
  const ep = t >= story.end ? easeOut((t - story.end) / 0.6) : 0;
  $("end").style.opacity = String(ep);
  $("end").style.transform = `scale(${0.92 + 0.08 * ep})`;
  const pulse = t > story.end + 1 ? 1 + Math.sin((t - story.end - 1) * 4) * 0.025 : 1;
  document.querySelector("#end .cta").style.transform = `scale(${pulse})`;
}

function renderChat(t) {
  const msgs = [];
  let active = null;
  for (const [at, name] of story.demos) {
    const d = DEMOS[name], k = demoTimes(at, d);
    if (t >= at) msgs.push(bubble("in", d.ask, t - at));
    if (t >= k.sent) msgs.push(bubble("out", d.reply, t - k.sent));
    if (t >= at && t < k.sent) active = { d, k };
  }
  $("msgs").innerHTML = msgs.join("");
  // Highlight the name and city while the caption talks about them.
  const [h0, h1] = story.highlight;
  const hp = presence(t, h0, h1, 0.3, 0.4);
  document.querySelectorAll(".bubble.out .hl").forEach((el) => {
    el.style.background = `rgba(56, 189, 248, ${0.45 * hp})`;
    el.style.boxShadow = `0 0 0 2px rgba(14, 165, 233, ${0.6 * hp})`;
  });
  // Composer, suggestion strip, and the tap on the suggestion.
  let typed = "";
  let strip = false, tapScale = 1;
  if (active) {
    const { d, k } = active;
    if (t >= k.typeStart) typed = k.chars.slice(0, Math.min(k.chars.length, Math.floor((t - k.typeStart) / CHAR) + 1));
    if (t >= k.tap + 0.1) typed = "";
    strip = typed.length >= 2 || (t >= k.typeEnd && t < k.sent);
    $("chip-key").textContent = `/${d.key}`;
    $("chip-title").textContent = d.title;
    if (t >= k.tap && t < k.tap + 0.25) tapScale = 0.93;
    $("slash-key").style.filter = t >= k.typeStart && t < k.typeStart + 0.15 ? "brightness(1.4)" : "none";
  }
  $("typed").textContent = typed;
  $("ph").style.display = typed ? "none" : "";
  $("caret").style.opacity = typed || Math.floor(t * 2) % 2 === 0 ? "1" : "0";
  $("strip").style.visibility = strip ? "visible" : "hidden";
  $("chip").style.transform = `scale(${tapScale})`;
  $("chip").style.background = tapScale < 1 ? "#e0f2fe" : "#fff";
}

function bubble(side, html, age) {
  const p = easeOut(age / 0.3);
  return `<div class="bubble ${side}" style="opacity:${p};transform:translateY(${(1 - p) * 14}px) scale(${0.96 + 0.04 * p})">${html}</div>`;
}

/** When the sounds play: incoming message, each typed key, the tap, the sent reply, pile pops. */
function soundCues(length) {
  const st = STORIES[length], cues = [];
  for (const [at, name] of st.demos) {
    const k = demoTimes(at, DEMOS[name]);
    cues.push({ t: at, kind: "in" });
    for (let i = 0; i < k.chars.length; i++) cues.push({ t: k.typeStart + i * CHAR, kind: "tick" });
    cues.push({ t: k.tap, kind: "tap" }, { t: k.sent, kind: "sent" });
  }
  if (st.pile) PILE.forEach((_, i) => cues.push({ t: st.pile[0] + 0.2 + i * 0.42, kind: "pop" }));
  cues.push({ t: st.end, kind: "end" });
  return cues.sort((a, b) => a.t - b.t);
}

window.setupScene = setup;
window.render = render;
window.soundCues = soundCues;

// Preview in a browser: scene.html?story=30&t=12.5
const params = new URLSearchParams(location.search);
if (params.has("t")) document.fonts.ready.then(() => { setup(Number(params.get("story") || 15)); render(Number(params.get("t"))); });
