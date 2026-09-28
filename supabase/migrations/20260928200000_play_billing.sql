-- Google Play Billing (docs/play-billing.md): Pro bought inside the Android app. The Edge Function
-- `play-billing` verifies each purchase with the Google Play Developer API (from the app right after
-- buying, and again on every Real-time Developer Notification) and stores the subscription here.
-- Web payments (Duitku, public.orders) keep extending entitlements.pro_until; a Play subscription
-- counts as Pro while it is active, so the two never overwrite each other.

create table public.play_subscriptions (
    purchase_token text primary key check (char_length(purchase_token) between 10 and 4096),
    user_id uuid not null references auth.users (id) on delete cascade,
    product_id text not null check (char_length(product_id) <= 100),
    base_plan_id text not null default '' check (char_length(base_plan_id) <= 100),
    offer_id text not null default '' check (char_length(offer_id) <= 100),
    -- subscriptionState from purchases.subscriptionsv2, without the SUBSCRIPTION_STATE_ prefix.
    state text not null check (state in ('pending', 'active', 'paused', 'in_grace_period', 'on_hold', 'canceled', 'expired', 'pending_purchase_canceled', 'unspecified')),
    expires_at timestamptz,
    auto_renewing boolean not null default false,
    acknowledged boolean not null default false,
    test_purchase boolean not null default false,
    latest_order_id text not null default '',
    -- An upgrade or resubscribe replaces the old token; the old row stops counting.
    replaced_by text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index play_subscriptions_user on public.play_subscriptions (user_id, expires_at desc);

alter table public.play_subscriptions enable row level security;
create policy "Owners read their Play subscriptions" on public.play_subscriptions
    for select to authenticated using ((select auth.uid()) = user_id);
revoke all on public.play_subscriptions from anon, authenticated;
grant select on public.play_subscriptions to authenticated;

/**
 * When [target]'s Play subscriptions give Pro until: the latest expiry among subscriptions that are
 * active, in grace, or canceled but not yet expired (Google keeps access until the paid period
 * ends). Null when none does.
 */
create function private.play_pro_until(target uuid) returns timestamptz
language sql stable security definer set search_path = '' as $$
  select max(expires_at) from public.play_subscriptions
  where user_id = target and replaced_by is null
    and state in ('active', 'in_grace_period', 'canceled')
    and expires_at > now()
$$;

/** [target]'s plan as JSON: plan, trial_ends_at (trial only), pro_until (pro; null = no end), store. */
create or replace function private.plan_of(target uuid) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
  ent public.entitlements;
  web_pro boolean;
  play_until timestamptz := private.play_pro_until(target);
  limits_from timestamptz;
  created timestamptz;
  trial_end timestamptz;
begin
  select * into ent from public.entitlements
    where user_id = target and plan = 'pro' and (pro_until is null or pro_until > now());
  web_pro := found;
  if web_pro or play_until is not null then
    return jsonb_build_object(
      'plan', 'pro',
      -- Pro without an end date stays that way; otherwise the later of the two.
      'pro_until', case when web_pro and ent.pro_until is null then null else greatest(ent.pro_until, play_until) end,
      -- 'play' when Google Play is what keeps Pro running (it renews there, not on the web).
      'store', case when play_until is not null and (not web_pro or (ent.pro_until is not null and play_until >= ent.pro_until)) then 'play' else 'web' end
    );
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

/**
 * The account a Play purchase belongs to, from the obfuscatedExternalAccountId the app sets
 * (lowercase hex sha256 of the user id). Null when no account matches. Service role only.
 */
create function public.play_account_for(account_hash text) returns uuid
language sql stable security definer set search_path = '' as $$
  select id from auth.users where encode(sha256(convert_to(id::text, 'UTF8')), 'hex') = lower(account_hash) limit 1
$$;

/**
 * Stores what Google says about [purchase_token] (insert or update). [replaces] is the
 * linkedPurchaseToken of an upgrade or resubscribe: that older row is marked replaced.
 * Service role only (the Edge Function, after verifying with Google).
 */
create function public.play_upsert_subscription(
    purchase_token text, target uuid, product_id text, base_plan_id text, offer_id text, state text,
    expires_at timestamptz, auto_renewing boolean, acknowledged boolean, test_purchase boolean,
    latest_order_id text, replaces text default null
) returns jsonb
language plpgsql security definer set search_path = '' as $$
#variable_conflict use_column
declare
  owner uuid;
begin
  select user_id into owner from public.play_subscriptions where purchase_token = play_upsert_subscription.purchase_token;
  -- A token never moves to another account.
  if owner is not null and owner <> target then
    raise exception 'token_owned_by_other_account';
  end if;
  insert into public.play_subscriptions as s (purchase_token, user_id, product_id, base_plan_id, offer_id, state, expires_at,
      auto_renewing, acknowledged, test_purchase, latest_order_id, updated_at)
    values (play_upsert_subscription.purchase_token, target, play_upsert_subscription.product_id, coalesce(play_upsert_subscription.base_plan_id, ''),
      coalesce(play_upsert_subscription.offer_id, ''), play_upsert_subscription.state, play_upsert_subscription.expires_at,
      play_upsert_subscription.auto_renewing, play_upsert_subscription.acknowledged, play_upsert_subscription.test_purchase,
      coalesce(play_upsert_subscription.latest_order_id, ''), now())
    on conflict (purchase_token) do update set
      product_id = excluded.product_id, base_plan_id = excluded.base_plan_id, offer_id = excluded.offer_id,
      state = excluded.state, expires_at = excluded.expires_at, auto_renewing = excluded.auto_renewing,
      acknowledged = s.acknowledged or excluded.acknowledged, test_purchase = excluded.test_purchase,
      latest_order_id = excluded.latest_order_id, updated_at = now();
  if replaces is not null and replaces <> play_upsert_subscription.purchase_token then
    update public.play_subscriptions set replaced_by = play_upsert_subscription.purchase_token, updated_at = now()
      where purchase_token = replaces and user_id = target;
  end if;
  return private.plan_of(target);
end;
$$;

/** Marks [purchase_token] acknowledged after the Edge Function acknowledged it with Google. Service role only. */
create function public.play_mark_acknowledged(purchase_token text) returns void
language sql security definer set search_path = '' as $$
  update public.play_subscriptions set acknowledged = true, updated_at = now()
  where play_subscriptions.purchase_token = play_mark_acknowledged.purchase_token
$$;

/** The account that owns [purchase_token], for notifications about a token already stored. Service role only. */
create function public.play_owner_of(purchase_token text) returns uuid
language sql stable security definer set search_path = '' as $$
  select user_id from public.play_subscriptions where play_subscriptions.purchase_token = play_owner_of.purchase_token
$$;

revoke all on function private.play_pro_until(uuid) from public, anon, authenticated;
revoke all on function public.play_account_for(text) from public, anon, authenticated;
revoke all on function public.play_upsert_subscription(text, uuid, text, text, text, text, timestamptz, boolean, boolean, boolean, text, text) from public, anon, authenticated;
revoke all on function public.play_mark_acknowledged(text) from public, anon, authenticated;
revoke all on function public.play_owner_of(text) from public, anon, authenticated;
grant execute on function public.play_account_for(text) to service_role;
grant execute on function public.play_upsert_subscription(text, uuid, text, text, text, text, timestamptz, boolean, boolean, boolean, text, text) to service_role;
grant execute on function public.play_mark_acknowledged(text) to service_role;
grant execute on function public.play_owner_of(text) to service_role;
