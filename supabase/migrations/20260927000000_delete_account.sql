-- Lets a signed-in user delete their own account (required by Google Play for apps with accounts).
-- Deleting the auth user cascades to templates, contacts, and field_definitions.
-- The advisor lint "authenticated_security_definer_function_executable" is expected: signed-in
-- users must call this, and it only ever deletes the caller (auth.uid()).
create function public.delete_account() returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception 'not signed in' using errcode = '42501';
    end if;
    delete from auth.users where id = auth.uid();
end;
$$;

revoke execute on function public.delete_account() from public, anon;
grant execute on function public.delete_account() to authenticated;
