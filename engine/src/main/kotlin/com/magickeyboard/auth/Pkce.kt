package com.magickeyboard.auth

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Random

/**
 * PKCE (RFC 7636) for sign-ins that leave the app: Google in the browser and the link in the
 * sign-in email. The app keeps the verifier; only its hash travels, so another app that catches
 * the `scriptgo://` callback cannot exchange the code.
 */
object Pkce {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** A 64-character verifier; pass a `SecureRandom` in production. */
    fun newVerifier(random: Random): String = buildString { repeat(64) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** The S256 challenge: base64url(SHA-256(verifier)) without padding. */
    fun challenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    /** Unpadded base64url; java.util.Base64 needs Android 8 and the app supports Android 5. */
    internal fun base64Url(bytes: ByteArray): String = buildString {
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
            append(ALPHABET[b0 shr 2])
            append(ALPHABET[((b0 and 0x03) shl 4) or (if (b1 < 0) 0 else b1 shr 4)])
            if (b1 >= 0) append(ALPHABET[((b1 and 0x0F) shl 2) or (if (b2 < 0) 0 else b2 shr 6)])
            if (b2 >= 0) append(ALPHABET[b2 and 0x3F])
            i += 3
        }
    }
}

/** What Supabase Auth sent back to `scriptgo://login-callback`. */
sealed interface AuthCallback {
    /** Exchange with the stored verifier (`/auth/v1/token?grant_type=pkce`). */
    data class Code(val code: String) : AuthCallback
    data class Error(val message: String) : AuthCallback
    data object Unknown : AuthCallback

    companion object {
        const val SCHEME = "scriptgo"
        const val HOST = "login-callback"
        const val REDIRECT_URL = "$SCHEME://$HOST"

        /** Reads the code or error from the query or the fragment (Auth errors arrive in either). */
        fun parse(url: String): AuthCallback {
            val uri = runCatching { URI(url) }.getOrNull() ?: return Unknown
            if (uri.scheme != SCHEME || uri.host != HOST) return Unknown
            val params = params(uri.rawQuery) + params(uri.rawFragment)
            params["code"]?.takeIf { it.isNotBlank() }?.let { return Code(it) }
            val error = params["error_description"]?.takeIf { it.isNotBlank() } ?: params["error"]
            return if (error.isNullOrBlank()) Unknown else Error(error)
        }

        private fun params(raw: String?): Map<String, String> =
            raw.orEmpty().split('&').filter { '=' in it }.associate {
                val (key, value) = it.split('=', limit = 2)
                // The Charset overload needs Android 13; the String one works everywhere.
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }
    }
}
