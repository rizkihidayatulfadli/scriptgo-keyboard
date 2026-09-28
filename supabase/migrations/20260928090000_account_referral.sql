-- "Ajak teman" in the app (Akun) and the extension popup: every account gets a referral link.
-- my_referral() returns the account's code (the one from its beta sign-up if the email is on the
-- waitlist, else one kept in account_referrals) and how many friends signed up with it and
-- confirmed their email. Friends join through scriptgo.id/?ref=<code>, like the /konfirmasi link.
create table public.account_referrals (
  user_id uuid primary key references auth.users (id) on delete cascade,
  referral_code text not null unique check (referral_code ~ '^[a-z0-9]{6,12}$'),
  created_at timestamptz not null default now()
);
alter table public.account_referrals enable row level security;
revoke all on public.account_referrals from anon, authenticated;

create function public.my_referral() returns json
language plpgsql security definer set search_path = '' as $$
declare
  v_uid uuid := auth.uid();
  v_email text;
  v_code text;
begin
  if v_uid is null then
    raise exception 'not_signed_in';
  end if;
  select lower(email) into v_email from auth.users where id = v_uid;
  select referral_code into v_code from public.waitlist where lower(email) = v_email;
  if v_code is null then
    select referral_code into v_code from public.account_referrals where user_id = v_uid;
  end if;
  while v_code is null loop
    v_code := private.new_referral_code();
    if exists (select 1 from public.waitlist where referral_code = v_code) then
      v_code := null;
    else
      insert into public.account_referrals (user_id, referral_code) values (v_uid, v_code)
        on conflict do nothing;
      select referral_code into v_code from public.account_referrals where user_id = v_uid;
    end if;
  end loop;
  return json_build_object(
    'code', v_code,
    'joined', (select count(*) from public.waitlist where referred_by = v_code and email_verified_at is not null)
  );
end;
$$;

revoke all on function public.my_referral() from public, anon;
grant execute on function public.my_referral() to authenticated;
