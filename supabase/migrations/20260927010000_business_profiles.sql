-- Optional business profile asked once after the first sign-in (PRD 9): one row per user, used for
-- business analysis. Codes match engine/src/main/kotlin/com/magickeyboard/profile/BusinessProfile.kt.
-- Deleting the auth user (delete_account) cascades to this row.

create table public.business_profiles (
    user_id uuid primary key default auth.uid() references auth.users (id) on delete cascade,
    business_name text not null default '' check (char_length(business_name) <= 120),
    business_type text check (business_type in ('fashion', 'food', 'beauty', 'electronics', 'services', 'other')),
    city text not null default '' check (char_length(city) <= 80),
    role text check (role in ('owner', 'admin', 'other')),
    channels text[] not null default '{}'
        check (channels <@ array['whatsapp', 'instagram', 'tiktok_shop', 'shopee', 'tokopedia', 'website']::text[]),
    team_size text check (team_size in ('solo', '2_3', '4_10', '10_plus')),
    chats_per_day text check (chats_per_day in ('lt_20', '20_50', '50_100', '100_plus')),
    monthly_revenue text check (monthly_revenue in ('lt_10m', '10_50m', '50_200m', 'gt_200m', 'secret')),
    -- E.164, normalized by the app.
    whatsapp text not null default '' check (whatsapp = '' or whatsapp ~ '^\+[1-9][0-9]{6,14}$'),
    whatsapp_opt_in boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.business_profiles enable row level security;

create policy "Owners read their profile" on public.business_profiles
    for select to authenticated using ((select auth.uid()) = user_id);
create policy "Owners create their profile" on public.business_profiles
    for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "Owners update their profile" on public.business_profiles
    for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

revoke all on public.business_profiles from anon;
grant select, insert, update on public.business_profiles to authenticated;

create function public.business_profiles_touch() returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

create trigger business_profiles_touch before update on public.business_profiles
    for each row execute function public.business_profiles_touch();
