// /bayar: sign in with an email code, pick a Pro plan (prices from payment_quote, with the founder
// price), and go to Duitku's payment page through the `payments` Edge Function. Duitku sends the
// buyer back to /bayar?order=SG-…, where the order's status is shown until it is paid.
// The session lives in sessionStorage only (this tab), never in localStorage or cookies.
(() => {
  const SUPABASE = "https://hsanqncbfurjhnshdpnc.supabase.co";
  const KEY = "sb_publishable_8YEhzjHcC9OOFN0n7B1vUg_E7540P_A";
  const STORE = "scriptgo-pay-session";
  const $ = (id) => document.getElementById(id);
  const steps = ["email-step", "code-step", "plans-step", "order-step"];
  const show = (id) => steps.forEach((s) => { $(s).hidden = s !== id; });
  const say = (id, text, error = false) => { $(id).textContent = text; $(id).classList.toggle("error", error); };
  const rupiah = (n) => "Rp" + Number(n).toLocaleString("id-ID");

  const session = {
    get() { try { return JSON.parse(sessionStorage.getItem(STORE) || "null"); } catch { return null; } },
    set(s) { try { sessionStorage.setItem(STORE, JSON.stringify(s)); } catch { /* private mode: this page only */ } memory = s; },
    clear() { try { sessionStorage.removeItem(STORE); } catch { /* ignore */ } memory = null; },
  };
  let memory = session.get();

  async function call(path, { method = "POST", body, auth = false } = {}) {
    const headers = { apikey: KEY, "Content-Type": "application/json" };
    if (auth && memory) headers.Authorization = `Bearer ${memory.token}`;
    let res;
    try {
      res = await fetch(SUPABASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    } catch {
      throw Object.assign(new Error("Gagal terhubung. Cek koneksi internet, lalu coba lagi."), { status: 0 });
    }
    const text = await res.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch { /* not JSON */ }
    if (!res.ok) throw Object.assign(new Error(json?.error_code || json?.error || json?.message || `http_${res.status}`), { status: res.status });
    return json;
  }

  // 1. Email → a 6-digit code (the same sign-in email the app uses).
  $("email-step").addEventListener("submit", async (e) => {
    e.preventDefault();
    const email = $("email").value.trim().toLowerCase();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return say("email-msg", "Email belum benar.", true);
    $("send-code").disabled = true;
    say("email-msg", "Mengirim…");
    try {
      await call("/auth/v1/otp", { body: { email, create_user: true } });
      $("code-email").textContent = email;
      say("email-msg", "");
      show("code-step");
      $("code").focus();
    } catch (err) {
      say("email-msg", err.status === 429 || err.message.includes("rate_limit") ? "Terlalu sering. Tunggu sebentar, lalu coba lagi." : err.status === 0 ? err.message : "Kode belum bisa dikirim. Coba lagi.", true);
    } finally {
      $("send-code").disabled = false;
    }
  });
  $("change-email").addEventListener("click", () => { say("code-msg", ""); show("email-step"); });

  // 2. Code → a session for this tab.
  $("code-step").addEventListener("submit", async (e) => {
    e.preventDefault();
    const token = $("code").value.replace(/\D/g, "");
    if (token.length !== 6) return say("code-msg", "Kodenya 6 angka.", true);
    $("verify").disabled = true;
    say("code-msg", "Memeriksa…");
    try {
      const json = await call("/auth/v1/verify", { body: { type: "email", email: $("code-email").textContent, token } });
      session.set({ token: json.access_token, email: json.user?.email || $("code-email").textContent, expires: Date.now() + (json.expires_in || 3600) * 1000 });
      say("code-msg", "");
      await showPlans();
    } catch (err) {
      say("code-msg", err.status === 0 ? err.message : "Kode salah atau sudah kedaluwarsa. Minta kode baru.", true);
    } finally {
      $("verify").disabled = false;
    }
  });

  // 3. Plans from payment_quote (prices are computed on the server, never here).
  let chosen = null;
  async function showPlans() {
    show("plans-step");
    $("plans-account").textContent = memory.email;
    say("plans-msg", "Memuat harga…");
    try {
      const quote = await call("/rest/v1/rpc/payment_quote", { body: {}, auth: true });
      if (quote.founder) $("plans-note").textContent = "Harga pendiri untuk pendaftar beta: setengah harga. Template dan pemakaian tanpa batas, sync antar HP, Chrome extension, dan AI 20 kali sehari.";
      $("plans").replaceChildren(...quote.products.map(planCard));
      choose(quote.products.find((p) => p.months === 12)?.product || quote.products[0].product);
      say("plans-msg", "");
    } catch (err) {
      if (err.status === 401 || err.status === 403) return signOut();
      say("plans-msg", err.status === 0 ? err.message : "Harga belum bisa dimuat. Muat ulang halaman ini.", true);
    }
  }
  function planCard(p) {
    const monthly = Math.round(p.amount / p.months);
    const card = document.createElement("button");
    card.type = "button";
    card.className = "plan-option";
    card.setAttribute("role", "radio");
    card.dataset.product = p.product;
    card.innerHTML = `<span class="plan-name"></span><span class="plan-price"></span><span class="plan-sub"></span><s class="plan-normal"></s>`;
    card.querySelector(".plan-name").textContent = `${p.months} bulan`;
    if (p.months === 12) card.querySelector(".plan-name").insertAdjacentHTML("beforeend", ` <span class="plan-badge">Paling hemat</span>`);
    card.querySelector(".plan-price").textContent = rupiah(p.amount);
    card.querySelector(".plan-sub").textContent = `${rupiah(monthly)}/bulan`;
    // Founder price: the normal price, struck through, under the price.
    if (p.amount < p.normal) card.querySelector(".plan-normal").textContent = rupiah(p.normal);
    else card.querySelector(".plan-normal").remove();
    card.addEventListener("click", () => choose(p.product));
    return card;
  }
  function choose(product) {
    chosen = product;
    for (const el of $("plans").children) el.setAttribute("aria-checked", String(el.dataset.product === product));
    $("pay").disabled = false;
  }
  $("pay").addEventListener("click", async () => {
    if (!chosen) return;
    $("pay").disabled = true;
    say("plans-msg", "Membuat tagihan…");
    try {
      const { paymentUrl } = await call("/functions/v1/payments", { body: { product: chosen }, auth: true });
      location.href = paymentUrl;
    } catch (err) {
      $("pay").disabled = false;
      if (err.status === 401) return signOut();
      const messages = {
        payments_not_configured: "Pembayaran belum dibuka. Coba lagi nanti.",
        too_many_orders: "Terlalu banyak tagihan hari ini. Selesaikan tagihan sebelumnya, atau coba lagi besok.",
        gateway_failed: "Halaman bayar belum bisa dibuka. Coba lagi sebentar lagi.",
      };
      say("plans-msg", messages[err.message] || (err.status === 0 ? err.message : "Tagihan belum bisa dibuat. Coba lagi."), true);
    }
  });
  function signOut() {
    session.clear();
    show("email-step");
  }
  $("sign-out").addEventListener("click", signOut);

  // 4. Back from Duitku.
  async function showOrder(id) {
    show("order-step");
    // state: "wait" (clock, pulsing), "ok" (check), "fail" (cross); the icon never says done before it is.
    const text = (title, body, again = false, state = "ok") => {
      $("order-title").textContent = title;
      $("order-text").textContent = body;
      $("order-again").hidden = !again;
      $("order-icon").dataset.state = state;
    };
    text("Mengecek pembayaran…", "", false, "wait");
    if (!memory) return text("Terima kasih!", `Pesanan ${id} tercatat. Kalau pembayaran sudah selesai, Pro aktif otomatis dalam beberapa menit. Cek di app: Akun → Paket.`, false, "wait");
    for (let i = 0; i < 24; i++) {
      try {
        const rows = await call(`/rest/v1/orders?id=eq.${encodeURIComponent(id)}&select=status,months`, { method: "GET", auth: true });
        const order = rows?.[0];
        if (order?.status === "paid") return text("Pro sudah aktif", `Terima kasih! ${order.months} bulan Pro sudah ditambahkan ke akun ${memory.email}. Buka app atau Chrome extension, lalu tekan Sinkron.`);
        if (order?.status === "failed") return text("Pembayaran belum berhasil", "Tagihan ini dibatalkan atau kedaluwarsa. Kamu bisa membuat tagihan baru.", true, "fail");
        text("Menunggu pembayaran…", "Halaman ini diperbarui sendiri. Sudah bayar? Biasanya Pro aktif kurang dari satu menit.", false, "wait");
      } catch (err) {
        if (err.status === 401) { session.clear(); memory = null; return showOrder(id); }
      }
      await new Promise((r) => setTimeout(r, 5000));
    }
    text("Masih menunggu pembayaran", "Kalau sudah bayar, Pro aktif otomatis. Cek lagi nanti di app: Akun → Paket.", true, "wait");
  }

  const order = new URLSearchParams(location.search).get("order") || "";
  if (memory && memory.expires < Date.now()) session.clear();
  if (/^SG-[0-9A-Z]{10}$/.test(order)) showOrder(order);
  else if (memory) showPlans();
  else show("email-step");
})();
