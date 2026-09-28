-- Longer business type list (onboarding dropdown) and the province, detected from the phone's
-- approximate location or picked from a list. Only city and province are stored, never coordinates.
alter table public.business_profiles drop constraint if exists business_profiles_business_type_check;
alter table public.business_profiles add constraint business_profiles_business_type_check
  check (business_type in ('fashion', 'food', 'beauty', 'electronics', 'services', 'muslim_fashion', 'shoes_bags', 'accessories', 'health', 'frozen_catering', 'bakery', 'grocery', 'ppob', 'home', 'furniture', 'baby', 'toys_hobby', 'books_stationery', 'automotive', 'agriculture', 'pets', 'crafts', 'digital', 'printing', 'laundry', 'education', 'travel', 'property', 'beauty_salon', 'other'));

alter table public.business_profiles add column province text not null default ''
  check (province = '' or province in ('Aceh', 'Sumatera Utara', 'Sumatera Barat', 'Riau', 'Kepulauan Riau', 'Jambi', 'Bengkulu', 'Sumatera Selatan', 'Kepulauan Bangka Belitung', 'Lampung', 'DKI Jakarta', 'Banten', 'Jawa Barat', 'Jawa Tengah', 'DI Yogyakarta', 'Jawa Timur', 'Bali', 'Nusa Tenggara Barat', 'Nusa Tenggara Timur', 'Kalimantan Barat', 'Kalimantan Tengah', 'Kalimantan Selatan', 'Kalimantan Timur', 'Kalimantan Utara', 'Sulawesi Utara', 'Gorontalo', 'Sulawesi Tengah', 'Sulawesi Barat', 'Sulawesi Selatan', 'Sulawesi Tenggara', 'Maluku', 'Maluku Utara', 'Papua', 'Papua Barat', 'Papua Barat Daya', 'Papua Tengah', 'Papua Pegunungan', 'Papua Selatan'));
