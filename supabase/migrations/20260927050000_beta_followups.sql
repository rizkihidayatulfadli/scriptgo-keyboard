-- Beta follow-up emails: tips 3 days after the invite, a feedback request 10 days after it.
-- A daily pg_cron job (09:00 WIB) sends them through Resend, at most 40 per run so a day's
-- total stays within Resend's free tier together with sign-in emails. followups_off stops them
-- for testers who reply STOP. Sending shares private.send_waitlist_email with the invite trigger.
create extension if not exists pg_cron with schema pg_catalog;

alter table public.waitlist
  add column tips_sent_at timestamptz,
  add column feedback_sent_at timestamptz,
  add column followups_off boolean not null default false;

/** Sends email template [template_key] to one waitlist row; returns the pg_net request id. */
create function private.send_waitlist_email(target public.waitlist, template_key text) returns bigint
language plpgsql security definer set search_path = '' as $$
declare
  api_key text;
  template record;
  safe_name text := private.html_escape(coalesce(nullif(trim(target.name), ''), 'Kak'));
  safe_email text := private.html_escape(target.email);
begin
  select decrypted_secret into api_key from vault.decrypted_secrets where name = 'resend_api_key';
  if api_key is null then
    raise exception 'Vault secret resend_api_key is missing; add it first (docs/beta-launch.md)';
  end if;
  select subject, html into template from private.email_templates where key = template_key;
  if not found then
    raise exception 'Email template % is missing; run supabase/templates/beta/beta_templates.sql', template_key;
  end if;
  return net.http_post(
    url := 'https://api.resend.com/emails',
    headers := jsonb_build_object('Authorization', 'Bearer ' || api_key, 'Content-Type', 'application/json'),
    body := jsonb_build_object(
      'from', 'ScriptGo <halo@scriptgo.id>',
      'to', jsonb_build_array(target.email),
      'reply_to', 'halo@scriptgo.id',
      'subject', replace(template.subject, '{{name}}', coalesce(nullif(trim(target.name), ''), 'Kak')),
      'html', replace(replace(template.html, '{{name}}', safe_name), '{{email}}', safe_email)
    )
  );
end;
$$;

create or replace function private.waitlist_beta_status() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.beta_status is not distinct from old.beta_status then
    return new;
  end if;
  if new.beta_status in ('approved', 'invited') and new.approved_at is null then
    new.approved_at := now();
  end if;
  if new.beta_status = 'invited' then
    new.invite_request_id := private.send_waitlist_email(new, 'beta_invite');
    new.invited_at := now();
  end if;
  return new;
end;
$$;

/** Daily: tips at day 3 and the feedback request at day 10 after the invite. */
create function private.send_beta_followups() returns integer
language plpgsql security definer set search_path = '' as $$
declare
  target public.waitlist;
  sent integer := 0;
begin
  for target in
    select * from public.waitlist
    where beta_status = 'invited' and not followups_off
      and ((tips_sent_at is null and invited_at <= now() - interval '3 days')
        or (feedback_sent_at is null and invited_at <= now() - interval '10 days'))
    order by invited_at
    limit 40
  loop
    -- A tester invited over 10 days ago gets only the feedback email, not both on one day.
    if target.feedback_sent_at is null and target.invited_at <= now() - interval '10 days' then
      perform private.send_waitlist_email(target, 'beta_feedback');
      update public.waitlist set feedback_sent_at = now(), tips_sent_at = coalesce(tips_sent_at, now()) where id = target.id;
    else
      perform private.send_waitlist_email(target, 'beta_tips');
      update public.waitlist set tips_sent_at = now() where id = target.id;
    end if;
    sent := sent + 1;
  end loop;
  return sent;
end;
$$;

revoke all on function private.send_waitlist_email(public.waitlist, text) from public, anon, authenticated;
revoke all on function private.send_beta_followups() from public, anon, authenticated;

select cron.schedule('beta-followups', '0 2 * * *', 'select private.send_beta_followups()');
