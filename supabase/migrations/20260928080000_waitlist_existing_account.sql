-- /daftar with an email that already has a ScriptGo account: no waitlist row and no confirmation
-- link. join_waitlist answers the same as always (the page shows "Cek email kamu"), and the email
-- (template beta_has_account) says the account already has access and how to sign in.

-- Per-email limit for these emails (3 per hour), keyed by a hash so no address is stored.
create table private.account_notice_log (
  email_hash text not null,
  sent_at timestamptz not null default now()
);
create index account_notice_log_email_sent on private.account_notice_log (email_hash, sent_at);

/** Sends the beta_has_account template to [target_email]; returns the pg_net request id. */
create function private.send_account_notice(target_email text) returns bigint
language plpgsql security definer set search_path = '' as $$
declare
  api_key text;
  template record;
begin
  select decrypted_secret into api_key from vault.decrypted_secrets where name = 'resend_api_key';
  if api_key is null then
    raise exception 'Vault secret resend_api_key is missing; add it first (docs/beta-launch.md)';
  end if;
  select subject, html into template from private.email_templates where key = 'beta_has_account';
  if not found then
    raise exception 'Email template beta_has_account is missing; run supabase/templates/beta/beta_templates.sql';
  end if;
  return net.http_post(
    url := 'https://api.resend.com/emails',
    headers := jsonb_build_object('Authorization', 'Bearer ' || api_key, 'Content-Type', 'application/json'),
    body := jsonb_build_object(
      'from', 'ScriptGo <halo@scriptgo.id>',
      'to', jsonb_build_array(target_email),
      'reply_to', 'halo@scriptgo.id',
      'subject', template.subject,
      'html', replace(template.html, '{{email}}', private.html_escape(target_email))
    )
  );
end;
$$;

create or replace function public.join_waitlist(
  p_email text, p_whatsapp text default '', p_device text default '',
  p_referred_by text default '', p_source text default '', p_ab text default ''
) returns void
language plpgsql security definer set search_path = '' as $$
declare
  v_email text := lower(trim(coalesce(p_email, '')));
  v_whatsapp text := coalesce(p_whatsapp, '');
  v_device text := coalesce(p_device, '');
  v_ref text := coalesce(p_referred_by, '');
  v_ab text := coalesce(p_ab, '');
  v_row public.waitlist;
  v_token text;
  v_now timestamptz := now();
begin
  if v_email !~* '^[^@\s]+@[^@\s]+\.[^@\s]+$' or length(v_email) > 254
     or (v_whatsapp <> '' and v_whatsapp !~ '^\+[1-9][0-9]{6,14}$')
     or v_device not in ('', 'android', 'iphone', 'laptop')
     or (v_ref <> '' and v_ref !~ '^[a-z0-9]{6,12}$')
     or v_ab not in ('', 'a', 'b') then
    raise exception 'invalid_input';
  end if;
  if (select count(*) from private.waitlist_send_log where sent_at > v_now - interval '1 minute') >= 30 then
    raise exception 'rate_limited';
  end if;

  -- Email already has a ScriptGo account: no waitlist row. The page shows the same "Cek email
  -- kamu" as for everyone else (so it cannot be used to find out who has an account); the email
  -- says the account already has access and how to sign in.
  if exists (select 1 from auth.users where lower(email) = v_email and deleted_at is null) then
    if (select count(*) from private.account_notice_log
        where email_hash = encode(extensions.digest(v_email, 'sha256'), 'hex')
          and sent_at > v_now - interval '1 hour') >= 3 then
      raise exception 'rate_limited';
    end if;
    insert into private.account_notice_log (email_hash) values (encode(extensions.digest(v_email, 'sha256'), 'hex'));
    insert into private.waitlist_send_log default values;
    perform private.send_account_notice(v_email);
    return;
  end if;

  select * into v_row from public.waitlist where lower(email) = v_email for update;
  if not found then
    insert into public.waitlist (email, whatsapp, device, wants_beta, referral_code, referred_by, source, ab_variant)
    values (v_email, v_whatsapp, v_device, v_device = 'android', private.new_referral_code(), v_ref,
            left(coalesce(p_source, ''), 200), v_ab)
    returning * into v_row;
  elsif v_row.email_verified_at is not null then
    return; -- already confirmed: same answer, no email
  else
    update public.waitlist
      set whatsapp = coalesce(nullif(v_whatsapp, ''), whatsapp),
          device = coalesce(nullif(v_device, ''), device),
          wants_beta = coalesce(nullif(v_device, ''), device) = 'android',
          ab_variant = coalesce(nullif(ab_variant, ''), v_ab)
      where id = v_row.id
      returning * into v_row;
  end if;

  if v_row.verify_window_start is null or v_row.verify_window_start < v_now - interval '1 hour' then
    v_row.verify_window_start := v_now;
    v_row.verify_send_count := 0;
  end if;
  if v_row.verify_send_count >= 3 then
    raise exception 'rate_limited';
  end if;

  v_token := encode(extensions.gen_random_bytes(32), 'hex');
  update public.waitlist set
    verify_token_hash = encode(extensions.digest(v_token, 'sha256'), 'hex'),
    verify_expires_at = v_now + interval '24 hours',
    verify_sent_at = v_now,
    verify_send_count = v_row.verify_send_count + 1,
    verify_window_start = v_row.verify_window_start
  where id = v_row.id;
  insert into private.waitlist_send_log default values;
  perform private.send_waitlist_confirm(v_email, 'https://scriptgo.id/konfirmasi?t=' || v_token);
end;
$$;

revoke all on function private.send_account_notice(text) from public, anon, authenticated;
revoke all on function public.join_waitlist(text, text, text, text, text, text) from public;
grant execute on function public.join_waitlist(text, text, text, text, text, text) to anon, authenticated;

-- Email template (generated by supabase/templates/build.mjs; keep in sync with beta/beta_templates.sql).
insert into private.email_templates (key, subject, html) values ('beta_has_account', $tpl$Email ini sudah punya akun ScriptGo$tpl$, $tpl$<!doctype html>
<html lang="id">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light">
<meta name="supported-color-schemes" content="light">
<link href="https://fonts.googleapis.com/css2?family=Urbanist:wght@500;700;800&display=swap" rel="stylesheet">
<title>Kamu sudah bisa pakai ScriptGo</title>
</head>
<body style="margin:0;padding:0;background:#EEF2F6;">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:#EEF2F6;">Tidak perlu daftar beta lagi. Cukup masuk dengan email ini.</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#EEF2F6;font-family:Urbanist, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
  <tr><td align="center" style="padding:32px 16px;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:520px;">
      <tr><td style="padding:0 8px 20px;">
        <table role="presentation" cellpadding="0" cellspacing="0"><tr>
          <td style="vertical-align:middle;"><img src="https://scriptgo.id/icon.png?v=a87be887" width="36" height="36" alt="" style="display:block;border:0;"></td>
          <td style="vertical-align:middle;padding-left:10px;font-size:20px;font-weight:800;letter-spacing:-0.3px;color:#0E1116;">ScriptGo</td>
        </tr></table>
      </td></tr>
      <tr><td style="background:#FFFFFF;border-radius:28px;padding:0;overflow:hidden;">
        <div style="padding:32px 28px;">
        <h1 style="margin:0 0 12px;font-size:26px;line-height:1.2;font-weight:800;letter-spacing:-0.5px;color:#0E1116;">Kamu sudah bisa pakai ScriptGo</h1>
        <p style="margin:0 0 16px;font-size:16px;line-height:1.6;color:#5B6675;">Hai! Ada yang mendaftar beta ScriptGo dengan email <strong style="color:#0E1116;font-weight:700;">{{email}}</strong>. Email ini sudah punya akun ScriptGo, jadi kamu <strong style="color:#0E1116;font-weight:700;">tidak perlu daftar lagi</strong>.</p>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:0 0 20px;">
  <tr>
    <td style="width:34px;vertical-align:top;padding:2px 0 12px;"><div style="width:26px;height:26px;border-radius:13px;background:#E0F2FE;color:#0369A1;font-size:14px;font-weight:800;line-height:26px;text-align:center;">1</div></td>
    <td style="vertical-align:top;padding:4px 0 12px;font-size:16px;line-height:1.5;color:#5B6675;">Buka app ScriptGo di HP Android kamu.</td>
  </tr>
  <tr>
    <td style="width:34px;vertical-align:top;padding:2px 0 12px;"><div style="width:26px;height:26px;border-radius:13px;background:#E0F2FE;color:#0369A1;font-size:14px;font-weight:800;line-height:26px;text-align:center;">2</div></td>
    <td style="vertical-align:top;padding:4px 0 12px;font-size:16px;line-height:1.5;color:#5B6675;">Buka tab <strong style="color:#0E1116;font-weight:700;">Akun</strong>, lalu masuk dengan email ini. Template, kontak, dan pengaturanmu ikut kembali.</td>
  </tr>
  <tr>
    <td style="width:34px;vertical-align:top;padding:2px 0 12px;"><div style="width:26px;height:26px;border-radius:13px;background:#E0F2FE;color:#0369A1;font-size:14px;font-weight:800;line-height:26px;text-align:center;">3</div></td>
    <td style="vertical-align:top;padding:4px 0 12px;font-size:16px;line-height:1.5;color:#5B6675;">Di laptop, masuk dengan email yang sama di Chrome extension ScriptGo.</td>
  </tr>
</table>
<table role="presentation" cellpadding="0" cellspacing="0" style="margin:4px 0 20px;">
  <tr><td style="background:#0E1116;border-radius:999px;">
    <a href="https://play.google.com/store/apps/details?id=id.scriptgo.keyboard" style="display:inline-block;padding:15px 28px;font-size:16px;font-weight:700;color:#FFFFFF;text-decoration:none;font-family:Urbanist, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">Buka ScriptGo di Google Play</a>
  </td></tr>
</table>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:8px 0 0;">
  <tr><td style="background:#F5F7FA;border-radius:16px;padding:14px 16px;font-size:14px;line-height:1.55;color:#5B6675;">Tidak merasa mendaftar? Abaikan email ini; akunmu tetap aman dan tidak ada yang berubah.</td></tr>
</table>
        </div>
      </td></tr>
      <tr><td style="padding:20px 8px 0;font-size:13px;line-height:1.6;color:#5B6675;">
        Email otomatis dari ScriptGo untuk {{email}}. Kamu menerima email ini karena mendaftar beta di scriptgo.id.<br>
        <a href="https://scriptgo.id" style="color:#0369A1;font-weight:600;text-decoration:none;">scriptgo.id</a> ·
        <a href="https://scriptgo.id/privacy.html" style="color:#5B6675;text-decoration:underline;">Kebijakan privasi</a>
      </td></tr>
    </table>
  </td></tr>
</table>
</body>
</html>
$tpl$)
on conflict (key) do update set subject = excluded.subject, html = excluded.html, updated_at = now();
