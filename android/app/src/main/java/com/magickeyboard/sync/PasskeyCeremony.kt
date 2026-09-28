package com.magickeyboard.sync

import android.content.Context
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.magickeyboard.manager.AppLanguage
import helium314.keyboard.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Passkeys on the phone: Android's Credential Manager runs the WebAuthn ceremony (fingerprint,
 * face, or screen lock) and [SupabaseSync] talks to Supabase Auth. The relying party is
 * scriptgo.id, so the app must be listed in https://scriptgo.id/.well-known/assetlinks.json (see
 * docs/auth-setup.md). [context] must be an Activity, which the passkey prompt is shown over.
 */
object PasskeyCeremony {
    /** Passkeys need Android 9 or newer. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** Thrown when the user closed the prompt; callers show nothing. */
    class Cancelled : Exception()

    /** Creates a passkey for the signed-in account. */
    suspend fun register(context: Context, sync: SupabaseSync) {
        val challenge = withContext(Dispatchers.IO) { sync.passkeyRegistrationOptions() }
        val response = try {
            CredentialManager.create(context).createCredential(context, CreatePublicKeyCredentialRequest(challenge.optionsJson))
        } catch (_: CreateCredentialCancellationException) {
            throw Cancelled()
        } catch (e: CreateCredentialException) {
            throw SyncException(AppLanguage.string(context, R.string.sg_err_passkey_failed), e)
        }
        val json = (response as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
            ?: throw SyncException(AppLanguage.string(context, R.string.sg_err_passkey_failed))
        withContext(Dispatchers.IO) { sync.verifyPasskeyRegistration(challenge.challengeId, json) }
    }

    /** Signs in with a passkey saved on this phone or in the user's password manager. */
    suspend fun signIn(context: Context, sync: SupabaseSync) {
        val challenge = withContext(Dispatchers.IO) { sync.passkeySignInOptions() }
        val result = try {
            CredentialManager.create(context).getCredential(
                context,
                GetCredentialRequest(listOf(GetPublicKeyCredentialOption(challenge.optionsJson))),
            )
        } catch (_: GetCredentialCancellationException) {
            throw Cancelled()
        } catch (e: NoCredentialException) {
            throw SyncException(AppLanguage.string(context, R.string.sg_err_passkey_none), e)
        } catch (e: GetCredentialException) {
            throw SyncException(AppLanguage.string(context, R.string.sg_err_passkey_failed), e)
        }
        val json = (result.credential as? PublicKeyCredential)?.authenticationResponseJson
            ?: throw SyncException(AppLanguage.string(context, R.string.sg_err_passkey_failed))
        withContext(Dispatchers.IO) { sync.signInWithPasskey(challenge.challengeId, json) }
    }
}
