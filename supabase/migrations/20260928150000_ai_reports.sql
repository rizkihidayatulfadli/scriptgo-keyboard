-- Reports of AI template results (Google Play policy on AI-generated content: users must be able to
-- report offensive output from inside the app). Sent from "Laporkan hasil ini" under an AI result
-- in the app and the extension; only signed-in users can use AI, so only they can report. Nobody
-- reads reports back through the API; the owner reads them in the dashboard (Table editor →
-- ai_reports), marks them read or done, and adjusts the prompt in supabase/functions/ai-template.

create table public.ai_reports (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    mode text not null check (mode in ('create', 'rewrite')),
    style text not null default '' check (style in ('', 'polite', 'shorter', 'casual', 'spelling')),
    -- What the user sent (description, or the template text) and what the AI answered.
    input text not null default '' check (char_length(input) <= 4000),
    result text not null check (char_length(result) between 1 and 4000),
    reason text not null check (reason in ('offensive', 'wrong', 'other')),
    note text not null default '' check (char_length(note) <= 500),
    source text not null check (source in ('android', 'extension')),
    status text not null default 'new' check (status in ('new', 'read', 'done')),
    created_at timestamptz not null default now()
);

create index ai_reports_created on public.ai_reports (created_at desc);

alter table public.ai_reports enable row level security;

create policy "Users send AI reports" on public.ai_reports
    for insert to authenticated
    with check (status = 'new' and user_id = (select auth.uid()));

revoke all on public.ai_reports from anon, authenticated;
grant insert on public.ai_reports to authenticated;

-- Flood guard: at most 10 reports per user per 10 minutes.
create function private.ai_reports_rate_limit() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    if (select count(*) from public.ai_reports
        where user_id = new.user_id and created_at > now() - interval '10 minutes') >= 10 then
        raise exception 'Too many reports, try again later' using errcode = 'P0001', hint = 'rate_limited';
    end if;
    new.created_at := now();
    return new;
end;
$$;

revoke all on function private.ai_reports_rate_limit() from public, anon, authenticated;

create trigger ai_reports_rate_limit before insert on public.ai_reports
    for each row execute function private.ai_reports_rate_limit();
