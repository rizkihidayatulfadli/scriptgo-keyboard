-- /daftar: "Perangkat kamu" is multiple choice. waitlist.device holds any combination of android,
-- iphone and laptop, comma-separated in that order ('' = not given). wants_beta stays "has Android".
-- Count per device: select d, count(*) from waitlist, unnest(string_to_array(device, ',')) d group by 1;
alter table public.waitlist drop constraint waitlist_device_check;
alter table public.waitlist add constraint waitlist_device_check
  check (device in ('', 'android', 'iphone', 'laptop', 'android,iphone', 'android,laptop', 'iphone,laptop', 'android,iphone,laptop'));

create or replace function public.join_waitlist(
  p_email text, p_whatsapp text default '', p_device text default '',
  p_referred_by text default '', p_source text default '', p_ab text default ''
) returns void
language plpgsql security definer set search_path = '' as $$
declare
  v_email text := lower(trim(coalesce(p_email, '')));
  v_whatsapp text := coalesce(p_whatsapp, '');
  -- "android,iphone,laptop": any of the three, stored de-duplicated in this order.
  v_parts text[] := string_to_array(replace(coalesce(p_device, ''), ' ', ''), ',');
  v_device text := array_to_string(array(
    select d from unnest(array['android', 'iphone', 'laptop']) d where d = any (v_parts)
  ), ',');
  v_ref text := coalesce(p_referred_by, '');
  v_ab text := coalesce(p_ab, '');
  v_row public.waitlist;
  v_token text;
  v_now timestamptz := now();
begin
  if v_email !~* '^[^@\s]+@[^@\s]+\.[^@\s]+$' or length(v_email) > 254
     or (v_whatsapp <> '' and v_whatsapp !~ '^\+[1-9][0-9]{6,14}$')
     or exists (select 1 from unnest(v_parts) d where d not in ('', 'android', 'iphone', 'laptop'))
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
    values (v_email, v_whatsapp, v_device, 'android' = any (string_to_array(v_device, ',')), private.new_referral_code(), v_ref,
            left(coalesce(p_source, ''), 200), v_ab)
    returning * into v_row;
  elsif v_row.email_verified_at is not null then
    return; -- already confirmed: same answer, no email
  else
    update public.waitlist
      set whatsapp = coalesce(nullif(v_whatsapp, ''), whatsapp),
          device = coalesce(nullif(v_device, ''), device),
          wants_beta = 'android' = any (string_to_array(coalesce(nullif(v_device, ''), device), ',')),
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

revoke all on function public.join_waitlist(text, text, text, text, text, text) from public;
grant execute on function public.join_waitlist(text, text, text, text, text, text) to anon, authenticated;
