-- Clipboard history that follows the account, for users who keep it without a time limit.
-- Every item is encrypted on the phone (AES-256-GCM) with a key that never reaches this server
-- (it lives in Google's Block Store), and ids are HMACs of the text, so this table holds nothing
-- readable. Deletions are tombstones, like the other sync tables.

create table public.clipboard_items (
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    id text not null check (id ~ '^[0-9a-f]{32}$'),
    payload text not null default '' check (char_length(payload) <= 400000),
    updated_at bigint not null,
    deleted boolean not null default false,
    synced_at timestamptz not null default clock_timestamp(),
    primary key (user_id, id)
);

create index clipboard_items_user_synced on public.clipboard_items (user_id, synced_at);

alter table public.clipboard_items enable row level security;

create policy "own clipboard items" on public.clipboard_items for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

revoke all on public.clipboard_items from anon;
grant select, insert, update on public.clipboard_items to authenticated;

create trigger clipboard_items_accept_write before insert or update on public.clipboard_items
    for each row execute function public.sync_accept_write();
