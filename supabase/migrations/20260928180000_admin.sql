-- Owner dashboard (admin/, served only inside the owner's tailnet). The page signs in with an email
-- code like /bayar and calls these RPCs with the user's token; each one checks private.admins
-- first, so the Tailscale limit is a second layer, not the only one. Every write is logged.

create table private.admins (
    user_id uuid primary key references auth.users (id) on delete cascade,
    added_at timestamptz not null default now()
);
-- The owner's two accounts.
insert into private.admins (user_id)
    select id from auth.users where lower(email) in ('owner@scriptgo.id', 'owner@scriptgo.id')
    on conflict do nothing;

create table private.admin_log (
    id bigint generated always as identity primary key,
    admin_id uuid not null,
    action text not null,
    target text not null default '',
    detail jsonb not null default '{}',
    created_at timestamptz not null default now()
);

create function private.is_admin() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from private.admins where user_id = auth.uid())
$$;

/** Raises 42501 unless the caller is an admin. */
create function private.require_admin() returns void
language plpgsql stable security definer set search_path = '' as $$
begin
  if not private.is_admin() then
    raise exception 'not_admin' using errcode = '42501';
  end if;
end;
$$;

create function private.admin_audit(action text, target text, detail jsonb) returns void
language sql security definer set search_path = '' as $$
  insert into private.admin_log (admin_id, action, target, detail) values (auth.uid(), action, target, detail)
$$;

/** Whether the caller may open the dashboard (the page asks before showing anything). */
create function public.admin_check() returns boolean
language sql stable security definer set search_path = '' as $$
  select private.is_admin()
$$;

/** Headline numbers for the dashboard's first screen. */
create function public.admin_overview() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
  today date := (now() at time zone 'Asia/Jakarta')::date;
begin
  perform private.require_admin();
  return jsonb_build_object(
    'users', (select count(*) from auth.users),
    'users_7d', (select count(*) from auth.users where created_at > now() - interval '7 days'),
    'waitlist', (select jsonb_object_agg(beta_status, n) from (select beta_status, count(*) n from public.waitlist group by 1) s),
    'waitlist_verified', (select count(*) from public.waitlist where email_verified_at is not null),
    'pro_active', (select count(*) from public.entitlements where plan = 'pro' and (pro_until is null or pro_until > now())),
    'revenue_today', (select coalesce(sum(amount), 0) from public.orders where status = 'paid' and (paid_at at time zone 'Asia/Jakarta')::date = today),
    'revenue_month', (select coalesce(sum(amount), 0) from public.orders where status = 'paid' and date_trunc('month', paid_at at time zone 'Asia/Jakarta') = date_trunc('month', now() at time zone 'Asia/Jakarta')),
    'orders_pending', (select count(*) from public.orders where status = 'pending' and created_at > now() - interval '1 day'),
    'ai_today', (select coalesce(sum(day_count), 0) from private.ai_usage where day = today),
    'reports_new', (select count(*) from public.ai_reports where status = 'new'),
    'support_new', (select count(*) from public.support_messages where status = 'new'),
    'limits_from', (select value #>> '{}' from private.app_config where key = 'limits_from'),
    'limits_active', public.limits_active()
  );
end;
$$;

/** Waitlist rows, newest first; [status] '' for all, [search] matches email, name, or WhatsApp. */
create function public.admin_waitlist(status text default '', search text default '', lim int default 100, off int default 0) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
#variable_conflict use_column
begin
  perform private.require_admin();
  return coalesce((
    select jsonb_agg(to_jsonb(r) order by r.created_at desc) from (
      select id, email, name, whatsapp, business_type, role, device, wants_beta, referral_code, referred_by,
             (select count(*) from public.waitlist x where x.referred_by = w.referral_code and w.referral_code <> '') as referrals,
             source, beta_status, email_verified_at, approved_at, invited_at, created_at
      from public.waitlist w
      where (admin_waitlist.status = '' or w.beta_status = admin_waitlist.status)
        and (admin_waitlist.search = '' or w.email ilike '%' || admin_waitlist.search || '%' or w.name ilike '%' || admin_waitlist.search || '%' or w.whatsapp like '%' || admin_waitlist.search || '%')
      order by w.created_at desc
      limit least(greatest(lim, 1), 500) offset greatest(off, 0)
    ) r
  ), '[]'::jsonb);
end;
$$;

/**
 * Moves a waitlist row to [new_status]. 'invited' sends the invitation email (the existing trigger),
 * and only a verified email may be approved or invited, as in docs/beta-launch.md.
 */
create function public.admin_set_beta_status(waitlist_id uuid, new_status text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  w public.waitlist;
begin
  perform private.require_admin();
  if new_status not in ('pending', 'approved', 'invited', 'rejected') then
    raise exception 'bad_status';
  end if;
  select * into w from public.waitlist where id = waitlist_id for update;
  if not found then
    raise exception 'unknown_waitlist';
  end if;
  if new_status in ('approved', 'invited') and w.email_verified_at is null then
    raise exception 'email_not_verified';
  end if;
  update public.waitlist set beta_status = new_status where id = waitlist_id;
  perform private.admin_audit('beta_status', w.email, jsonb_build_object('from', w.beta_status, 'to', new_status));
  return jsonb_build_object('id', waitlist_id, 'beta_status', new_status);
end;
$$;

/** Accounts with their plan, newest first; [search] matches the email. */
create function public.admin_users(search text default '', lim int default 100, off int default 0) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
#variable_conflict use_column
begin
  perform private.require_admin();
  return coalesce((
    select jsonb_agg(to_jsonb(r) order by r.created_at desc) from (
      select u.id, u.email, u.created_at, u.last_sign_in_at,
             private.plan_of(u.id) as plan,
             e.pro_until, e.note,
             bp.business_name, bp.city,
             (select count(*) from public.templates t where t.user_id = u.id and not t.deleted) as templates,
             (select count(*) from public.contacts c where c.user_id = u.id and not c.deleted) as contacts,
             exists (select 1 from private.admins a where a.user_id = u.id) as admin
      from auth.users u
      left join public.entitlements e on e.user_id = u.id
      left join public.business_profiles bp on bp.user_id = u.id
      where admin_users.search = '' or u.email ilike '%' || admin_users.search || '%'
      order by u.created_at desc
      limit least(greatest(lim, 1), 500) offset greatest(off, 0)
    ) r
  ), '[]'::jsonb);
end;
$$;

/**
 * Gives [target] Pro for [months] more (added to what is left, like a payment), or Pro without an
 * end date when [months] is null. [reason] is kept in entitlements.note.
 */
create function public.admin_grant_pro(target uuid, months int, reason text default '') returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  current_end timestamptz;
  has_row boolean;
  new_end timestamptz;
begin
  perform private.require_admin();
  if months is not null and (months < 1 or months > 36) then
    raise exception 'bad_months';
  end if;
  if not exists (select 1 from auth.users where id = target) then
    raise exception 'unknown_user';
  end if;
  select true, pro_until into has_row, current_end from public.entitlements
    where user_id = target and plan = 'pro' and (pro_until is null or pro_until > now());
  new_end := case
    when months is null then null
    when has_row and current_end is null then null
    else greatest(coalesce(current_end, now()), now()) + make_interval(months => months)
  end;
  insert into public.entitlements (user_id, plan, pro_until, note, updated_at)
    values (target, 'pro', new_end, left(coalesce(nullif(trim(reason), ''), 'admin'), 200), now())
    on conflict (user_id) do update set plan = 'pro', pro_until = excluded.pro_until, note = excluded.note, updated_at = now();
  perform private.admin_audit('grant_pro', target::text, jsonb_build_object('months', months, 'pro_until', new_end, 'reason', reason));
  return jsonb_build_object('pro_until', new_end);
end;
$$;

/** Ends [target]'s Pro now (they fall back to trial, beta, or free as private.plan_of decides). */
create function public.admin_revoke_pro(target uuid, reason text default '') returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform private.require_admin();
  update public.entitlements set plan = 'free', pro_until = null, note = left(coalesce(nullif(trim(reason), ''), 'admin revoke'), 200), updated_at = now()
    where user_id = target;
  perform private.admin_audit('revoke_pro', target::text, jsonb_build_object('reason', reason));
end;
$$;

/**
 * The limits switch (PRD §9): free limits, trials, and the founder window start at [starts]; null
 * removes it (beta: everyone keeps Pro features).
 */
create function public.admin_set_limits_from(starts timestamptz) returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform private.require_admin();
  if starts is null then
    delete from private.app_config where key = 'limits_from';
  else
    insert into private.app_config (key, value) values ('limits_from', to_jsonb(starts))
      on conflict (key) do update set value = excluded.value;
  end if;
  perform private.admin_audit('limits_from', '', jsonb_build_object('starts', starts));
end;
$$;

/** Orders newest first ([status] '' for all) and paid revenue per day for the last [days] days. */
create function public.admin_orders(status text default '', lim int default 100, days int default 30) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
#variable_conflict use_column
begin
  perform private.require_admin();
  return jsonb_build_object(
    'orders', coalesce((
      select jsonb_agg(to_jsonb(r) order by r.created_at desc) from (
        select o.id, u.email, o.product, o.months, o.amount, o.founder, o.status, o.reference, o.created_at, o.paid_at
        from public.orders o left join auth.users u on u.id = o.user_id
        where admin_orders.status = '' or o.status = admin_orders.status
        order by o.created_at desc
        limit least(greatest(lim, 1), 500)
      ) r
    ), '[]'::jsonb),
    'daily', coalesce((
      select jsonb_agg(jsonb_build_object('day', d.day::date, 'revenue', coalesce(s.revenue, 0), 'orders', coalesce(s.orders, 0)) order by d.day)
      from generate_series((now() at time zone 'Asia/Jakarta')::date - (least(greatest(days, 1), 366) - 1), (now() at time zone 'Asia/Jakarta')::date, interval '1 day') as d(day)
      left join (
        select (paid_at at time zone 'Asia/Jakarta')::date as day, sum(amount) as revenue, count(*) as orders
        from public.orders where status = 'paid' group by 1
      ) s on s.day = d.day::date
    ), '[]'::jsonb)
  );
end;
$$;

/** AI reports and support messages ([status] '' for all), newest first, and AI calls per day. */
create function public.admin_inbox(status text default 'new', lim int default 100) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
#variable_conflict use_column
begin
  perform private.require_admin();
  return jsonb_build_object(
    'reports', coalesce((
      select jsonb_agg(to_jsonb(r) order by r.created_at desc) from (
        select a.id, u.email, a.mode, a.style, a.input, a.result, a.reason, a.note, a.source, a.status, a.created_at
        from public.ai_reports a left join auth.users u on u.id = a.user_id
        where admin_inbox.status = '' or a.status = admin_inbox.status
        order by a.created_at desc limit least(greatest(lim, 1), 500)
      ) r
    ), '[]'::jsonb),
    'support', coalesce((
      select jsonb_agg(to_jsonb(r) order by r.created_at desc) from (
        select m.id, u.email, m.kind, m.message, m.reply_email, m.source, m.app_version, m.status, m.created_at
        from public.support_messages m left join auth.users u on u.id = m.user_id
        where admin_inbox.status = '' or m.status = admin_inbox.status
        order by m.created_at desc limit least(greatest(lim, 1), 500)
      ) r
    ), '[]'::jsonb),
    'ai_daily', coalesce((
      select jsonb_agg(jsonb_build_object('day', day, 'calls', calls, 'users', users) order by day)
      from (select day, sum(day_count) calls, count(*) users from private.ai_usage where day > current_date - 30 group by day) s
    ), '[]'::jsonb)
  );
end;
$$;

/** Marks an AI report or support message read or done. */
create function public.admin_set_inbox_status(kind text, item uuid, new_status text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform private.require_admin();
  if new_status not in ('new', 'read', 'done') then
    raise exception 'bad_status';
  end if;
  if kind = 'report' then
    update public.ai_reports set status = new_status where id = item;
  elsif kind = 'support' then
    update public.support_messages set status = new_status where id = item;
  else
    raise exception 'bad_kind';
  end if;
  perform private.admin_audit('inbox_status', kind || ':' || item, jsonb_build_object('status', new_status));
end;
$$;

/** The hero headline A/B test report (private.ab_report) as JSON. */
create function public.admin_ab_report(experiment text default 'hero-1') returns jsonb
language plpgsql stable security definer set search_path = '' as $$
begin
  perform private.require_admin();
  return coalesce((select jsonb_agg(to_jsonb(r)) from private.ab_report(experiment) r), '[]'::jsonb);
end;
$$;

revoke all on function private.is_admin() from public, anon, authenticated;
revoke all on function private.require_admin() from public, anon, authenticated;
revoke all on function private.admin_audit(text, text, jsonb) from public, anon, authenticated;
do $$
declare f text;
begin
  foreach f in array array[
    'public.admin_check()', 'public.admin_overview()', 'public.admin_waitlist(text, text, int, int)',
    'public.admin_set_beta_status(uuid, text)', 'public.admin_users(text, int, int)', 'public.admin_grant_pro(uuid, int, text)',
    'public.admin_revoke_pro(uuid, text)', 'public.admin_set_limits_from(timestamptz)', 'public.admin_orders(text, int, int)',
    'public.admin_inbox(text, int)', 'public.admin_set_inbox_status(text, uuid, text)', 'public.admin_ab_report(text)'
  ] loop
    execute format('revoke all on function %s from public, anon', f);
    execute format('grant execute on function %s to authenticated', f);
  end loop;
end $$;
