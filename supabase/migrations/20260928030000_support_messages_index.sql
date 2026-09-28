-- The support_messages rate limit counts a user's recent messages; index it (Supabase advisor:
-- unindexed foreign key user_id).
create index support_messages_user_created on public.support_messages (user_id, created_at);
