-- Beta sign-up with email confirmation. Visitors call join_waitlist (no direct inserts); it stores
-- a SHA-256 hash of a one-time token and emails a link to scriptgo.id/konfirmasi. confirm_waitlist
-- marks the email verified. Invites (beta_status = invited) are refused for unverified emails.
alter table public.waitlist
  add column email_verified_at timestamptz,
  add column verify_token_hash text,
  add column verify_expires_at timestamptz,
  add column verify_sent_at timestamptz,
  add column verify_send_count int not null default 0,
  add column verify_window_start timestamptz;

create unique index waitlist_verify_token_hash_key on public.waitlist (verify_token_hash)
  where verify_token_hash is not null;

drop policy "Anyone can join the waitlist" on public.waitlist;
revoke all on public.waitlist from anon, authenticated;

-- Global rate limit for confirmation emails.
create table private.waitlist_send_log (sent_at timestamptz not null default now());
create index waitlist_send_log_sent_at on private.waitlist_send_log (sent_at);

create function private.new_referral_code() returns text
language sql volatile set search_path = '' as $$
  select string_agg(substr('abcdefghjkmnpqrstuvwxyz23456789', 1 + (get_byte(b, i) % 31), 1), '' order by i)
  from (select extensions.gen_random_bytes(8) as b) r, generate_series(0, 7) i
$$;

/** Sends the beta_confirm template to [target_email] with [link]; returns the pg_net request id. */
create function private.send_waitlist_confirm(target_email text, link text) returns bigint
language plpgsql security definer set search_path = '' as $$
declare
  api_key text;
  template record;
begin
  select decrypted_secret into api_key from vault.decrypted_secrets where name = 'resend_api_key';
  if api_key is null then
    raise exception 'Vault secret resend_api_key is missing; add it first (docs/beta-launch.md)';
  end if;
  select subject, html into template from private.email_templates where key = 'beta_confirm';
  if not found then
    raise exception 'Email template beta_confirm is missing; run supabase/templates/beta/beta_templates.sql';
  end if;
  return net.http_post(
    url := 'https://api.resend.com/emails',
    headers := jsonb_build_object('Authorization', 'Bearer ' || api_key, 'Content-Type', 'application/json'),
    body := jsonb_build_object(
      'from', 'ScriptGo <halo@scriptgo.id>',
      'to', jsonb_build_array(target_email),
      'reply_to', 'halo@scriptgo.id',
      'subject', template.subject,
      'html', replace(replace(template.html, '{{link}}', private.html_escape(link)), '{{email}}', private.html_escape(target_email))
    )
  );
end;
$$;

create function public.join_waitlist(
  p_email text, p_whatsapp text default '', p_device text default '',
  p_referred_by text default '', p_source text default ''
) returns void
language plpgsql security definer set search_path = '' as $$
declare
  v_email text := lower(trim(coalesce(p_email, '')));
  v_whatsapp text := coalesce(p_whatsapp, '');
  v_device text := coalesce(p_device, '');
  v_ref text := coalesce(p_referred_by, '');
  v_row public.waitlist;
  v_token text;
  v_now timestamptz := now();
begin
  if v_email !~* '^[^@\s]+@[^@\s]+\.[^@\s]+$' or length(v_email) > 254
     or (v_whatsapp <> '' and v_whatsapp !~ '^\+[1-9][0-9]{6,14}$')
     or v_device not in ('', 'android', 'iphone', 'laptop')
     or (v_ref <> '' and v_ref !~ '^[a-z0-9]{6,12}$') then
    raise exception 'invalid_input';
  end if;
  if (select count(*) from private.waitlist_send_log where sent_at > v_now - interval '1 minute') >= 30 then
    raise exception 'rate_limited';
  end if;

  select * into v_row from public.waitlist where lower(email) = v_email for update;
  if not found then
    insert into public.waitlist (email, whatsapp, device, wants_beta, referral_code, referred_by, source)
    values (v_email, v_whatsapp, v_device, v_device = 'android', private.new_referral_code(), v_ref,
            left(coalesce(p_source, ''), 200))
    returning * into v_row;
  elsif v_row.email_verified_at is not null then
    return; -- already confirmed: same answer, no email
  else
    update public.waitlist
      set whatsapp = coalesce(nullif(v_whatsapp, ''), whatsapp),
          device = coalesce(nullif(v_device, ''), device),
          wants_beta = coalesce(nullif(v_device, ''), device) = 'android'
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

create function public.confirm_waitlist(p_token text) returns text
language plpgsql security definer set search_path = '' as $$
declare
  v_code text;
begin
  if coalesce(p_token, '') !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid_token';
  end if;
  update public.waitlist
    set email_verified_at = now(), verify_token_hash = null, verify_expires_at = null
    where verify_token_hash = encode(extensions.digest(p_token, 'sha256'), 'hex')
      and verify_expires_at > now()
    returning referral_code into v_code;
  if v_code is null then
    raise exception 'invalid_token';
  end if;
  return v_code;
end;
$$;

-- Invites only for confirmed emails.
create or replace function private.waitlist_beta_status() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.beta_status is not distinct from old.beta_status then
    return new;
  end if;
  if new.beta_status = 'invited' and new.email_verified_at is null then
    raise exception 'Email % belum dikonfirmasi; undangan beta hanya untuk email terverifikasi', new.email;
  end if;
  if new.beta_status in ('approved', 'invited') and new.approved_at is null then
    new.approved_at := now();
  end if;
  if new.beta_status = 'invited' then
    new.invite_request_id := private.send_waitlist_email(new, 'beta_invite');
    new.invited_at := now();
  end if;
  return new;
end;
$$;

revoke all on function private.new_referral_code() from public, anon, authenticated;
revoke all on function private.send_waitlist_confirm(text, text) from public, anon, authenticated;
revoke all on function public.join_waitlist(text, text, text, text, text) from public;
revoke all on function public.confirm_waitlist(text) from public;
grant execute on function public.join_waitlist(text, text, text, text, text) to anon, authenticated;
grant execute on function public.confirm_waitlist(text) to anon, authenticated;
