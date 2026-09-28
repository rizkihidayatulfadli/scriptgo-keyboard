// /konfirmasi?t=<token>: confirms the email through confirm_waitlist and shows the referral link.
(async () => {
  const $ = (id) => document.getElementById(id);
  const token = new URLSearchParams(location.search).get("t") || "";
  const show = (id) => ["checking", "ok", "bad"].forEach((v) => { $(v).hidden = v !== id; });
  $("ref-copy").addEventListener("click", async () => {
    try { await navigator.clipboard.writeText($("ref-link").value); $("ref-copy").textContent = "Tersalin"; }
    catch { $("ref-link").select(); }
  });
  if (!/^[0-9a-f]{64}$/.test(token)) return show("bad");
  history.replaceState(null, "", location.pathname); // keep the token out of history and screenshots
  try {
    const code = await window.ScriptGo.rpc("confirm_waitlist", { p_token: token });
    const link = `https://scriptgo.id/?ref=${code}`;
    $("ref-link").value = link;
    $("ref-wa").href = `https://wa.me/?text=${encodeURIComponent(`Aku pakai ScriptGo buat balas chat pelanggan pakai template, jadi jauh lebih cepat. Ikut beta gratis: ${link}`)}`;
    show("ok");
  } catch (e) {
    if (e.message === "network") {
      show("checking");
      $("checking").firstElementChild.textContent = "Gagal terhubung. Cek internet, lalu buka lagi link dari email.";
    } else {
      show("bad");
    }
  }
})();
