-- Beta and waitlist sign-ups from scriptgo.id. Visitors can only insert (no read back); the owner
-- reads them in the dashboard. Referral codes let early users invite others for founding perks.
create table public.waitlist (
  id uuid primary key default gen_random_uuid(),
  email text not null check (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$' and length(email) <= 254),
  name text not null default '' check (length(name) <= 120),
  whatsapp text not null default '' check (whatsapp = '' or whatsapp ~ '^\+[1-9][0-9]{6,14}$'),
  business_type text not null default '' check (business_type in ('', 'fashion', 'food', 'beauty', 'electronics', 'services', 'other')),
  role text not null default '' check (role in ('', 'owner', 'admin', 'other')),
  wants_beta boolean not null default false,
  device text not null default '' check (device in ('', 'android', 'iphone', 'laptop')),
  referral_code text not null unique check (referral_code ~ '^[a-z0-9]{6,12}$'),
  referred_by text not null default '' check (referred_by = '' or referred_by ~ '^[a-z0-9]{6,12}$'),
  source text not null default '' check (length(source) <= 200),
  created_at timestamptz not null default now()
);

create unique index waitlist_email_key on public.waitlist (lower(email));
create index waitlist_referred_by_idx on public.waitlist (referred_by) where referred_by <> '';

alter table public.waitlist enable row level security;

create policy "Anyone can join the waitlist" on public.waitlist
  for insert to anon, authenticated
  with check (true);

revoke all on public.waitlist from anon, authenticated;
grant insert (email, name, whatsapp, business_type, role, wants_beta, device, referral_code, referred_by, source)
  on public.waitlist to anon, authenticated;
