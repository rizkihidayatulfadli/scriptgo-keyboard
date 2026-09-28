-- Pusat bantuan: help requests, ideas, and bug reports sent from the app and the extension.
-- Anyone may send (signed in or not); nobody can read them back through the API. The owner reads
-- them in the Supabase dashboard (Table editor → support_messages) and marks them read or done.

create table public.support_messages (
    id uuid primary key default gen_random_uuid(),
    user_id uuid default auth.uid() references auth.users (id) on delete set null,
    kind text not null check (kind in ('help', 'idea', 'bug')),
    message text not null check (char_length(btrim(message)) between 1 and 4000),
    -- Where to reply when the sender is not signed in (or wants another address); may be empty.
    reply_email text not null default ''
        check (reply_email = '' or (char_length(reply_email) <= 254 and reply_email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$')),
    source text not null check (source in ('android', 'extension')),
    app_version text not null default '' check (char_length(app_version) <= 40),
    device text not null default '' check (char_length(device) <= 120),
    language text not null default '' check (char_length(language) <= 10),
    status text not null default 'new' check (status in ('new', 'read', 'done')),
    created_at timestamptz not null default now()
);

create index support_messages_created on public.support_messages (created_at desc);

alter table public.support_messages enable row level security;

-- Insert only, as yourself or anonymously, and always as a new message.
create policy "Anyone can send a support message" on public.support_messages
    for insert to anon, authenticated
    with check (status = 'new' and (user_id is null or user_id = (select auth.uid())));

revoke all on public.support_messages from anon, authenticated;
grant insert on public.support_messages to anon, authenticated;

-- Flood guard: at most 5 messages per signed-in user, and 30 anonymous ones overall, per 10 minutes.
create function public.support_messages_rate_limit() returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if new.user_id is not null then
        if (select count(*) from public.support_messages
            where user_id = new.user_id and created_at > now() - interval '10 minutes') >= 5 then
            raise exception 'Too many messages, try again later' using errcode = 'P0001', hint = 'rate_limited';
        end if;
    elsif (select count(*) from public.support_messages
           where user_id is null and created_at > now() - interval '10 minutes') >= 30 then
        raise exception 'Too many messages, try again later' using errcode = 'P0001', hint = 'rate_limited';
    end if;
    new.created_at := now();
    return new;
end;
$$;

revoke execute on function public.support_messages_rate_limit() from public, anon, authenticated;

create trigger support_messages_rate_limit before insert on public.support_messages
    for each row execute function public.support_messages_rate_limit();
