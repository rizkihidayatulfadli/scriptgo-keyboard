-- Sync tables for templates, contacts, and custom field definitions (M7).
-- Each row belongs to one user; Row Level Security limits every query to the signed-in user's rows.
-- updated_at is the client's edit time in epoch milliseconds (last-write-wins).
-- synced_at is the server time of the last accepted write; clients pull rows with synced_at > their cursor.
-- deleted marks tombstones so deletions reach other devices.

create table public.templates (
    id text primary key,
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    title text not null,
    shortcut text not null,
    body text not null,
    tags text not null default '',
    updated_at bigint not null,
    deleted boolean not null default false,
    synced_at timestamptz not null default clock_timestamp()
);

create table public.contacts (
    id text primary key,
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    name text not null,
    phones text not null default '',
    fields jsonb not null default '{}'::jsonb,
    updated_at bigint not null,
    deleted boolean not null default false,
    synced_at timestamptz not null default clock_timestamp()
);

create table public.field_definitions (
    id text primary key,
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    field_key text not null,
    label text not null,
    type text not null,
    options text not null default '',
    position integer not null default 0,
    updated_at bigint not null,
    deleted boolean not null default false,
    synced_at timestamptz not null default clock_timestamp()
);

create index templates_user_synced on public.templates (user_id, synced_at);
create index contacts_user_synced on public.contacts (user_id, synced_at);
create index field_definitions_user_synced on public.field_definitions (user_id, synced_at);

-- Last write wins: an update carrying an older updated_at than the stored row is ignored.
-- Accepted writes get a fresh synced_at, and a row can never move to another user.
create function public.sync_accept_write() returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if tg_op = 'UPDATE' then
        if new.updated_at < old.updated_at then
            return old;
        end if;
        new.user_id := old.user_id;
    end if;
    new.synced_at := clock_timestamp();
    return new;
end;
$$;

create trigger templates_accept_write before insert or update on public.templates
    for each row execute function public.sync_accept_write();
create trigger contacts_accept_write before insert or update on public.contacts
    for each row execute function public.sync_accept_write();
create trigger field_definitions_accept_write before insert or update on public.field_definitions
    for each row execute function public.sync_accept_write();

alter table public.templates enable row level security;
alter table public.contacts enable row level security;
alter table public.field_definitions enable row level security;

create policy "own templates" on public.templates for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "own contacts" on public.contacts for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "own field definitions" on public.field_definitions for all to authenticated
    using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
