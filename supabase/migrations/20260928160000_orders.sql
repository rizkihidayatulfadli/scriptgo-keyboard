-- Paying for Pro on scriptgo.id/bayar through Duitku (PRD §9: payment only on the web). The Edge
-- Function `payments` creates an order here (price computed in SQL, never taken from the browser),
-- sends the buyer to Duitku, and on Duitku's verified callback marks it paid, which extends
-- entitlements.pro_until. Users can read their own orders (for /bayar?order=…); only the service
-- role writes them.
--
-- Prices (Rp): Pro 1 bulan 49.000, 3 bulan 125.000, 12 bulan 439.000. Founder price (docs/beta-
-- launch.md): 50% for accounts whose email confirmed a beta sign-up, for orders placed until 12
-- months after the limits_from switch (or any time before it).

create table public.orders (
    id text primary key check (id ~ '^SG-[0-9A-Z]{10}$'),
    user_id uuid not null references auth.users (id) on delete cascade,
    product text not null check (product in ('pro_1', 'pro_3', 'pro_12')),
    months int not null check (months in (1, 3, 12)),
    amount int not null check (amount > 0),
    founder boolean not null default false,
    status text not null default 'pending' check (status in ('pending', 'paid', 'failed')),
    reference text not null default '',
    payment_url text not null default '',
    created_at timestamptz not null default now(),
    paid_at timestamptz
);

create index orders_user on public.orders (user_id, created_at desc);

alter table public.orders enable row level security;
create policy "Owners read their orders" on public.orders
    for select to authenticated using ((select auth.uid()) = user_id);
revoke all on public.orders from anon, authenticated;
grant select on public.orders to authenticated;

/** Months, normal price, and label of [product]; null for an unknown one. */
create function private.product_info(product text, out months int, out amount int, out label text)
language sql immutable as $$
  select p.months, p.amount, p.label from (values
    ('pro_1', 1, 49000, 'ScriptGo Pro 1 bulan'),
    ('pro_3', 3, 125000, 'ScriptGo Pro 3 bulan'),
    ('pro_12', 12, 439000, 'ScriptGo Pro 12 bulan')
  ) as p(code, months, amount, label) where p.code = product_info.product
$$;

/** Whether [target] pays the founder price now (confirmed beta sign-up, within 12 months of release). */
create function private.is_founder(target uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.waitlist w join auth.users u on lower(u.email) = lower(w.email)
    where u.id = target and w.email_verified_at is not null
  ) and coalesce(
    (select now() < (value #>> '{}')::timestamptz + interval '12 months' from private.app_config where key = 'limits_from'),
    true
  )
$$;

/** Prices for the signed-in user, for /bayar: [{product, months, amount, normal, label}], and founder. */
create function public.payment_quote() returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
  founder boolean;
begin
  if auth.uid() is null then
    raise exception 'not_signed_in' using errcode = '42501';
  end if;
  founder := private.is_founder(auth.uid());
  return jsonb_build_object(
    'founder', founder,
    'products', (
      select jsonb_agg(jsonb_build_object(
        'product', code, 'months', i.months, 'label', i.label, 'normal', i.amount,
        'amount', case when founder then (i.amount / 2) else i.amount end
      ) order by i.months)
      from unnest(array['pro_1', 'pro_3', 'pro_12']) as code, private.product_info(code) i
    )
  );
end;
$$;

/**
 * A new pending order for [target] and [product] at today's price; returns the order with the
 * buyer's email and name for the invoice. Service role only (the Edge Function).
 */
create function public.payments_create_order(target uuid, product text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  info record;
  founder boolean := private.is_founder(target);
  buyer auth.users;
  new_id text := 'SG-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 10));
  o public.orders;
begin
  select * into info from private.product_info(product);
  if info.months is null then
    raise exception 'unknown_product';
  end if;
  select * into buyer from auth.users where id = target;
  if buyer.id is null or buyer.email is null then
    raise exception 'unknown_user';
  end if;
  -- A small cap against runaway retries: 10 open orders per account.
  if (select count(*) from public.orders where user_id = target and status = 'pending' and created_at > now() - interval '1 day') >= 10 then
    raise exception 'too_many_orders';
  end if;
  insert into public.orders (id, user_id, product, months, amount, founder)
    values (new_id, target, product, info.months, case when founder then info.amount / 2 else info.amount end, founder)
    returning * into o;
  return jsonb_build_object(
    'id', o.id, 'amount', o.amount, 'label', info.label || case when founder then ' (harga pendiri)' else '' end,
    'email', buyer.email,
    'name', coalesce(nullif(trim(buyer.raw_user_meta_data ->> 'full_name'), ''), nullif(trim(buyer.raw_user_meta_data ->> 'name'), ''), '')
  );
end;
$$;

/** Saves Duitku's reference and payment page for [order_id]. Service role only. */
create function public.payments_attach(order_id text, ref text, url text) returns void
language sql security definer set search_path = '' as $$
  update public.orders set reference = ref, payment_url = url where id = order_id and status = 'pending'
$$;

/**
 * Marks [order_id] paid after Duitku confirmed [paid_amount] (idempotent: a second callback changes
 * nothing) and extends Pro by the order's months from the later of now and the current end.
 * Returns the new pro_until, or null when the order was already paid. Service role only.
 */
create function public.payments_mark_paid(order_id text, paid_amount int, ref text) returns timestamptz
language plpgsql security definer set search_path = '' as $$
declare
  o public.orders;
  current_end timestamptz;
  new_end timestamptz;
begin
  select * into o from public.orders where id = order_id for update;
  if not found then
    raise exception 'unknown_order';
  end if;
  if o.status = 'paid' then
    return null;
  end if;
  if o.amount <> paid_amount then
    raise exception 'amount_mismatch';
  end if;
  update public.orders set status = 'paid', paid_at = now(), reference = coalesce(nullif(ref, ''), reference) where id = order_id;
  select pro_until into current_end from public.entitlements
    where user_id = o.user_id and plan = 'pro' and (pro_until is null or pro_until > now());
  if found and current_end is null then
    return null; -- Pro without an end date stays that way
  end if;
  new_end := greatest(coalesce(current_end, now()), now()) + make_interval(months => o.months);
  insert into public.entitlements (user_id, plan, pro_until, note, updated_at)
    values (o.user_id, 'pro', new_end, 'duitku ' || order_id, now())
    on conflict (user_id) do update set plan = 'pro', pro_until = excluded.pro_until, note = excluded.note, updated_at = now();
  return new_end;
end;
$$;

/** Marks [order_id] failed (Duitku reported a failed or expired payment). Service role only. */
create function public.payments_mark_failed(order_id text) returns void
language sql security definer set search_path = '' as $$
  update public.orders set status = 'failed' where id = order_id and status = 'pending'
$$;

revoke all on function private.product_info(text) from public, anon, authenticated;
revoke all on function private.is_founder(uuid) from public, anon, authenticated;
revoke all on function public.payment_quote() from public, anon;
grant execute on function public.payment_quote() to authenticated;
revoke all on function public.payments_create_order(uuid, text) from public, anon, authenticated;
revoke all on function public.payments_attach(text, text, text) from public, anon, authenticated;
revoke all on function public.payments_mark_paid(text, int, text) from public, anon, authenticated;
revoke all on function public.payments_mark_failed(text) from public, anon, authenticated;
grant execute on function public.payments_create_order(uuid, text) to service_role;
grant execute on function public.payments_attach(text, text, text) to service_role;
grant execute on function public.payments_mark_paid(text, int, text) to service_role;
grant execute on function public.payments_mark_failed(text) to service_role;
