// Applies ScriptGo's Supabase Auth settings through the Management API: site URL, redirect URLs,
// email templates and subjects (built by supabase/templates/build.mjs), security notifications, and
// optionally Resend SMTP.
//
//   SUPABASE_ACCESS_TOKEN=sbp_... node scripts/apply-supabase-auth.mjs            # dry run
//   SUPABASE_ACCESS_TOKEN=sbp_... node scripts/apply-supabase-auth.mjs --apply
//   Add RESEND_API_KEY=re_... to also switch sending to Resend (noreply@scriptgo.id).
//
// The access token comes from https://supabase.com/dashboard/account/tokens; revoke it afterwards.
import { readFileSync } from "node:fs";

const PROJECT_REF = "hsanqncbfurjhnshdpnc";
const SITE_URL = "https://scriptgo.id";
// App deep link, the site, Google sign-in in the extension (chrome.identity), and the extension's email-link page.
const REDIRECT_URLS = ["scriptgo://login-callback", "https://scriptgo.id/**", "https://*.chromiumapp.org/**", "chrome-extension://*/auth.html"];
const NOTIFICATIONS = [
  "password_changed", "email_changed", "phone_changed", "identity_linked", "identity_unlinked",
  "mfa_factor_enrolled", "mfa_factor_unenrolled",
];

const dir = new URL("../supabase/templates/", import.meta.url);
const subjects = JSON.parse(readFileSync(new URL("subjects.json", dir), "utf8"));

const body = { site_url: SITE_URL, uri_allow_list: REDIRECT_URLS.join(",") };
for (const [key, subject] of Object.entries(subjects)) {
  body[`mailer_subjects_${key}`] = subject;
  body[`mailer_templates_${key}_content`] = readFileSync(new URL(`${key}.html`, dir), "utf8");
}
// Security notifications for changes this app can make (email, linked Google account).
for (const n of NOTIFICATIONS) body[`mailer_notifications_${n}_enabled`] = ["email_changed", "identity_linked", "identity_unlinked"].includes(n);

if (process.env.RESEND_API_KEY) {
  Object.assign(body, {
    smtp_host: "smtp.resend.com",
    smtp_port: "465",
    smtp_user: "resend",
    smtp_pass: process.env.RESEND_API_KEY,
    smtp_admin_email: "noreply@scriptgo.id",
    smtp_sender_name: "ScriptGo",
    rate_limit_email_sent: 100,
  });
}

const token = process.env.SUPABASE_ACCESS_TOKEN;
if (!token) {
  console.error("Set SUPABASE_ACCESS_TOKEN (https://supabase.com/dashboard/account/tokens).");
  process.exit(1);
}
const summary = Object.fromEntries(Object.entries(body).map(([k, v]) => [k, k.includes("content") ? `${v.length} chars` : k === "smtp_pass" ? "***" : v]));
console.log(summary);
if (!process.argv.includes("--apply")) {
  console.log("Dry run. Add --apply to send.");
  process.exit(0);
}

const res = await fetch(`https://api.supabase.com/v1/projects/${PROJECT_REF}/config/auth`, {
  method: "PATCH",
  headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
  body: JSON.stringify(body),
});
console.log(res.status, res.ok ? "applied" : await res.text());
if (!res.ok) process.exit(1);
