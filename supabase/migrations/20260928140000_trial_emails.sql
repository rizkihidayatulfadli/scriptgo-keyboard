-- Trial emails (PRD §9): 3 days before an account's trial ends, and once it has ended, with a link
-- to the pricing page (emails may link to payment; the app may not). A daily pg_cron job (09:05
-- WIB) sends them through Resend, at most 40 per run, and only once the limits_from switch is on,
-- so nothing is sent during the closed beta. Each kind is sent at most once per account.
-- Templates: private.email_templates 'trial_ending' and 'trial_ended' (supabase/templates/plan).

create table private.plan_email_log (
  user_id uuid not null references auth.users (id) on delete cascade,
  kind text not null check (kind in ('trial_ending', 'trial_ended')),
  sent_at timestamptz not null default now(),
  request_id bigint,
  primary key (user_id, kind)
);

/** Where the plan emails send people; /bayar once the payment page exists. */
create function private.pricing_link() returns text
language sql immutable as $$ select 'https://scriptgo.id/#harga' $$;

/** Sends plan email [template_key] to [target]; returns the pg_net request id. */
create function private.send_plan_email(target auth.users, template_key text, trial_end timestamptz) returns bigint
language plpgsql security definer set search_path = '' as $$
declare
  api_key text;
  template record;
  display_name text := coalesce(nullif(trim(target.raw_user_meta_data ->> 'full_name'), ''), nullif(trim(target.raw_user_meta_data ->> 'name'), ''), 'Kak');
  days text := greatest(ceil(extract(epoch from trial_end - now()) / 86400), 0)::int::text;
  end_date text := to_char(trial_end at time zone 'Asia/Jakarta', 'FMDD')
    || ' ' || (array['Jan','Feb','Mar','Apr','Mei','Jun','Jul','Agu','Sep','Okt','Nov','Des'])[extract(month from trial_end at time zone 'Asia/Jakarta')::int]
    || ' ' || to_char(trial_end at time zone 'Asia/Jakarta', 'YYYY');
  body_html text;
begin
  select decrypted_secret into api_key from vault.decrypted_secrets where name = 'resend_api_key';
  if api_key is null then
    raise exception 'Vault secret resend_api_key is missing; add it first (docs/beta-launch.md)';
  end if;
  select t.subject, t.html into template from private.email_templates t where t.key = template_key;
  if not found then
    raise exception 'Email template % is missing; run supabase/templates/plan/plan_templates.sql', template_key;
  end if;
  body_html := replace(replace(replace(replace(replace(template.html,
    '{{name}}', private.html_escape(display_name)), '{{email}}', private.html_escape(target.email)),
    '{{days}}', days), '{{date}}', end_date), '{{link}}', private.pricing_link());
  return net.http_post(
    url := 'https://api.resend.com/emails',
    headers := jsonb_build_object('Authorization', 'Bearer ' || api_key, 'Content-Type', 'application/json'),
    body := jsonb_build_object(
      'from', 'ScriptGo <halo@scriptgo.id>',
      'to', jsonb_build_array(target.email),
      'reply_to', 'halo@scriptgo.id',
      'subject', replace(replace(template.subject, '{{days}}', days), '{{name}}', display_name),
      'html', body_html
    )
  );
end;
$$;

/** Daily: 'trial_ending' when 3 days or less are left, 'trial_ended' in the week after the end. */
create function private.send_trial_emails() returns integer
language plpgsql security definer set search_path = '' as $$
declare
  limits_from timestamptz;
  r record;
  sent integer := 0;
begin
  select (value #>> '{}')::timestamptz into limits_from from private.app_config where key = 'limits_from';
  if limits_from is null or limits_from > now() then
    return 0;
  end if;
  for r in
    select u, greatest(u.created_at, limits_from) + interval '14 days' as trial_end
    from auth.users u
    where u.email is not null and u.email_confirmed_at is not null and u.deleted_at is null
      and not exists (
        select 1 from public.entitlements e
        where e.user_id = u.id and e.plan = 'pro' and (e.pro_until is null or e.pro_until > now())
      )
      and greatest(u.created_at, limits_from) + interval '14 days' between now() - interval '7 days' and now() + interval '3 days'
      -- Not sent yet: trial_ending before the end (none of either kind), trial_ended after it.
      and not exists (
        select 1 from private.plan_email_log l
        where l.user_id = u.id
          and (l.kind = 'trial_ended' or greatest(u.created_at, limits_from) + interval '14 days' > now())
      )
    order by 2
    limit 40
  loop
    if r.trial_end > now() then
      insert into private.plan_email_log (user_id, kind, request_id)
        values ((r.u).id, 'trial_ending', private.send_plan_email(r.u, 'trial_ending', r.trial_end));
    else
      insert into private.plan_email_log (user_id, kind, request_id)
        values ((r.u).id, 'trial_ended', private.send_plan_email(r.u, 'trial_ended', r.trial_end));
    end if;
    sent := sent + 1;
  end loop;
  return sent;
end;
$$;

revoke all on function private.pricing_link() from public, anon, authenticated;
revoke all on function private.send_plan_email(auth.users, text, timestamptz) from public, anon, authenticated;
revoke all on function private.send_trial_emails() from public, anon, authenticated;

select cron.schedule('trial-emails', '5 2 * * *', 'select private.send_trial_emails()');
