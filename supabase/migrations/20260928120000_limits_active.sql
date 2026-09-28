-- Whether the free limits are switched on (private.app_config 'limits_from' is set and reached),
-- for apps that are not signed in: once on, a phone without an account gets the free limits and
-- is asked to sign in to start the trial. Signed-in apps read my_plan() instead.

create function public.limits_active() returns boolean
language sql stable security definer set search_path = '' as $$
  select coalesce((select (value #>> '{}')::timestamptz <= now() from private.app_config where key = 'limits_from'), false)
$$;

revoke all on function public.limits_active() from public;
grant execute on function public.limits_active() to anon, authenticated;

-- A switch date still in the future also means beta, as limits_active() says (was: any set date).
create or replace function private.plan_of(target uuid) returns jsonb
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
  if limits_from is null or limits_from > now() then
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
revoke all on function private.plan_of(uuid) from public, anon, authenticated;
