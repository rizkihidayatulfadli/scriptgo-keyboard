-- A/B test of the landing page headline (spec: docs/superpowers/specs/2026-09-28-hero-ab-test-design.md).
-- The site gives each visitor a random id and a variant (a or b) kept in localStorage, records one
-- "view" and at most one "cta" (sign-up button click) per visitor, and passes the variant to
-- join_waitlist so sign-ups and confirmed emails can be counted per variant. No IP address, no
-- cookie, nothing that identifies the visitor. Read the result with: select * from private.ab_report('hero-1');

create table private.ab_events (
  experiment text not null check (experiment ~ '^[a-z0-9-]{1,40}$'),
  visitor uuid not null,
  event text not null check (event in ('view', 'cta')),
  variant text not null check (variant in ('a', 'b')),
  created_at timestamptz not null default now(),
  primary key (experiment, visitor, event)
);
create index ab_events_created_at on private.ab_events (created_at);

alter table public.waitlist add column ab_variant text not null default '' check (ab_variant in ('', 'a', 'b'));

/**
 * Records one event of an anonymous visitor; repeats are ignored. Beyond 1200 events a minute
 * (a flood, not visitors) it silently drops them, so the page never shows an error.
 */
create function public.ab_track(p_experiment text, p_variant text, p_visitor uuid, p_event text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if coalesce(p_experiment, '') !~ '^[a-z0-9-]{1,40}$' or p_variant not in ('a', 'b')
     or p_visitor is null or p_event not in ('view', 'cta') then
    raise exception 'invalid_input';
  end if;
  if (select count(*) from private.ab_events where created_at > now() - interval '1 minute') >= 1200 then
    return;
  end if;
  insert into private.ab_events (experiment, visitor, event, variant)
  values (p_experiment, p_visitor, p_event, p_variant)
  on conflict do nothing;
end;
$$;

-- join_waitlist gains p_ab: the headline variant the visitor saw (kept from the first sign-up).
drop function public.join_waitlist(text, text, text, text, text);
create function public.join_waitlist(
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

/** Standard normal CDF (Abramowitz–Stegun 26.2.17, error < 7.5e-8). */
create function private.normal_cdf(x double precision) returns double precision
language plpgsql immutable set search_path = '' as $$
declare
  t double precision := 1 / (1 + 0.2316419 * abs(x));
  d double precision := 0.3989422804014327 * exp(-x * x / 2);
  p double precision := d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
begin
  return case when x >= 0 then 1 - p else p end;
end;
$$;

/**
 * The owner's report for [p_experiment]: per variant, visitors, button clicks, sign-ups, and
 * confirmed emails with rates, plus the chance that B beats A on confirmed emails and on clicks
 * (Beta(1,1) posteriors, normal approximation). Sign-ups count waitlist rows created since the
 * experiment's first view.
 */
create function private.ab_report(p_experiment text)
returns table (variant text, visitors bigint, clicks bigint, signups bigint, confirmed bigint,
               click_rate numeric, confirm_rate numeric, chance_b_wins_confirmed numeric, chance_b_wins_clicks numeric)
language plpgsql stable security definer set search_path = '' as $$
declare
  v_start timestamptz := (select min(created_at) from private.ab_events where experiment = p_experiment);
begin
  return query
  with counts as (
    select v.variant,
      (select count(*) from private.ab_events e where e.experiment = p_experiment and e.variant = v.variant and e.event = 'view') as visitors,
      (select count(*) from private.ab_events e where e.experiment = p_experiment and e.variant = v.variant and e.event = 'cta') as clicks,
      (select count(*) from public.waitlist w where w.ab_variant = v.variant and w.created_at >= v_start) as signups,
      (select count(*) from public.waitlist w where w.ab_variant = v.variant and w.created_at >= v_start and w.email_verified_at is not null) as confirmed
    from (values ('a'), ('b')) as v(variant)
  ),
  post as (
    -- Beta(1 + successes, 1 + failures): mean and variance of each rate.
    select c.*,
      (c.confirmed + 1.0) / (c.visitors + 2.0) as mc,
      (c.confirmed + 1.0) * (c.visitors - c.confirmed + 1.0) / ((c.visitors + 2.0) ^ 2 * (c.visitors + 3.0)) as vc,
      (c.clicks + 1.0) / (c.visitors + 2.0) as mk,
      (c.clicks + 1.0) * (c.visitors - c.clicks + 1.0) / ((c.visitors + 2.0) ^ 2 * (c.visitors + 3.0)) as vk
    from counts c
  ),
  chance as (
    select
      private.normal_cdf((b.mc - a.mc) / sqrt(a.vc + b.vc)) as pc,
      private.normal_cdf((b.mk - a.mk) / sqrt(a.vk + b.vk)) as pk
    from post a, post b where a.variant = 'a' and b.variant = 'b'
  )
  select p.variant, p.visitors, p.clicks, p.signups, p.confirmed,
    round(100.0 * p.clicks / nullif(p.visitors, 0), 2),
    round(100.0 * p.confirmed / nullif(p.visitors, 0), 2),
    round(100 * ch.pc::numeric, 1),
    round(100 * ch.pk::numeric, 1)
  from post p, chance ch
  order by p.variant;
end;
$$;

revoke all on function public.ab_track(text, text, uuid, text) from public;
grant execute on function public.ab_track(text, text, uuid, text) to anon, authenticated;
revoke all on function public.join_waitlist(text, text, text, text, text, text) from public;
grant execute on function public.join_waitlist(text, text, text, text, text, text) to anon, authenticated;
revoke all on function private.normal_cdf(double precision) from public, anon, authenticated;
revoke all on function private.ab_report(text) from public, anon, authenticated;
