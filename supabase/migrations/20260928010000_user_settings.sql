-- Keyboard and app settings that follow the account, so a reinstall or a new phone keeps them.
-- One row per user; `data` holds typed SharedPreferences values grouped by file (see
-- android/.../sync/SettingsSyncer.kt). updated_at is the client's edit time in epoch ms, and an
-- older write never replaces a newer one (as sync_accept_write does for the sync tables).

create table public.user_settings (
    user_id uuid primary key default auth.uid() references auth.users (id) on delete cascade,
    data jsonb not null default '{}'::jsonb check (octet_length(data::text) <= 262144),
    updated_at bigint not null,
    synced_at timestamptz not null default clock_timestamp()
);

alter table public.user_settings enable row level security;

create policy "Owners read their settings" on public.user_settings
    for select to authenticated using ((select auth.uid()) = user_id);
create policy "Owners create their settings" on public.user_settings
    for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "Owners update their settings" on public.user_settings
    for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

revoke all on public.user_settings from anon;
grant select, insert, update on public.user_settings to authenticated;

create trigger user_settings_accept_write before insert or update on public.user_settings
    for each row execute function public.sync_accept_write();
