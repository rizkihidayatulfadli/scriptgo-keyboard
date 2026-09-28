-- The account's plan (PRD §9), for the app and extension to show: beta, trial, pro, or free.
-- Only shown for now; the free limits (20 templates, 30 uses, no sync or extension) come later.
--
-- The switch: private.app_config 'limits_from' (a timestamptz as JSON, e.g. '"2026-11-01T00:00:00+07:00"').
-- Unset (the default, during the closed beta) → every account is 'beta' with all Pro features.
-- Set at release → the 14-day trial runs from the later of sign-up and that date, then 'free'.
-- Turn it on with:
--   insert into private.app_config (key, value) values ('limits_from', to_jsonb(now()))
--     on conflict (key) do update set value = excluded.value;
-- Pro (entitlements, granted by hand or later by the payment flow) always wins.

create table private.app_config (
  key text primary key,
  value jsonb not null
);

/** [target]'s plan as JSON: plan, trial_ends_at (trial only), pro_until (pro; null = no end). */
create function private.plan_of(target uuid) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
  ent public.entitlements;
  limits_from timestamptz;
  created timestamptz;
  trial_end timestamptz;
begin
  select * into ent from public.entitlements
    where user_id = target and plan = 'pro' and (pro_until is null or pro_until > now());
  if found then
    return jsonb_build_object('plan', 'pro', 'pro_until', ent.pro_until);
  end if;
  select (value #>> '{}')::timestamptz into limits_from from private.app_config where key = 'limits_from';
  if limits_from is null then
    return jsonb_build_object('plan', 'beta');
  end if;
  select created_at into created from auth.users where id = target;
  trial_end := greatest(coalesce(created, now()), limits_from) + interval '14 days';
  if now() < trial_end then
    return jsonb_build_object('plan', 'trial', 'trial_ends_at', trial_end);
  end if;
  return jsonb_build_object('plan', 'free');
end;
$$;

/** The signed-in user's plan, for "Trial Pro, berakhir 3 hari lagi" in Akun. */
create function public.my_plan() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
begin
  if auth.uid() is null then
    raise exception 'not_signed_in' using errcode = '42501';
  end if;
  return private.plan_of(auth.uid());
end;
$$;

revoke all on function private.plan_of(uuid) from public, anon, authenticated;
revoke all on function public.my_plan() from public, anon;
grant execute on function public.my_plan() to authenticated;
