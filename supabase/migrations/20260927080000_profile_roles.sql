-- Roles are now owner, employee, freelance, and student; the business name question follows the
-- role (see Role in engine/.../profile/BusinessProfile.kt). 'admin' and 'other' stay valid for rows
-- saved by older app versions; the app reads 'admin' as 'employee' and asks again for 'other'.
alter table public.business_profiles drop constraint if exists business_profiles_role_check;
alter table public.business_profiles add constraint business_profiles_role_check
    check (role in ('owner', 'employee', 'freelance', 'student', 'admin', 'other'));
