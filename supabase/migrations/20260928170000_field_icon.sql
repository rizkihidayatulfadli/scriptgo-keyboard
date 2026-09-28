-- A field's icon: a Phosphor Fill name from design/icons/field-icons.json that the user picked.
-- Empty means automatic (the app and extension pick one from the label, see FieldIcons). Older
-- clients leave it out of their upserts, which keeps the stored value.
alter table public.field_definitions
    add column icon text not null default '' check (char_length(icon) <= 40);
