package com.magickeyboard.sync

import android.content.Context
import android.util.Base64
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.android.gms.tasks.Tasks
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The key that locks clipboard history before it leaves the phone. It is made on the phone and
 * kept in Google's Block Store (backed up end-to-end encrypted with the phone's screen lock, and
 * kept across uninstall when Google backup is on), never sent to ScriptGo's server, so the server
 * only ever holds ciphertext. Blocking calls; run off the main thread.
 */
internal class ClipboardCrypto private constructor(private val key: ByteArray) {
    /** Stable id for a clip, an HMAC of its text: equal texts match without revealing them. */
    fun idOf(text: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(("clip:$text").toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    }

    /** AES-256-GCM with a random 12-byte nonce; base64 of nonce + ciphertext. */
    fun encrypt(plain: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    /** The plaintext, or null when the data was locked with another key or was tampered with. */
    fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes, 0, 12))
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()

    companion object {
        private const val TIMEOUT_SECONDS = 20L

        /**
         * The key for [userId], made and stored in Block Store the first time. Null when Block
         * Store is unavailable (no Google Play services); then clipboard history is not synced.
         */
        fun forUser(context: Context, userId: String): ClipboardCrypto? = runCatching {
            val client = Blockstore.getClient(context)
            val label = "clipboard_key_$userId"
            val existing = Tasks.await(
                client.retrieveBytes(RetrieveBytesRequest.Builder().setKeys(listOf(label)).build()),
                TIMEOUT_SECONDS, TimeUnit.SECONDS,
            ).blockstoreDataMap[label]?.bytes
            val key = existing?.takeIf { it.size == 32 } ?: ByteArray(32).also { fresh ->
                SecureRandom().nextBytes(fresh)
                Tasks.await(
                    client.storeBytes(StoreBytesData.Builder().setKey(label).setBytes(fresh).setShouldBackupToCloud(true).build()),
                    TIMEOUT_SECONDS, TimeUnit.SECONDS,
                )
            }
            ClipboardCrypto(key)
        }.getOrNull()
    }
}
