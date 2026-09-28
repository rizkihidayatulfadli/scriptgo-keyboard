// ScriptGo admin dashboard. Signs in with an email code (Supabase auth, as on /bayar) and calls the
// admin_* RPCs, which check private.admins on the server; this page only shows what they return.
// Served inside the owner's tailnet only (docs/admin.md). The session lives in sessionStorage.
(() => {
  const SUPABASE = "https://hsanqncbfurjhnshdpnc.supabase.co";
  const KEY = "sb_publishable_8YEhzjHcC9OOFN0n7B1vUg_E7540P_A";
  const STORE = "scriptgo-admin-session";
  const $ = (id) => document.getElementById(id);

  /** An element with attributes (functions become listeners) and children; text is never parsed as HTML. */
  function h(tag, attrs = {}, ...children) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
      if (v === undefined || v === null || v === false) continue;
      if (typeof v === "function") el.addEventListener(k, v);
      else el.setAttribute(k, v === true ? "" : String(v));
    }
    for (const c of children.flat()) if (c !== null && c !== undefined && c !== false) el.append(c instanceof Node ? c : String(c));
    return el;
  }
  const rupiah = (n) => "Rp" + Number(n || 0).toLocaleString("id-ID");
  const when = (iso) => (iso ? new Date(iso).toLocaleString("id-ID", { dateStyle: "medium", timeStyle: "short" }) : "—");
  const day = (iso) => (iso ? new Date(iso).toLocaleDateString("id-ID", { dateStyle: "medium" }) : "—");
  const say = (id, text, error = false) => { $(id).textContent = text; $(id).classList.toggle("error", error); };
  let toastTimer;
  const toast = (text) => {
    $("toast").textContent = text;
    $("toast").hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => ($("toast").hidden = true), 2600);
  };

  // --- Session ----------------------------------------------------------------------------------
  let session = null;
  try { session = JSON.parse(sessionStorage.getItem(STORE) || "null"); } catch { session = null; }
  const saveSession = (s) => { session = s; try { s ? sessionStorage.setItem(STORE, JSON.stringify(s)) : sessionStorage.removeItem(STORE); } catch { /* private mode */ } };

  async function call(path, { method = "POST", body, auth = true } = {}) {
    const headers = { apikey: KEY, "Content-Type": "application/json" };
    if (auth && session) headers.Authorization = `Bearer ${session.token}`;
    let res;
    try {
      res = await fetch(SUPABASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    } catch {
      throw Object.assign(new Error("Gagal terhubung. Cek koneksi."), { status: 0 });
    }
    const text = await res.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch { /* not JSON */ }
    if (!res.ok) throw Object.assign(new Error(json?.message || json?.error_code || json?.error || `http_${res.status}`), { status: res.status });
    return json;
  }
  const rpc = (name, args = {}) => call(`/rest/v1/rpc/${name}`, { body: args });

  // --- Sign-in ----------------------------------------------------------------------------------
  $("email-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const email = $("email").value.trim().toLowerCase();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return say("signin-msg", "Email belum benar.", true);
    $("send-code").disabled = true;
    say("signin-msg", "Mengirim…");
    try {
      // create_user false: the dashboard never makes new accounts.
      await call("/auth/v1/otp", { body: { email, create_user: false }, auth: false });
      $("code-email").textContent = email;
      $("email-form").hidden = true;
      $("code-form").hidden = false;
      say("signin-msg", "");
      $("code").focus();
    } catch (err) {
      say("signin-msg", err.status === 429 ? "Terlalu sering. Tunggu sebentar." : "Kode belum bisa dikirim.", true);
    } finally {
      $("send-code").disabled = false;
    }
  });
  $("change-email").addEventListener("click", () => { $("code-form").hidden = true; $("email-form").hidden = false; say("signin-msg", ""); });
  $("code-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const token = $("code").value.replace(/\D/g, "");
    if (token.length !== 6) return say("signin-msg", "Kodenya 6 angka.", true);
    $("verify").disabled = true;
    try {
      const json = await call("/auth/v1/verify", { body: { type: "email", email: $("code-email").textContent, token }, auth: false });
      saveSession({ token: json.access_token, email: json.user?.email ?? "", expires: Date.now() + (json.expires_in || 3600) * 1000 });
      await enter();
    } catch {
      say("signin-msg", "Kode salah atau kedaluwarsa.", true);
    } finally {
      $("verify").disabled = false;
    }
  });
  $("sign-out").addEventListener("click", () => signOut());
  function signOut(message = "") {
    saveSession(null);
    $("app").hidden = true;
    $("who").hidden = true;
    $("signin").hidden = false;
    $("code-form").hidden = true;
    $("email-form").hidden = false;
    say("signin-msg", message, Boolean(message));
  }

  async function enter() {
    let ok = false;
    try { ok = await rpc("admin_check"); } catch (err) { if (err.status === 401) return signOut("Sesi habis. Masuk lagi."); }
    if (!ok) return signOut("Akun ini bukan admin.");
    $("signin").hidden = true;
    $("app").hidden = false;
    $("who").hidden = false;
    $("who-email").textContent = session.email;
    show(location.hash.slice(1) || "overview", false);
  }

  // --- Tabs -------------------------------------------------------------------------------------
  const TABS = { overview, beta, users, orders, inbox };
  let current = "overview";
  document.querySelectorAll("[data-tab]").forEach((b) => b.addEventListener("click", () => show(b.dataset.tab)));
  // Back/forward and an edited #tab in the address bar switch tabs too.
  addEventListener("hashchange", () => {
    const tab = location.hash.slice(1);
    if (session && tab !== current && TABS[tab]) show(tab);
  });
  async function show(tab, push = true) {
    if (!TABS[tab]) tab = "overview";
    current = tab;
    // The first tab after signing in replaces the entry, so Back leaves the dashboard as usual.
    if (location.hash !== `#${tab}`) history[push ? "pushState" : "replaceState"](null, "", `#${tab}`);
    document.querySelectorAll("[data-tab]").forEach((b) => b.setAttribute("aria-selected", String(b.dataset.tab === tab)));
    // On a phone the tab row scrolls; keep the chosen tab in view.
    document.querySelector(`[data-tab="${tab}"]`)?.scrollIntoView({ block: "nearest", inline: "nearest" });
    say("app-msg", "Memuat…");
    try {
      const view = await TABS[tab]();
      if (current !== tab) return;
      $("view").replaceChildren(view);
      say("app-msg", "");
    } catch (err) {
      if (err.status === 401) return signOut("Sesi habis. Masuk lagi.");
      say("app-msg", err.message === "not_admin" ? "Akun ini bukan admin." : `Gagal memuat: ${err.message}`, true);
    }
  }
  const reload = () => show(current);

  /** Runs an admin action, shows [done] or the error, and reloads the tab. */
  async function act(fn, done) {
    try {
      await fn();
      toast(done);
      await reload();
    } catch (err) {
      const known = { email_not_verified: "Email belum dikonfirmasi, belum bisa disetujui atau diundang.", not_admin: "Akun ini bukan admin." };
      say("app-msg", known[err.message] ?? `Gagal: ${err.message}`, true);
    }
  }

  const stat = (label, value, hot = false) => h("div", { class: hot ? "stat hot" : "stat" }, h("b", {}, value), h("span", {}, label));
  const table = (head, rows, empty = "Belum ada data.") =>
    rows.length
      ? h("div", { class: "table-wrap" }, h("table", {}, h("thead", {}, h("tr", {}, head.map((t) => h("th", {}, t)))), h("tbody", {}, rows)))
      : h("div", { class: "card empty" }, empty);
  const planPill = (plan) => {
    const p = plan?.plan ?? "free";
    const cls = { pro: "ok", trial: "warn", beta: "", free: "muted" }[p] ?? "muted";
    const label = { pro: "Pro", trial: "Trial", beta: "Beta", free: "Gratis" }[p] ?? p;
    return h("span", { class: `pill ${cls}` }, label);
  };
  const statusPill = (s) => h("span", { class: `pill ${{ pending: "warn", approved: "", invited: "ok", rejected: "bad", paid: "ok", failed: "bad", new: "warn", read: "", done: "ok" }[s] ?? "muted"}` }, s);

  // --- Ringkasan --------------------------------------------------------------------------------
  async function overview() {
    const [o, pay] = await Promise.all([rpc("admin_overview"), rpc("admin_orders", { lim: 1, days: 30 })]);
    const wl = o.waitlist ?? {};
    const max = Math.max(1, ...pay.daily.map((d) => d.revenue));
    const limits = h("input", { type: "date", "aria-label": "Tanggal mulai batas gratis" });
    if (o.limits_from) limits.value = o.limits_from.slice(0, 10);
    return h(
      "div",
      { class: "stack" },
      h(
        "div",
        { class: "grid" },
        stat("Pendapatan hari ini", rupiah(o.revenue_today), true),
        stat("Pendapatan bulan ini", rupiah(o.revenue_month), true),
        stat("Pro aktif", o.pro_active),
        stat(`Akun · +${o.users_7d} minggu ini`, o.users),
        stat("Waitlist menunggu", wl.pending ?? 0),
        stat("Waitlist terkonfirmasi", o.waitlist_verified),
        stat("Diundang ke beta", wl.invited ?? 0),
        stat("Tagihan terbuka 24 jam", o.orders_pending),
        stat("Pemakaian AI hari ini", o.ai_today),
        stat("Laporan AI baru", o.reports_new),
        stat("Pesan bantuan baru", o.support_new),
      ),
      h(
        "div",
        { class: "card stack" },
        h("h2", {}, "Pendapatan 30 hari"),
        h("div", { class: "chart", role: "img", "aria-label": "Grafik pendapatan per hari" },
          // Heights are set through CSSOM: the page's CSP blocks style attributes.
          pay.daily.map((d) => {
            const bar = h("div", { class: "bar", "data-tip": `${day(d.day)} · ${rupiah(d.revenue)} · ${d.orders} order` });
            bar.style.height = `${Math.round((d.revenue / max) * 100)}%`;
            return bar;
          })),
      ),
      h(
        "div",
        { class: "card stack" },
        h("h2", {}, "Saklar batas Gratis (limits_from)"),
        h("p", { class: "muted" }, o.limits_active
          ? `Aktif sejak ${day(o.limits_from)}: batas Gratis, trial 14 hari, dan jendela harga pendiri berjalan.`
          : o.limits_from ? `Dijadwalkan mulai ${day(o.limits_from)}. Sampai hari itu semua pengguna tetap beta.` : "Belum diatur: semua pengguna masih beta (fitur Pro gratis)."),
        h("div", { class: "row" }, limits,
          h("button", { type: "button", click: () => {
            if (!limits.value) return;
            if (!confirm(`Nyalakan batas Gratis mulai ${limits.value}? Pengguna yang bukan Pro akan kena batas mulai tanggal itu.`)) return;
            act(() => rpc("admin_set_limits_from", { starts: new Date(`${limits.value}T00:00:00+07:00`).toISOString() }), "Saklar disimpan.");
          } }, "Simpan tanggal"),
          o.limits_from ? h("button", { type: "button", class: "danger", click: () => {
            if (confirm("Hapus saklar? Semua pengguna kembali ke beta.")) act(() => rpc("admin_set_limits_from", { starts: null }), "Saklar dihapus.");
          } }, "Hapus saklar") : null),
      ),
    );
  }

  // --- Beta & waitlist --------------------------------------------------------------------------
  let betaFilter = { status: "", search: "" };
  async function beta() {
    const rows = await rpc("admin_waitlist", { status: betaFilter.status, search: betaFilter.search, lim: 300, off: 0 });
    const status = h("select", { "aria-label": "Status", change: (e) => { betaFilter.status = e.target.value; reload(); } },
      [["", "Semua status"], ["pending", "Menunggu"], ["approved", "Disetujui"], ["invited", "Diundang"], ["rejected", "Ditolak"]].map(([v, t]) => h("option", { value: v }, t)));
    status.value = betaFilter.status;
    const search = h("input", { type: "search", placeholder: "Cari email, nama, atau WhatsApp", value: betaFilter.search });
    search.addEventListener("keydown", (e) => { if (e.key === "Enter") { betaFilter.search = search.value.trim(); reload(); } });
    const move = (w, to, label) => h("button", { type: "button", class: to === "rejected" ? "small danger" : "small", click: () => {
      if (to === "invited" && !confirm(`Undang ${w.email}? Email undangan beta langsung terkirim.`)) return;
      act(() => rpc("admin_set_beta_status", { waitlist_id: w.id, new_status: to }), `${w.email}: ${label}.`);
    } }, label);
    return h(
      "div",
      {},
      h("div", { class: "toolbar" }, search, status, h("span", { class: "muted" }, `${rows.length} baris`)),
      table(
        ["Pendaftar", "Usaha", "Perangkat", "Status", "Daftar", "Aksi"],
        rows.map((w) => h("tr", {},
          h("td", {}, h("b", {}, w.name || "—"), h("span", { class: "sub" }, w.email), w.whatsapp ? h("span", { class: "sub" }, w.whatsapp) : null),
          h("td", {}, w.business_type || "—", h("span", { class: "sub" }, `${w.role || "—"}${w.referrals ? ` · ${w.referrals} ajakan` : ""}`)),
          h("td", {}, w.device || "—"),
          h("td", {}, statusPill(w.beta_status), h("span", { class: "sub" }, w.email_verified_at ? "Email terkonfirmasi" : "Belum konfirmasi email")),
          h("td", {}, day(w.created_at), h("span", { class: "sub" }, w.source || "")),
          h("td", { class: "actions" },
            w.beta_status === "pending" ? move(w, "approved", "Setujui") : null,
            w.beta_status === "approved" ? move(w, "invited", "Undang") : null,
            w.beta_status !== "rejected" && w.beta_status !== "invited" ? move(w, "rejected", "Tolak") : null,
            w.beta_status === "rejected" ? move(w, "pending", "Kembalikan") : null),
        )),
        "Tidak ada pendaftar yang cocok.",
      ),
    );
  }

  // --- Pengguna & paket -------------------------------------------------------------------------
  let userSearch = "";
  async function users() {
    const rows = await rpc("admin_users", { search: userSearch, lim: 300, off: 0 });
    const search = h("input", { type: "search", placeholder: "Cari email", value: userSearch });
    search.addEventListener("keydown", (e) => { if (e.key === "Enter") { userSearch = search.value.trim(); reload(); } });
    const grant = (u, months) => h("button", { type: "button", class: "small", click: () => {
      const label = months ? `${months} bulan` : "tanpa batas waktu";
      const reason = prompt(`Kasih Pro ${label} ke ${u.email}? Tulis alasannya (disimpan):`, "hadiah beta");
      if (reason === null) return;
      act(() => rpc("admin_grant_pro", { target: u.id, months, reason }), `Pro ${label} untuk ${u.email}.`);
    } }, months ? `+${months} bln` : "Pro ∞");
    return h(
      "div",
      {},
      h("div", { class: "toolbar" }, search, h("span", { class: "muted" }, `${rows.length} akun`)),
      table(
        ["Akun", "Paket", "Isi", "Aktif", "Aksi"],
        rows.map((u) => h("tr", {},
          h("td", {}, h("b", {}, u.email), u.admin ? h("span", { class: "pill" }, "admin") : null, h("span", { class: "sub" }, [u.business_name, u.city].filter(Boolean).join(" · ") || "Belum isi profil usaha")),
          h("td", {}, planPill(u.plan), h("span", { class: "sub" }, u.plan?.plan === "pro" ? (u.plan.pro_until ? `sampai ${day(u.plan.pro_until)}` : "tanpa batas") : u.plan?.trial_ends_at ? `trial sampai ${day(u.plan.trial_ends_at)}` : ""), u.note ? h("span", { class: "sub" }, u.note) : null),
          h("td", {}, `${u.templates} template`, h("span", { class: "sub" }, `${u.contacts} kontak`)),
          h("td", {}, day(u.created_at), h("span", { class: "sub" }, `masuk ${when(u.last_sign_in_at)}`)),
          h("td", { class: "actions" }, grant(u, 1), grant(u, 3), grant(u, 12), grant(u, null),
            u.plan?.plan === "pro" ? h("button", { type: "button", class: "small danger", click: () => {
              const reason = prompt(`Cabut Pro ${u.email}? Tulis alasannya:`, "");
              if (reason === null) return;
              act(() => rpc("admin_revoke_pro", { target: u.id, reason }), `Pro ${u.email} dicabut.`);
            } }, "Cabut") : null),
        )),
        "Tidak ada akun yang cocok.",
      ),
    );
  }

  // --- Pembayaran -------------------------------------------------------------------------------
  let orderStatus = "";
  async function orders() {
    const data = await rpc("admin_orders", { status: orderStatus, lim: 300, days: 30 });
    const status = h("select", { "aria-label": "Status order", change: (e) => { orderStatus = e.target.value; reload(); } },
      [["", "Semua"], ["paid", "Lunas"], ["pending", "Menunggu"], ["failed", "Gagal"]].map(([v, t]) => h("option", { value: v }, t)));
    status.value = orderStatus;
    const paid = data.daily.reduce((s, d) => s + d.revenue, 0);
    return h(
      "div",
      {},
      h("div", { class: "grid" }, stat("Lunas 30 hari", rupiah(paid), true), stat("Order lunas 30 hari", data.daily.reduce((s, d) => s + d.orders, 0))),
      h("div", { class: "toolbar" }, status),
      table(
        ["Order", "Pembeli", "Paket", "Nominal", "Status", "Waktu"],
        data.orders.map((o) => h("tr", {},
          h("td", {}, h("b", {}, o.id), h("span", { class: "sub" }, o.reference || "")),
          h("td", {}, o.email ?? "(akun dihapus)"),
          h("td", {}, `Pro ${o.months} bulan`, o.founder ? h("span", { class: "sub" }, "harga pendiri") : null),
          h("td", {}, rupiah(o.amount)),
          h("td", {}, statusPill(o.status)),
          h("td", {}, when(o.created_at), o.paid_at ? h("span", { class: "sub" }, `lunas ${when(o.paid_at)}`) : null),
        )),
        "Belum ada order.",
      ),
    );
  }

  // --- Laporan AI, pesan bantuan, A/B -----------------------------------------------------------
  let inboxStatus = "new";
  async function inbox() {
    const [data, ab] = await Promise.all([rpc("admin_inbox", { status: inboxStatus, lim: 200 }), rpc("admin_ab_report", { experiment: "hero-1" })]);
    const status = h("select", { "aria-label": "Status pesan", change: (e) => { inboxStatus = e.target.value; reload(); } },
      [["new", "Baru"], ["read", "Dibaca"], ["done", "Selesai"], ["", "Semua"]].map(([v, t]) => h("option", { value: v }, t)));
    status.value = inboxStatus;
    const mark = (kind, item) => h("span", {},
      item.status !== "read" ? h("button", { type: "button", class: "small", click: () => act(() => rpc("admin_set_inbox_status", { kind, item: item.id, new_status: "read" }), "Ditandai dibaca.") }, "Dibaca") : null,
      item.status !== "done" ? h("button", { type: "button", class: "small", click: () => act(() => rpc("admin_set_inbox_status", { kind, item: item.id, new_status: "done" }), "Ditandai selesai.") }, "Selesai") : null);
    const reason = { offensive: "Menyinggung", wrong: "Salah", other: "Lainnya" };
    const kind = { help: "Bantuan", idea: "Saran", bug: "Masalah" };
    const calls = data.ai_daily.reduce((s, d) => s + Number(d.calls), 0);
    const pct = (x) => (x === null || x === undefined ? "—" : `${(Number(x) * 100).toFixed(1)}%`);
    return h(
      "div",
      { class: "stack" },
      h("div", { class: "toolbar" }, status, h("span", { class: "muted" }, `AI 30 hari: ${calls} kali`)),
      h("h2", {}, "Pesan bantuan"),
      table(
        ["Dari", "Jenis", "Pesan", "Status", "Aksi"],
        data.support.map((m) => h("tr", {},
          h("td", {}, m.email ?? "—", m.reply_email ? h("span", { class: "sub" }, `balas ke ${m.reply_email}`) : null, h("span", { class: "sub" }, `${m.source} ${m.app_version || ""} · ${when(m.created_at)}`)),
          h("td", {}, kind[m.kind] ?? m.kind),
          h("td", { class: "long" }, m.message),
          h("td", {}, statusPill(m.status)),
          h("td", { class: "actions" }, mark("support", m)),
        )),
        "Tidak ada pesan.",
      ),
      h("h2", {}, "Laporan hasil AI"),
      table(
        ["Dari", "Alasan", "Permintaan", "Hasil AI", "Status", "Aksi"],
        data.reports.map((r) => h("tr", {},
          h("td", {}, r.email ?? "—", h("span", { class: "sub" }, `${r.source} · ${when(r.created_at)}`)),
          h("td", {}, reason[r.reason] ?? r.reason, r.note ? h("span", { class: "sub" }, r.note) : null),
          h("td", { class: "long" }, r.input),
          h("td", { class: "long" }, r.result),
          h("td", {}, statusPill(r.status)),
          h("td", { class: "actions" }, mark("report", r)),
        )),
        "Tidak ada laporan.",
      ),
      h("h2", {}, "A/B judul hero (hero-1)"),
      table(
        ["Varian", "Pengunjung", "Klik daftar", "Daftar", "Terkonfirmasi", "Rasio klik", "Rasio konfirmasi", "Peluang B menang"],
        ab.map((r) => h("tr", {}, h("td", {}, h("b", {}, r.variant.toUpperCase())), h("td", {}, r.visitors), h("td", {}, r.clicks), h("td", {}, r.signups), h("td", {}, r.confirmed),
          h("td", {}, pct(r.click_rate)), h("td", {}, pct(r.confirm_rate)), h("td", {}, pct(r.chance_b_wins_confirmed)))),
        "Belum ada data A/B.",
      ),
    );
  }

  if (session && session.expires > Date.now()) enter();
  else saveSession(null);
})();
