-- Sync (and so the Chrome extension, which only works through sync) is for Pro (PRD §9): the
-- synced tables accept reads and writes only while my plan has Pro features (beta, trial, pro).
-- A free account keeps its data on the phone; nothing on the server is deleted. During the closed
-- beta every account is 'beta', so nothing changes until the limits_from switch is turned on.
-- Enforced here, not only in the apps, because a rebuilt app (GPL) can skip client checks.

/** Whether the signed-in user may sync now (plan beta, trial, or pro). */
create function public.can_sync() returns boolean
language sql stable security definer set search_path = '' as $$
  select auth.uid() is not null and (private.plan_of(auth.uid()) ->> 'plan') <> 'free'
$$;
revoke all on function public.can_sync() from public, anon;
grant execute on function public.can_sync() to authenticated;

alter policy "own templates" on public.templates
  using ((select auth.uid()) = user_id and (select public.can_sync()))
  with check ((select auth.uid()) = user_id and (select public.can_sync()));
alter policy "own contacts" on public.contacts
  using ((select auth.uid()) = user_id and (select public.can_sync()))
  with check ((select auth.uid()) = user_id and (select public.can_sync()));
alter policy "own field definitions" on public.field_definitions
  using ((select auth.uid()) = user_id and (select public.can_sync()))
  with check ((select auth.uid()) = user_id and (select public.can_sync()));
alter policy "own clipboard items" on public.clipboard_items
  using ((select auth.uid()) = user_id and (select public.can_sync()))
  with check ((select auth.uid()) = user_id and (select public.can_sync()));
alter policy "Owners create their settings" on public.user_settings
  with check ((select auth.uid()) = user_id and (select public.can_sync()));
alter policy "Owners read their settings" on public.user_settings
  using ((select auth.uid()) = user_id and (select public.can_sync()));
alter policy "Owners update their settings" on public.user_settings
  using ((select auth.uid()) = user_id and (select public.can_sync()))
  with check ((select auth.uid()) = user_id and (select public.can_sync()));
