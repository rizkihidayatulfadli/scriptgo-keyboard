// Landing page: headline A/B tracking, interactive demo, problem pile, time-saved calculator,
// scroll reveals, and sign-up links that carry ?ref and utm_*. Everything animated has a static
// form under prefers-reduced-motion.

// Demo templates, filled for the sample contact Rina from Bandung (as the app would).
const TEMPLATES = {
  ongkir: { title: "Info ongkir", ask: "Kak, ongkir ke Bandung berapa?", reply: "Halo Kak Rina, ongkir ke Bandung Rp18.000 via JNE (2–3 hari). Mau langsung diproses?" },
  rekening: { title: "Nomor rekening", ask: "Transfernya ke mana kak?", reply: "Nomor rekening BCA 1234567890 a.n. Toko Rina ya Kak. Kirim bukti transfer di sini 🙏" },
  salam: { title: "Salam pembuka", ask: "Halo kak, mau tanya-tanya dulu", reply: "Halo Kak Rina! Terima kasih sudah mampir ke Toko Rina 😊 Ada yang bisa dibantu?" },
  followup: { title: "Follow-up kirim", ask: "Kak, paketku udah dikirim belum?", reply: "Kak Rina, pesanannya sudah kami kirim hari ini. No. resi JNE123456789, bisa dicek besok ya." },
};

(() => {
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const $ = (id) => document.getElementById(id);
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  // Headline A/B test: the inline script under the headline picked the variant; count one view
  // and the first sign-up click of this visitor. Failures are ignored (the page works without it).
  const ab = window.ScriptGoAB;
  const track = (event, keepalive = false) => {
    if (!ab?.counted || !window.ScriptGo?.rpc) return;
    window.ScriptGo.rpc("ab_track", { p_experiment: ab.experiment, p_variant: ab.variant, p_visitor: ab.visitor, p_event: event }, { keepalive }).catch(() => {});
  };
  track("view");

  // Sign-up links keep the referral code and campaign of this visit.
  const params = new URLSearchParams(location.search);
  const carry = new URLSearchParams();
  const ref = params.get("ref") || "";
  if (/^[a-z0-9]{6,12}$/.test(ref)) carry.set("ref", ref);
  for (const k of ["utm_source", "utm_medium", "utm_campaign"]) if (params.get(k)) carry.set(k, params.get(k).slice(0, 80));
  if ([...carry].length) document.querySelectorAll("a[data-cta]").forEach((a) => { a.href = `${a.getAttribute("href")}?${carry}`; });
  document.querySelectorAll("a[data-cta]").forEach((a) => a.addEventListener("click", () => track("cta", true)));

  // Demo: the customer asks, "/key" is typed, the suggestion is tapped, the reply is sent.
  const ask = $("demo-ask"), reply = $("demo-reply"), typed = $("demo-typed"), ph = $("demo-ph");
  const strip = $("demo-strip"), best = strip.querySelector(".best");
  const chips = [...document.querySelectorAll(".try [data-t]")];
  let run = 0; // bumps on each new sequence so an older one stops
  const press = (key) => chips.forEach((c) => c.setAttribute("aria-pressed", String(c.dataset.t === key)));
  const showStatic = (key) => {
    press(key);
    ask.textContent = TEMPLATES[key].ask;
    reply.textContent = TEMPLATES[key].reply;
    reply.classList.add("show");
  };
  async function play(key) {
    const id = ++run;
    const alive = () => id === run;
    const t = TEMPLATES[key];
    press(key);
    reply.classList.remove("show");
    strip.classList.remove("show");
    typed.textContent = ""; ph.hidden = false;
    ask.classList.add("fade");
    await sleep(250); if (!alive()) return;
    ask.textContent = t.ask; ask.classList.remove("fade");
    await sleep(900); if (!alive()) return;
    ph.hidden = true;
    $("demo-key").textContent = `/${key}`; $("demo-title").textContent = t.title;
    for (const ch of `/${key}`) {
      typed.textContent += ch;
      if (typed.textContent.length === 3) strip.classList.add("show");
      await sleep(110); if (!alive()) return;
    }
    await sleep(500); if (!alive()) return;
    best.classList.add("tap"); await sleep(260); best.classList.remove("tap");
    if (!alive()) return;
    typed.textContent = ""; ph.hidden = false; strip.classList.remove("show");
    reply.textContent = t.reply; reply.classList.add("show");
  }
  let auto = true;
  chips.forEach((c) => c.addEventListener("click", () => {
    auto = false;
    if (reduce) showStatic(c.dataset.t); else play(c.dataset.t);
  }));
  if (reduce) showStatic("ongkir");
  else (async () => {
    const keys = Object.keys(TEMPLATES);
    for (let i = 0; auto; i = (i + 1) % keys.length) {
      await play(keys[i]);
      await sleep(3800);
    }
  })();

  // "Tonton 30 detik": swap the poster for the video only when asked, so the page stays light.
  $("watch").addEventListener("click", () => {
    const video = document.createElement("video");
    video.src = "video/scriptgo-30s.mp4";
    video.poster = "video/scriptgo-30s-poster.jpg";
    video.controls = true;
    video.autoplay = true;
    video.playsInline = true;
    video.setAttribute("aria-label", "Video ScriptGo, 30 detik");
    $("watch").replaceWith(video);
    video.focus();
  });

  // Calculator: 35 seconds saved per templated reply; the numbers count to their new value.
  const chats = $("chats");
  const fmt = (min) => min < 60 ? `± ${Math.round(min)} menit` : `± ${(min / 60).toLocaleString("id-ID", { maximumFractionDigits: 1 })} jam`;
  let shown = (Number(chats.value) * 35) / 60, frame = 0;
  const paint = (perDay) => { $("saved").textContent = fmt(perDay); $("saved-month").textContent = fmt(perDay * 30); };
  const calc = () => {
    const target = (Number(chats.value) * 35) / 60;
    $("chats-out").textContent = chats.value;
    cancelAnimationFrame(frame);
    if (reduce) { shown = target; return paint(target); }
    const from = shown, start = performance.now();
    const step = (now) => {
      const k = Math.min((now - start) / 350, 1);
      shown = from + (target - from) * (1 - (1 - k) ** 3);
      paint(shown);
      if (k < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
  };
  chats.addEventListener("input", calc);
  paint(shown);

  // Scroll reveals, and the pile of repeated chats getting answered one by one.
  const pile = [...$("pile").children];
  const answerPile = () => pile.forEach((li, i) => setTimeout(() => li.classList.add("done"), reduce ? 0 : 450 + i * 380));
  if (reduce || !("IntersectionObserver" in window)) {
    document.querySelectorAll("[data-reveal]").forEach((el) => el.classList.add("in"));
    answerPile();
    return;
  }
  document.documentElement.classList.add("reveal-on");
  const io = new IntersectionObserver((entries) => entries.forEach((e) => {
    if (!e.isIntersecting) return;
    io.unobserve(e.target);
    if (e.target.id === "pile") answerPile(); else e.target.classList.add("in");
  }), { rootMargin: "0px 0px -10% 0px" });
  document.querySelectorAll("[data-reveal]").forEach((el) => io.observe(el));
  io.observe($("pile"));
})();
