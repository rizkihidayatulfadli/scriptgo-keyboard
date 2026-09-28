-- Beta through Google Play closed testing. The owner reviews waitlist sign-ups and moves them
-- pending → approved (then adds the email to the Play tester list) → invited. Setting 'invited'
-- sends the invite email through Resend (pg_net, after the transaction commits). The Resend key
-- lives in Vault as 'resend_api_key'; the template in private.email_templates. Flow and SQL for
-- the owner: docs/beta-launch.md.
create extension if not exists pg_net with schema extensions;

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table private.email_templates (
  key text primary key,
  subject text not null,
  html text not null,
  updated_at timestamptz not null default now()
);

alter table public.waitlist
  add column beta_status text not null default 'pending'
    check (beta_status in ('pending', 'approved', 'invited', 'rejected')),
  add column approved_at timestamptz,
  add column invited_at timestamptz,
  add column invite_request_id bigint;

create index waitlist_beta_status_idx on public.waitlist (beta_status) where wants_beta;

create function private.html_escape(value text) returns text
language sql immutable set search_path = '' as $$
  select replace(replace(replace(replace(value, '&', '&amp;'), '<', '&lt;'), '>', '&gt;'), '"', '&quot;')
$$;

create function private.waitlist_beta_status() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  api_key text;
  template record;
  request_id bigint;
begin
  if new.beta_status is not distinct from old.beta_status then
    return new;
  end if;
  if new.beta_status in ('approved', 'invited') and new.approved_at is null then
    new.approved_at := now();
  end if;
  if new.beta_status <> 'invited' then
    return new;
  end if;

  select decrypted_secret into api_key from vault.decrypted_secrets where name = 'resend_api_key';
  if api_key is null then
    raise exception 'Vault secret resend_api_key is missing; add it first (docs/beta-launch.md)';
  end if;
  select subject, html into template from private.email_templates where key = 'beta_invite';
  if not found then
    raise exception 'Email template beta_invite is missing; run supabase/templates/beta/beta_invite.sql';
  end if;

  select net.http_post(
    url := 'https://api.resend.com/emails',
    headers := jsonb_build_object('Authorization', 'Bearer ' || api_key, 'Content-Type', 'application/json'),
    body := jsonb_build_object(
      'from', 'ScriptGo <halo@scriptgo.id>',
      'to', jsonb_build_array(new.email),
      'reply_to', 'halo@scriptgo.id',
      'subject', template.subject,
      'html', replace(
        replace(template.html, '{{name}}', private.html_escape(coalesce(nullif(trim(new.name), ''), 'Kak'))),
        '{{email}}', private.html_escape(new.email)
      )
    )
  ) into request_id;

  new.invited_at := now();
  new.invite_request_id := request_id;
  return new;
end;
$$;

revoke all on function private.waitlist_beta_status() from public, anon, authenticated;
revoke all on function private.html_escape(text) from public, anon, authenticated;

create trigger waitlist_beta_status
  before update of beta_status on public.waitlist
  for each row execute function private.waitlist_beta_status();
