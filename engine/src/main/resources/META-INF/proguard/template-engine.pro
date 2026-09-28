# libphonenumber loads its metadata by resource path; keep it intact when the app is shrunk.
-keep class com.google.i18n.phonenumbers.** { *; }
