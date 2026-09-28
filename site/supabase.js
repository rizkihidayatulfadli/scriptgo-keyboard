// Calls a Supabase RPC with the publishable key (safe to ship). Anonymous visitors can only run
// join_waitlist, confirm_waitlist, and ab_track; the waitlist table itself is closed to them.
window.ScriptGo = window.ScriptGo || {};
// keepalive lets a call finish while the page navigates away (a sign-up button click).
window.ScriptGo.rpc = async (name, args, { keepalive = false } = {}) => {
  let res;
  try {
    res = await fetch(`https://hsanqncbfurjhnshdpnc.supabase.co/rest/v1/rpc/${name}`, {
      method: "POST",
      keepalive,
      headers: { apikey: "sb_publishable_8YEhzjHcC9OOFN0n7B1vUg_E7540P_A", "Content-Type": "application/json" },
      body: JSON.stringify(args),
    });
  } catch {
    throw new Error("network");
  }
  const text = await res.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch { /* not JSON */ }
  if (!res.ok) throw new Error(body?.message || "network");
  return body;
};
