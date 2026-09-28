// /daftar: joins the beta list through join_waitlist. The server emails a confirmation link;
// only confirmed emails can be invited (docs/beta-launch.md).
(() => {
  const $ = (id) => document.getElementById(id);
  const params = new URLSearchParams(location.search);
  const ref = /^[a-z0-9]{6,12}$/.test(params.get("ref") || "") ? params.get("ref") : "";
  const source = ["utm_source", "utm_medium", "utm_campaign"].map((k) => params.get(k)).filter(Boolean).join("/").slice(0, 200);
  // The headline variant this visitor saw on the landing page (A/B test), if any.
  let ab = "";
  try { ab = JSON.parse(localStorage.getItem("sg_hero") || "null")?.v || ""; } catch { /* storage blocked */ }
  if (ab !== "a" && ab !== "b") ab = "";
  // 0812…, 62812…, +62 812… → +62812…; empty stays empty; anything else is null (invalid).
  const normalizeWa = (raw) => {
    let d = raw.replace(/\D/g, "");
    if (!d) return "";
    if (d.startsWith("62")) d = d.slice(2); else if (d.startsWith("0")) d = d.slice(1);
    return /^8\d{7,11}$/.test(d) ? `+62${d}` : null;
  };
  // Devices: any combination (someone may use all three); sent as "android,iphone,laptop" in this order.
  const chips = [...document.querySelectorAll("[data-device]")];
  chips.forEach((b) => b.addEventListener("click", () => {
    b.setAttribute("aria-pressed", String(b.getAttribute("aria-pressed") !== "true"));
    say("msg", "");
  }));
  const devices = () => chips.filter((b) => b.getAttribute("aria-pressed") === "true").map((b) => b.dataset.device).join(",");
  const errors = { rate_limited: "Terlalu sering. Coba lagi beberapa menit lagi.", invalid_input: "Ada isian yang belum benar." };
  const failed = "Gagal mengirim. Cek koneksi internet, lalu coba lagi.";
  const say = (id, text, error = false) => { $(id).textContent = text; $(id).classList.toggle("error", error); };
  let last = null, timer = 0;

  async function send(args, msgId) {
    $("submit").disabled = true; $("resend").disabled = true;
    say(msgId, "Mengirim…");
    try {
      await window.ScriptGo.rpc("join_waitlist", args);
      last = args;
      say(msgId, msgId === "sent-msg" ? "Link baru sudah dikirim." : "");
      showSent(args.p_email);
    } catch (e) {
      say(msgId, errors[e.message] || failed, true);
      if (msgId === "sent-msg") $("resend").disabled = false;
    } finally {
      $("submit").disabled = false;
    }
  }
  function showSent(email) {
    $("signup").hidden = true; $("sent").hidden = false;
    $("sent-email").textContent = email;
    const btn = $("resend");
    let left = 60;
    clearTimeout(timer);
    const tick = () => {
      btn.textContent = left > 0 ? `Kirim ulang (${left})` : "Kirim ulang";
      btn.disabled = left > 0;
      if (left-- > 0) timer = setTimeout(tick, 1000);
    };
    tick();
  }
  $("signup").addEventListener("submit", (e) => {
    e.preventDefault();
    const email = $("email").value.trim().toLowerCase();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) { say("msg", "Email belum benar.", true); $("email").focus(); return; }
    const wa = normalizeWa($("wa").value);
    if (wa === null) { say("msg", "Nomor WhatsApp belum benar. Contoh: 0812 3456 7890.", true); $("wa").focus(); return; }
    const device = devices();
    if (!device) { say("msg", "Pilih minimal satu perangkat.", true); chips[0].focus(); return; }
    send({ p_email: email, p_whatsapp: wa, p_device: device, p_referred_by: ref, p_source: source, p_ab: ab }, "msg");
  });
  $("resend").addEventListener("click", () => last && send(last, "sent-msg"));
})();
