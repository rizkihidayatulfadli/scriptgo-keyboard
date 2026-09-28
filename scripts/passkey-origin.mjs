// Turns the app's signing certificate SHA-256 fingerprint(s) into what passkeys need:
// - the Android origin for Supabase Auth's WebAuthn origins (android:apk-key-hash:<base64url>)
// - the ANDROID_CERT_SHA256 value for site/build.mjs (assetlinks.json)
// Usage: node scripts/passkey-origin.mjs AB:CD:...:EF [12:34:...]
// Fingerprints are public (anyone can read them from the APK); no secret is needed.
const inputs = process.argv.slice(2);
if (!inputs.length) {
  console.error("Usage: node scripts/passkey-origin.mjs <SHA-256 fingerprint> [more fingerprints]");
  process.exit(1);
}
const origins = [];
const normalized = [];
for (const input of inputs) {
  const hex = input.replace(/[^0-9a-f]/gi, "");
  if (hex.length !== 64) {
    console.error(`Not a SHA-256 fingerprint (64 hex digits): ${input}`);
    process.exit(1);
  }
  normalized.push(hex.toUpperCase().match(/../g).join(":"));
  origins.push(`android:apk-key-hash:${Buffer.from(hex, "hex").toString("base64url")}`);
}
console.log("Supabase webauthn_rp_origins:");
console.log(["https://scriptgo.id", ...origins].join(","));
console.log("\nANDROID_CERT_SHA256 for site/build.mjs:");
console.log(normalized.join(","));
