-- Pro status and the AI template quota.
-- entitlements: one row per account that is (or was) Pro. No row = free. Only the owner (SQL
-- editor) or, later, the payment flow writes it; users can read their own row. During the beta the
-- owner grants Pro with: insert into entitlements (user_id, plan, pro_until, note) values (…).
-- AI quota (PRD §9): Pro 20 per day (reset at midnight WIB), free and trial 5 in total. The Edge
-- Function ai-template consumes one before calling the model and refunds it when the call fails.

create table public.entitlements (
  user_id uuid primary key references auth.users (id) on delete cascade,
  plan text not null default 'free' check (plan in ('free', 'pro')),
  pro_until timestamptz,
  note text not null default '' check (length(note) <= 200),
  updated_at timestamptz not null default now()
);

alter table public.entitlements enable row level security;
create policy "Owners read their plan" on public.entitlements
  for select to authenticated using ((select auth.uid()) = user_id);
revoke all on public.entitlements from anon, authenticated;
grant select on public.entitlements to authenticated;

create table private.ai_usage (
  user_id uuid primary key references auth.users (id) on delete cascade,
  day date not null,
  day_count int not null default 0 check (day_count >= 0),
  free_count int not null default 0 check (free_count >= 0)
);

/** Whether [target] is Pro right now. */
create function private.is_pro(target uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.entitlements
    where user_id = target and plan = 'pro' and (pro_until is null or pro_until > now())
  )
$$;

/** The quota of [target] as JSON: plan, limit, used, remaining, and period ('day' or 'total'). */
create function private.ai_quota_of(target uuid) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
  pro boolean := private.is_pro(target);
  today date := (now() at time zone 'Asia/Jakarta')::date;
  usage private.ai_usage;
  used int;
  lim int;
begin
  select * into usage from private.ai_usage where user_id = target;
  if pro then
    lim := 20;
    used := case when usage.day = today then usage.day_count else 0 end;
  else
    lim := 5;
    used := coalesce(usage.free_count, 0);
  end if;
  return jsonb_build_object(
    'plan', case when pro then 'pro' else 'free' end,
    'limit', lim, 'used', used, 'remaining', greatest(lim - used, 0),
    'period', case when pro then 'day' else 'total' end
  );
end;
$$;

/** The signed-in user's AI quota, for the app to show "Sisa 18 dari 20 hari ini". */
create function public.ai_quota() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
begin
  if auth.uid() is null then
    raise exception 'not_signed_in' using errcode = '42501';
  end if;
  return private.ai_quota_of(auth.uid());
end;
$$;

/**
 * Takes one AI use from [target]'s quota and returns the quota after it; raises 'quota_exceeded'
 * when none is left. Called only by the ai-template Edge Function (service role), with the user
 * id from the verified JWT. Row lock makes parallel requests count correctly.
 */
create function public.ai_consume(target uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  pro boolean := private.is_pro(target);
  today date := (now() at time zone 'Asia/Jakarta')::date;
  usage private.ai_usage;
begin
  insert into private.ai_usage (user_id, day) values (target, today) on conflict (user_id) do nothing;
  select * into usage from private.ai_usage where user_id = target for update;
  if pro then
    if usage.day <> today then
      usage.day := today;
      usage.day_count := 0;
    end if;
    if usage.day_count >= 20 then
      raise exception 'quota_exceeded';
    end if;
    update private.ai_usage set day = today, day_count = usage.day_count + 1 where user_id = target;
  else
    if usage.free_count >= 5 then
      raise exception 'quota_exceeded';
    end if;
    update private.ai_usage set free_count = usage.free_count + 1 where user_id = target;
  end if;
  return private.ai_quota_of(target);
end;
$$;

/** Gives back the use taken by ai_consume when the model call failed. */
create function public.ai_refund(target uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
begin
  if private.is_pro(target) then
    update private.ai_usage set day_count = greatest(day_count - 1, 0)
      where user_id = target and day = (now() at time zone 'Asia/Jakarta')::date;
  else
    update private.ai_usage set free_count = greatest(free_count - 1, 0) where user_id = target;
  end if;
  return private.ai_quota_of(target);
end;
$$;

revoke all on function private.is_pro(uuid) from public, anon, authenticated;
revoke all on function private.ai_quota_of(uuid) from public, anon, authenticated;
revoke all on function public.ai_quota() from public, anon;
grant execute on function public.ai_quota() to authenticated;
revoke all on function public.ai_consume(uuid) from public, anon, authenticated;
revoke all on function public.ai_refund(uuid) from public, anon, authenticated;
grant execute on function public.ai_consume(uuid) to service_role;
grant execute on function public.ai_refund(uuid) to service_role;
