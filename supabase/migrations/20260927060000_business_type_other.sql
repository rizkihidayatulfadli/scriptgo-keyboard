-- "Lainnya" in the business type picker now asks what the business is. Filled only when
-- business_type = 'other' (the app clears it otherwise); see BusinessProfile.businessTypeOther.
alter table public.business_profiles
    add column business_type_other text not null default '' check (char_length(business_type_other) <= 60);
