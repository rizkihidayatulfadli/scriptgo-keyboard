-- A field's fallback: the raw value its template variable takes when a contact has none and the
-- template gives no fallback of its own (see "Fallback field" in docs/template-syntax.md).
-- Empty means no fallback. Older clients leave it out of their upserts, which keeps the stored value.
alter table public.field_definitions
    add column fallback text not null default '' check (char_length(fallback) <= 200);
