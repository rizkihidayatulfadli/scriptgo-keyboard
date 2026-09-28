package com.magickeyboard.sync

import android.content.Context
import com.magickeyboard.auth.AuthCallback
import com.magickeyboard.clipboard.ClipboardSync
import com.magickeyboard.auth.Pkce
import com.magickeyboard.manager.AppLanguage
import com.magickeyboard.manager.data.ManagerRepository
import com.magickeyboard.manager.data.SyncTable
import com.magickeyboard.plan.AccountPlan
import com.magickeyboard.plan.FreeLimits
import com.magickeyboard.profile.BusinessProfile
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Account and sync with Supabase (PRD 6.5, M7). Runs only from the manager app, never from the
 * keyboard, which makes no network calls. Calls are blocking; run them off the main thread.
 *
 * The publishable key is meant to ship in apps: Row Level Security limits every row to its owner.
 */
class SupabaseSync private constructor(context: Context) {
    private val repo = ManagerRepository.get(context)
    private val prefs = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val settings = SettingsSyncer(context.applicationContext)
    private val appContext = context.applicationContext

    /** A user-facing message in the app language. */
    private fun text(id: Int, vararg args: Any): String = AppLanguage.string(appContext, id, *args)

    /** Email of the signed-in account, or null. */
    fun signedInEmail(): String? = prefs.getString(KEY_EMAIL, null)?.takeIf { prefs.contains(KEY_REFRESH) }

    fun lastSyncAt(): Long? = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 }

    /** How the account signs in ("google" or "email"), or null when signed out. */
    fun signInMethod(): String? = prefs.getString(KEY_METHOD, null)?.takeIf { signedInEmail() != null }

    /**
     * Sends one email with a 6-digit code and a sign-in link; a new account is created on first
     * sign-in. The link returns to `scriptgo://login-callback` with a PKCE code ([completeCallback]).
     */
    fun sendCode(email: String) {
        val body = JSONObject().put("email", email.trim()).put("create_user", true)
            .put("code_challenge", Pkce.challenge(newVerifier())).put("code_challenge_method", "s256")
        request("POST", "/auth/v1/otp?redirect_to=" + enc(AuthCallback.REDIRECT_URL), body, auth = false)
    }

    /** The Supabase page that starts Google sign-in in the browser; it returns to [completeCallback]. */
    fun googleSignInUrl(): String =
        SupabaseConfig.URL + "/auth/v1/authorize?provider=google" +
            "&redirect_to=" + enc(AuthCallback.REDIRECT_URL) +
            "&code_challenge=" + Pkce.challenge(newVerifier()) + "&code_challenge_method=s256"

    /** Finishes a Google or email-link sign-in from the `scriptgo://login-callback` URL. */
    fun completeCallback(url: String) {
        when (val callback = AuthCallback.parse(url)) {
            is AuthCallback.Code -> {
                val verifier = prefs.getString(KEY_PKCE, null)
                    ?: throw SyncException(text(R.string.sg_err_old_link))
                val body = JSONObject().put("auth_code", callback.code).put("code_verifier", verifier)
                signIn(JSONObject(request("POST", "/auth/v1/token?grant_type=pkce", body, auth = false)))
                prefs.edit().remove(KEY_PKCE).apply()
            }
            is AuthCallback.Error -> throw SyncException(
                if (callback.message.contains("expired", ignoreCase = true) || callback.message.contains("invalid", ignoreCase = true))
                    text(R.string.sg_err_link_expired)
                else text(R.string.sg_err_sign_in, callback.message)
            )
            AuthCallback.Unknown -> throw SyncException(text(R.string.sg_err_unknown_link))
        }
    }

    private fun newVerifier(): String = Pkce.newVerifier(SecureRandom()).also { prefs.edit().putString(KEY_PKCE, it).apply() }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    /** Verifies the emailed [code] and stores the session. */
    fun verifyCode(email: String, code: String) {
        val body = JSONObject().put("type", "email").put("email", email.trim()).put("token", code.trim())
        signIn(JSONObject(request("POST", "/auth/v1/verify", body, auth = false)))
    }

    /** Signs in with email and password (a password is optional and set from Akun → Kata sandi). */
    fun signInWithPassword(email: String, password: String) {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val session = try {
            JSONObject(request("POST", "/auth/v1/token?grant_type=password", body, auth = false))
        } catch (e: SyncException) {
            throw if (e.errorCode == "invalid_credentials") SyncException(text(R.string.sg_err_wrong_password), errorCode = e.errorCode) else e
        }
        signIn(session)
        prefs.edit().putString(KEY_METHOD, "email").putBoolean(KEY_HAS_PASSWORD, true).apply()
    }

    /** Sets or changes the account's password; afterwards the user can sign in with it. */
    fun setPassword(password: String) {
        request("PUT", "/auth/v1/user", JSONObject().put("password", password), auth = true)
        prefs.edit().putBoolean(KEY_HAS_PASSWORD, true).apply()
    }

    /** True when a password was set on this phone or used to sign in (the server does not say). */
    fun hasPassword(): Boolean = prefs.getBoolean(KEY_HAS_PASSWORD, false)

    // region Passkeys (Supabase Auth, experimental). The ceremony itself runs in [PasskeyCeremony].

    /** A WebAuthn challenge: [optionsJson] goes to Credential Manager, [challengeId] back to the server. */
    class PasskeyChallenge(val challengeId: String, val optionsJson: String)

    /** One passkey on the account, for Akun → Passkey. */
    class Passkey(val id: String, val name: String, val createdAt: String, val lastUsedAt: String?)

    /** Registration options for the signed-in user. Blocking. */
    fun passkeyRegistrationOptions(): PasskeyChallenge =
        challenge(request("POST", "/auth/v1/passkeys/registration/options", JSONObject(), auth = true))

    /** Stores the new passkey created from [passkeyRegistrationOptions]. Blocking. */
    fun verifyPasskeyRegistration(challengeId: String, credentialJson: String) {
        val body = JSONObject().put("challenge_id", challengeId).put("credential", JSONObject(credentialJson))
        request("POST", "/auth/v1/passkeys/registration/verify", body, auth = true)
    }

    /** Sign-in options; the user picks the account in the passkey prompt (no email needed). Blocking. */
    fun passkeySignInOptions(): PasskeyChallenge =
        challenge(request("POST", "/auth/v1/passkeys/authentication/options", JSONObject(), auth = false))

    /** Signs in with the passkey assertion for [passkeySignInOptions]. Blocking. */
    fun signInWithPasskey(challengeId: String, credentialJson: String) {
        val body = JSONObject().put("challenge_id", challengeId).put("credential", JSONObject(credentialJson))
        signIn(JSONObject(request("POST", "/auth/v1/passkeys/authentication/verify", body, auth = false)))
        prefs.edit().putString(KEY_METHOD, "passkey").apply()
    }

    /** The account's passkeys, newest first. Blocking. */
    fun passkeys(): List<Passkey> {
        val body = request("GET", "/auth/v1/passkeys", null, auth = true).trim().ifEmpty { "[]" }
        val array = if (body.startsWith("[")) JSONArray(body) else JSONObject(body).optJSONArray("passkeys") ?: JSONArray()
        return (0 until array.length()).map { array.getJSONObject(it) }.map {
            Passkey(
                id = it.getString("id"),
                name = it.optString("friendly_name"),
                createdAt = it.optString("created_at"),
                lastUsedAt = it.optString("last_used_at").takeIf { used -> used.isNotEmpty() && !it.isNull("last_used_at") },
            )
        }.sortedByDescending { it.createdAt }
    }

    /** Removes a passkey from the account; it can no longer sign in. Blocking. */
    fun deletePasskey(id: String) {
        request("DELETE", "/auth/v1/passkeys/${enc(id)}", null, auth = true)
    }

    private fun challenge(body: String): PasskeyChallenge {
        val json = JSONObject(body)
        return PasskeyChallenge(json.getString("challenge_id"), json.getJSONObject("options").toString())
    }
    // endregion

    /**
     * Sends a message to the ScriptGo team from Akun → Pusat bantuan ([kind]: help, idea, bug).
     * Works signed out too; then [replyEmail] is where to answer. Blocking.
     */
    fun sendSupport(kind: String, message: String, replyEmail: String, appVersion: String, device: String, language: String) {
        val body = JSONObject()
            .put("kind", kind)
            .put("message", message.trim().take(4000))
            .put("reply_email", replyEmail.trim().take(254))
            .put("source", "android")
            .put("app_version", appVersion.take(40))
            .put("device", device.take(120))
            .put("language", language.take(10))
        val signedIn = signedInEmail() != null
        try {
            request("POST", "/rest/v1/support_messages", body, auth = signedIn, extraHeaders = mapOf("Prefer" to "return=minimal"))
        } catch (e: SyncException) {
            throw if (e.message.orEmpty().contains("rate_limited") || e.message.orEmpty().contains("Too many")) {
                SyncException(text(R.string.sg_help_too_many), e)
            } else e
        }
    }

    // region AI templates (Edge Function ai-template; docs/superpowers/specs/2026-09-28-ai-templates-design.md)
    /** How many AI uses are left: [daily] for Pro (resets at midnight WIB), else in total. */
    class AiQuota(val limit: Int, val remaining: Int, val daily: Boolean)

    class AiTemplate(val title: String, val shortcut: String, val body: String)

    /** The signed-in user's AI quota. Blocking. */
    fun aiQuota(): AiQuota = aiQuotaFrom(JSONObject(request("POST", "/rest/v1/rpc/ai_quota", JSONObject(), auth = true)))

    /** A new template from [description]; [fields] are the user's field keys (never values). Blocking. */
    fun aiCreate(description: String, fields: List<String>): Pair<AiTemplate, AiQuota> =
        ai(JSONObject().put("mode", "create").put("description", description.trim()).put("fields", JSONArray(fields)))

    /** [body] rewritten in [style] (polite, shorter, casual, spelling), keeping every variable. Blocking. */
    fun aiRewrite(style: String, title: String, body: String, fields: List<String>): Pair<AiTemplate, AiQuota> =
        ai(JSONObject().put("mode", "rewrite").put("style", style).put("title", title.trim()).put("body", body).put("fields", JSONArray(fields)))

    // One try only: the function already retries the model, and a second call would take another use.
    private fun ai(body: JSONObject): Pair<AiTemplate, AiQuota> {
        val json = JSONObject(requestOnce("POST", "/functions/v1/ai-template", body, auth = true, emptyMap(), AI_TIMEOUT_MS))
        val template = json.getJSONObject("template")
        return AiTemplate(template.getString("title"), template.getString("shortcut"), template.getString("body")) to
            aiQuotaFrom(json.getJSONObject("quota"))
    }

    /**
     * Reports an AI result as offensive, wrong, or otherwise bad (Google Play's AI content policy).
     * [input] is what was sent to the AI (the description or the template text). Blocking.
     */
    fun reportAi(mode: String, style: String, input: String, result: String, reason: String, note: String) {
        val body = JSONObject()
            .put("mode", mode).put("style", style).put("input", input.take(4000)).put("result", result.take(4000))
            .put("reason", reason).put("note", note.trim().take(500)).put("source", "android")
        request("POST", "/rest/v1/ai_reports", body, auth = true, extraHeaders = mapOf("Prefer" to "return=minimal"))
    }

    private fun aiQuotaFrom(json: JSONObject) =
        AiQuota(json.optInt("limit"), json.optInt("remaining"), json.optString("period") == "day")
    // endregion

    // region Paket (my_plan, PRD §9)
    /** The last [loadPlan] result, or null before the first one. */
    fun cachedPlan(): AccountPlan? = prefs.getString(KEY_PLAN, null)?.let { runCatching { planFrom(JSONObject(it)) }.getOrNull() }

    /** The account's plan (beta, trial, pro, or free), cached. Blocking. */
    fun loadPlan(): AccountPlan {
        val json = JSONObject(request("POST", "/rest/v1/rpc/my_plan", JSONObject(), auth = true))
        val plan = planFrom(json) ?: error(text(R.string.sg_error_generic))
        prefs.edit().putString(KEY_PLAN, json.toString()).apply()
        return plan
    }

    /** Whether the free limits are switched on (limits_active), cached; false until first loaded. */
    fun limitsActive(): Boolean = prefs.getBoolean(KEY_LIMITS_ACTIVE, false)

    /** Reads limits_active() (no account needed) and caches it. Blocking. */
    fun refreshLimitsActive(): Boolean {
        val active = request("POST", "/rest/v1/rpc/limits_active", JSONObject(), auth = false).trim() == "true"
        prefs.edit().putBoolean(KEY_LIMITS_ACTIVE, active).apply()
        return active
    }

    /** Whether the free limits (20 templates, 30 uses) apply now, from the cached plan and switch. */
    fun freeLimited(): Boolean =
        FreeLimits.limited(if (signedInEmail() != null) cachedPlan() else null, limitsActive(), java.time.Instant.now())

    private fun planFrom(json: JSONObject): AccountPlan? = AccountPlan.parse(
        json.optString("plan"),
        json.optString("trial_ends_at").takeIf { !json.isNull("trial_ends_at") },
        json.optString("pro_until").takeIf { !json.isNull("pro_until") },
    )
    // endregion

    // region Ajak teman
    /** The account's referral code and how many friends signed up with it and confirmed their email. */
    class Referral(val code: String, val joined: Int) {
        /** The link friends open to sign up; same as on scriptgo.id/konfirmasi and in the extension. */
        val link: String get() = "https://scriptgo.id/?ref=$code"
    }

    /** The last [loadReferral] result, or null before the first one. */
    fun cachedReferral(): Referral? =
        prefs.getString(KEY_REFERRAL_CODE, null)?.let { Referral(it, prefs.getInt(KEY_REFERRAL_JOINED, 0)) }

    /** The account's referral code (created on first use by my_referral) and friend count, cached. Blocking. */
    fun loadReferral(): Referral {
        val json = JSONObject(request("POST", "/rest/v1/rpc/my_referral", JSONObject(), auth = true))
        val code = json.optString("code")
        check(REFERRAL_CODE.matches(code)) { text(R.string.sg_error_generic) }
        val joined = json.optInt("joined").coerceAtLeast(0)
        prefs.edit().putString(KEY_REFERRAL_CODE, code).putInt(KEY_REFERRAL_JOINED, joined).apply()
        return Referral(code, joined)
    }
    // endregion

    /** The name on the account (user_metadata.full_name), cached; see [loadPersonal]. */
    fun fullName(): String = prefs.getString(KEY_FULL_NAME, "").orEmpty()

    /** Sign-in methods linked to the account ("google", "email"), cached; see [loadPersonal]. */
    fun linkedProviders(): List<String> = prefs.getString(KEY_PROVIDERS, "").orEmpty().split(',').filter { it.isNotEmpty() }

    /** Refreshes the name and linked sign-in methods from the server. Blocking. */
    fun loadPersonal() {
        val user = JSONObject(request("GET", "/auth/v1/user", null, auth = true))
        cacheUser(user)
    }

    /** Saves the name on the account. Blocking. */
    fun savePersonal(fullName: String) {
        val body = JSONObject().put("data", JSONObject().put("full_name", fullName.trim().take(120)))
        cacheUser(JSONObject(request("PUT", "/auth/v1/user", body, auth = true)))
    }

    private fun cacheUser(user: JSONObject) {
        val name = user.optJSONObject("user_metadata")?.let { it.optString("full_name").ifEmpty { it.optString("name") } }.orEmpty()
        val providers = user.optJSONObject("app_metadata")?.optJSONArray("providers")
            ?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty()
        prefs.edit().putString(KEY_FULL_NAME, name).putString(KEY_PROVIDERS, providers.joinToString(",")).apply()
    }

    /**
     * Keyboard and app settings follow the account. A fresh install (nothing synced yet) takes the
     * account's settings; otherwise a change on this phone since the last sync is uploaded (it wins
     * over a change elsewhere), and a newer copy on the server is applied here.
     */
    private fun syncSettings() {
        val local = settings.snapshot()
        val localPrint = settings.fingerprint(local)
        val lastPrint = prefs.getString(KEY_SETTINGS_PRINT, null)
        val knownAt = prefs.getLong(KEY_SETTINGS_AT, 0)
        val remote = JSONArray(request("GET", "/rest/v1/user_settings?select=data,updated_at", null, auth = true)).optJSONObject(0)
        val remoteAt = remote?.optLong("updated_at") ?: 0
        if (remote != null && remoteAt > knownAt && (lastPrint == null || localPrint == lastPrint)) {
            settings.apply(remote.optJSONObject("data") ?: JSONObject())
            prefs.edit().putString(KEY_SETTINGS_PRINT, settings.fingerprint(settings.snapshot())).putLong(KEY_SETTINGS_AT, remoteAt).apply()
        } else if (remote == null || localPrint != lastPrint) {
            val at = maxOf(System.currentTimeMillis(), remoteAt + 1)
            request(
                "POST", "/rest/v1/user_settings", JSONObject().put("data", local).put("updated_at", at), auth = true,
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
            )
            prefs.edit().putString(KEY_SETTINGS_PRINT, localPrint).putLong(KEY_SETTINGS_AT, at).apply()
        }
    }

    /**
     * Clipboard history follows the account only when it is kept without a time limit (the
     * keyboard's retention slider at "no limit"). Text clips only; images and files stay on the
     * phone. Everything is encrypted here before upload; see [ClipboardCrypto].
     */
    private fun syncClipboard() {
        val keyboardPrefs = appContext.prefs()
        val enabled = keyboardPrefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY) &&
            keyboardPrefs.getInt(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, Defaults.PREF_CLIPBOARD_HISTORY_RETENTION_TIME) > 120
        if (!enabled) return
        val userId = prefs.getString(KEY_USER_ID, null) ?: return
        val dao = ClipboardDao.getInstance(appContext) ?: return
        val crypto = ClipboardCrypto.forUser(appContext, userId) ?: return

        val local = dao.getAll().filter { it.filename == null && !it.text.isNullOrEmpty() }
            .map { ClipboardSync.Clip(crypto.idOf(it.text!!), it.text, it.isPinned, it.timeStamp) }
        val rows = JSONArray(request("GET", "/rest/v1/clipboard_items?select=id,payload,deleted,updated_at", null, auth = true))
        val remote = (0 until rows.length()).map { rows.getJSONObject(it) }.map { row ->
            val clip = crypto.decrypt(row.optString("payload"))?.let { runCatching { JSONObject(it) }.getOrNull() }?.let {
                ClipboardSync.Clip(row.getString("id"), it.optString("t"), it.optBoolean("p"), it.optLong("u"))
            }
            ClipboardSync.Remote(row.getString("id"), clip, row.optBoolean("deleted"), row.optLong("updated_at"))
        }
        val synced = prefs.getStringSet(KEY_CLIPBOARD_SYNCED, emptySet()).orEmpty()
        val plan = ClipboardSync.plan(local, remote, synced)

        val uploads = JSONArray()
        for (clip in plan.upload) {
            val payload = JSONObject().put("t", clip.text).put("p", clip.pinned).put("u", clip.updatedAt).toString()
            uploads.put(JSONObject().put("id", clip.id).put("payload", crypto.encrypt(payload)).put("updated_at", clip.updatedAt).put("deleted", false))
        }
        val now = System.currentTimeMillis()
        for (id in plan.deleteRemote) uploads.put(JSONObject().put("id", id).put("payload", "").put("updated_at", now).put("deleted", true))
        if (uploads.length() > 0) {
            request(
                "POST", "/rest/v1/clipboard_items?on_conflict=user_id,id", uploads, auth = true,
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
            )
        }
        for (clip in plan.saveLocal) {
            val existing = dao.getAll().firstOrNull { it.text == clip.text && it.filename == null }
            when {
                existing == null -> dao.insertNewEntry(clip.updatedAt, clip.pinned, clip.text, null, null, null)
                existing.isPinned != clip.pinned -> dao.togglePinned(existing.id)
            }
        }
        for (id in plan.deleteLocal) {
            val index = dao.getAll().indexOfFirst { it.filename == null && it.text != null && crypto.idOf(it.text) == id }
            if (index >= 0) dao.deleteClipAt(index)
        }
        prefs.edit().putStringSet(KEY_CLIPBOARD_SYNCED, ClipboardSync.syncedAfter(local, plan)).apply()
    }

    /** Stores a new session; a different account than before starts sync from scratch. */
    private fun signIn(session: JSONObject) {
        val previousUser = prefs.getString(KEY_USER_ID, null)
        storeSession(session)
        if (previousUser != null && previousUser != prefs.getString(KEY_USER_ID, null)) {
            repo.resetSync()
            prefs.edit().remove(KEY_PROFILE).remove(KEY_SETTINGS_PRINT).remove(KEY_SETTINGS_AT).remove(KEY_CLIPBOARD_SYNCED).apply()
        }
    }

    /** The business profile saved on this phone, shown while offline. */
    fun cachedProfile(): BusinessProfile? = prefs.getString(KEY_PROFILE, null)?.let { runCatching { profileFrom(JSONObject(it)) }.getOrNull() }

    /** Downloads the business profile; null when the user has not filled it in yet. */
    fun loadProfile(): BusinessProfile? {
        val rows = JSONArray(request("GET", "/rest/v1/business_profiles?select=*", null, auth = true))
        val row = if (rows.length() > 0) rows.getJSONObject(0) else null
        // A profile filled in on this phone before signing in wins until it is uploaded.
        if (prefs.getBoolean(KEY_PROFILE_PENDING, false)) return cachedProfile()
        prefs.edit().apply { if (row == null) remove(KEY_PROFILE) else putString(KEY_PROFILE, row.toString()) }.apply()
        return row?.let { profileFrom(it) }
    }

    /**
     * Saves the business profile on this phone and, when signed in, on the server (insert or update).
     * Filled in during onboarding before any account exists; [uploadPendingProfile] sends it after sign-in.
     */
    fun saveProfile(profile: BusinessProfile) {
        val row = profileRow(profile.cleaned())
        prefs.edit().putString(KEY_PROFILE, row.toString()).putBoolean(KEY_PROFILE_PENDING, true).apply()
        if (signedInEmail() != null) uploadPendingProfile()
    }

    /** Uploads the profile saved on this phone if the server does not have it yet. Blocking. */
    fun uploadPendingProfile() {
        if (!prefs.getBoolean(KEY_PROFILE_PENDING, false)) return
        val row = prefs.getString(KEY_PROFILE, null)?.let { JSONObject(it) } ?: return
        row.put("user_id", prefs.getString(KEY_USER_ID, null) ?: throw SyncException(text(R.string.sg_err_not_signed_in)))
        request(
            "POST", "/rest/v1/business_profiles", row, auth = true,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
        prefs.edit().putString(KEY_PROFILE, row.toString()).putBoolean(KEY_PROFILE_PENDING, false).apply()
    }

    private fun profileRow(clean: BusinessProfile): JSONObject = JSONObject()
        .put("business_name", clean.businessName)
        .put("business_type", clean.businessType ?: JSONObject.NULL)
        .put("business_type_other", clean.businessTypeOther)
        .put("city", clean.city)
        .put("province", clean.province)
        .put("role", clean.role ?: JSONObject.NULL)
        .put("channels", JSONArray(clean.channels))
        .put("team_size", clean.teamSize ?: JSONObject.NULL)
        .put("chats_per_day", clean.chatsPerDay ?: JSONObject.NULL)
        .put("monthly_revenue", clean.monthlyRevenue ?: JSONObject.NULL)
        .put("whatsapp", clean.whatsapp)
        .put("whatsapp_opt_in", clean.whatsappOptIn)

    /** True until every business profile question is answered (onboarding cannot be finished without it). */
    fun needsProfilePrompt(): Boolean = cachedProfile()?.isComplete() != true

    private fun profileFrom(json: JSONObject): BusinessProfile {
        fun code(name: String) = json.optString(name).takeIf { it.isNotEmpty() && !json.isNull(name) }
        val channels = json.optJSONArray("channels")?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty()
        return BusinessProfile(
            businessName = json.optString("business_name"),
            businessType = code("business_type"),
            businessTypeOther = json.optString("business_type_other"),
            city = json.optString("city"),
            province = json.optString("province"),
            role = code("role"),
            channels = channels,
            teamSize = code("team_size"),
            chatsPerDay = code("chats_per_day"),
            monthlyRevenue = code("monthly_revenue"),
            whatsapp = json.optString("whatsapp"),
            whatsappOptIn = json.optBoolean("whatsapp_opt_in"),
        )
    }

    /**
     * Deletes the account and everything synced to it from the server (Google Play requires in-app
     * account deletion), then signs out. Data on this phone stays.
     */
    fun deleteAccount() {
        request("POST", "/rest/v1/rpc/delete_account", JSONObject(), auth = true)
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES_AT).remove(KEY_USER_ID).remove(KEY_LAST_SYNC)
            .remove(KEY_PROFILE).remove(KEY_METHOD).remove(KEY_HAS_PASSWORD).remove(KEY_FULL_NAME).remove(KEY_PROVIDERS)
            .remove(KEY_REFERRAL_CODE).remove(KEY_REFERRAL_JOINED).remove(KEY_PLAN)
            .remove(KEY_SETTINGS_PRINT).remove(KEY_SETTINGS_AT).remove(KEY_CLIPBOARD_SYNCED).apply()
        repo.resetSync()
    }

    fun signOut() {
        runCatching { request("POST", "/auth/v1/logout", null, auth = true) }
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES_AT).remove(KEY_PROFILE).remove(KEY_METHOD)
            .remove(KEY_HAS_PASSWORD).remove(KEY_FULL_NAME).remove(KEY_PROVIDERS).remove(KEY_REFERRAL_CODE).remove(KEY_REFERRAL_JOINED).remove(KEY_PLAN).apply()
    }

    /**
     * Uploads local changes, then downloads changes from other devices. Returns how many local
     * records were replaced by newer versions from the server.
     */
    fun syncNow(): Int {
        check(signedInEmail() != null) { text(R.string.sg_err_not_signed_in) }
        // Sync is for Pro (PRD §9; the server refuses it too, can_sync()). Read the plan first, so a
        // trial that just started is not refused with an old cached plan.
        runCatching { loadPlan() }
        check(!freeLimited()) { text(R.string.sg_sync_pro_only) }
        var pulled = 0
        for (table in SyncTable.entries) push(table)
        for (table in SyncTable.entries) pulled += pull(table)
        // Copies from earlier installs only meet after a pull; upload their deletion right away.
        if (repo.removeDuplicateTemplates() > 0) push(SyncTable.TEMPLATES)
        runCatching { syncSettings() } // settings never block templates and contacts
        runCatching { syncClipboard() } // the clipboard never blocks templates and contacts
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
        return pulled
    }

    private fun push(table: SyncTable) {
        val rows = repo.dirtyRows(table)
        for (batch in rows.chunked(BATCH)) {
            request(
                "POST", "/rest/v1/${table.local}", JSONArray(batch), auth = true,
                extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
            )
            batch.forEach { repo.markClean(table, it.getString("id"), it.getLong("updated_at")) }
        }
    }

    private fun pull(table: SyncTable): Int {
        var applied = 0
        while (true) {
            val cursor = repo.pullCursor(table)
            val filter = if (cursor == null) "" else "&synced_at=gt.${URLEncoder.encode(cursor, "UTF-8")}"
            val rows = JSONArray(
                request("GET", "/rest/v1/${table.local}?select=*&order=synced_at.asc&limit=$BATCH$filter", null, auth = true)
            )
            val list = (0 until rows.length()).map { rows.getJSONObject(it) }
            if (list.isEmpty()) return applied
            applied += repo.applyRemote(table, list)
            repo.setPullCursor(table, list.last().getString("synced_at"))
            if (list.size < BATCH) return applied
        }
    }

    private fun storeSession(session: JSONObject) {
        val user = session.optJSONObject("user")
        val editor = prefs.edit()
            .putString(KEY_ACCESS, session.getString("access_token"))
            .putString(KEY_REFRESH, session.getString("refresh_token"))
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + session.optLong("expires_in", 3600) * 1000)
        if (user != null) {
            editor.putString(KEY_USER_ID, user.optString("id"))
            editor.putString(KEY_EMAIL, user.optString("email"))
            user.optJSONObject("app_metadata")?.optString("provider")?.takeIf { it.isNotEmpty() }?.let { editor.putString(KEY_METHOD, it) }
        }
        editor.apply()
    }

    /** A valid access token, refreshed shortly before it expires. */
    private fun accessToken(): String {
        val refresh = prefs.getString(KEY_REFRESH, null) ?: throw SyncException(text(R.string.sg_err_not_signed_in))
        if (System.currentTimeMillis() > prefs.getLong(KEY_EXPIRES_AT, 0) - 60_000) {
            val body = JSONObject().put("refresh_token", refresh)
            storeSession(JSONObject(request("POST", "/auth/v1/token?grant_type=refresh_token", body, auth = false)))
        }
        return prefs.getString(KEY_ACCESS, null) ?: throw SyncException(text(R.string.sg_err_not_signed_in))
    }

    /**
     * A request with one retry when the server or the gateway briefly fails (5xx, Cloudflare 52x,
     * dropped connection): those requests never reached Supabase, so trying again is safe.
     */
    private fun request(
        method: String,
        path: String,
        body: Any?,
        auth: Boolean,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String = try {
        requestOnce(method, path, body, auth, extraHeaders)
    } catch (e: SyncException) {
        if (!e.retryable) throw e
        Thread.sleep(RETRY_DELAY_MS)
        requestOnce(method, path, body, auth, extraHeaders)
    }

    private fun requestOnce(
        method: String,
        path: String,
        body: Any?,
        auth: Boolean,
        extraHeaders: Map<String, String>,
        readTimeoutMs: Int = 30_000,
    ): String {
        val connection = URL(SupabaseConfig.URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            if (auth) connection.setRequestProperty("Authorization", "Bearer ${accessToken()}")
            extraHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) {
                throw SyncException(errorMessage(code, text), errorCode = errorCodeOf(text), retryable = code in RETRY_CODES)
            }
            return text
        } catch (e: IOException) {
            throw SyncException(text(R.string.sg_err_offline), e, retryable = true)
        } finally {
            connection.disconnect()
        }
    }

    private fun errorCodeOf(body: String): String? =
        runCatching { JSONObject(body).optString("error_code") }.getOrNull()?.ifEmpty { null }

    private fun errorMessage(code: Int, body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val errorCode = json?.optString("error_code").orEmpty()
        return when {
            // Errors of the ai-template Edge Function ({"error": "..."}).
            json?.optString("error") == "quota_exceeded" -> text(R.string.sg_ai_err_quota)
            json?.optString("error") == "ai_failed" -> text(R.string.sg_ai_err_failed)
            json?.optString("error") == "ai_not_configured" -> text(R.string.sg_ai_err_off)
            errorCode == "otp_expired" || errorCode == "invalid_credentials" -> text(R.string.sg_err_code)
            errorCode == "passkey_disabled" -> text(R.string.sg_err_passkey_off)
            errorCode == "webauthn_credential_not_found" -> text(R.string.sg_err_passkey_unknown)
            errorCode == "webauthn_credential_exists" -> text(R.string.sg_err_passkey_exists)
            errorCode == "too_many_passkeys" -> text(R.string.sg_err_passkey_too_many)
            errorCode.startsWith("webauthn_") -> text(R.string.sg_err_passkey_failed)
            errorCode == "weak_password" -> text(R.string.sg_err_weak_password)
            errorCode == "same_password" -> text(R.string.sg_err_same_password)
            errorCode == "reauthentication_needed" -> text(R.string.sg_err_reauth)
            errorCode == "over_email_send_rate_limit" || code == 429 -> text(R.string.sg_err_rate_limit)
            errorCode == "flow_state_not_found" || errorCode == "flow_state_expired" || errorCode == "bad_code_verifier" ->
                text(R.string.sg_err_link_expired)
            errorCode == "provider_disabled" || errorCode == "validation_failed" && body.contains("provider") ->
                text(R.string.sg_err_google_off)
            code == 401 || code == 403 -> text(R.string.sg_err_session)
            code >= 500 -> text(R.string.sg_err_server)
            else -> text(R.string.sg_err_failed_code, code, json?.optString("msg")?.ifEmpty { null } ?: json?.optString("message") ?: body.take(200))
        }
    }

    companion object {
        private const val BATCH = 500
        private const val RETRY_DELAY_MS = 1_500L
        private const val AI_TIMEOUT_MS = 75_000
        private val RETRY_CODES = setOf(500, 502, 503, 504, 520, 521, 522, 523, 524)
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_LAST_SYNC = "last_sync_at"
        private const val KEY_PKCE = "pkce_verifier"
        private const val KEY_METHOD = "sign_in_method"
        private const val KEY_PROFILE = "business_profile"
        private const val KEY_PROFILE_PENDING = "business_profile_pending"
        private const val KEY_HAS_PASSWORD = "has_password"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_PROVIDERS = "providers"
        private const val KEY_REFERRAL_CODE = "referral_code"
        private const val KEY_REFERRAL_JOINED = "referral_joined"
        private const val KEY_PLAN = "account_plan"
        private const val KEY_LIMITS_ACTIVE = "limits_active"
        private val REFERRAL_CODE = Regex("^[a-z0-9]{6,12}$")
        private const val KEY_SETTINGS_PRINT = "settings_fingerprint"
        private const val KEY_SETTINGS_AT = "settings_updated_at"
        private const val KEY_CLIPBOARD_SYNCED = "clipboard_synced_ids"

        @Volatile
        private var instance: SupabaseSync? = null

        fun get(context: Context): SupabaseSync =
            instance ?: synchronized(this) { instance ?: SupabaseSync(context).also { instance = it } }
    }
}

class SyncException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String? = null,
    /** True when trying the same request again may succeed (server hiccup, dropped connection). */
    val retryable: Boolean = false,
) : Exception(message, cause)

/** Project `magic-keyboard` (Singapore). The publishable key is safe to ship; RLS protects the data. */
object SupabaseConfig {
    const val URL = "https://hsanqncbfurjhnshdpnc.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_8YEhzjHcC9OOFN0n7B1vUg_E7540P_A"
}
