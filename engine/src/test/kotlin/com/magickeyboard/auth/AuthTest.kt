package com.magickeyboard.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class PkceTest {
    @Test
    fun `challenge matches RFC 7636 example`() {
        // RFC 7636, appendix B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun `base64url matches the JDK encoder for every padding length`() {
        for (size in 0..40) {
            val bytes = ByteArray(size) { (it * 37 + size).toByte() }
            assertEquals(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), Pkce.base64Url(bytes))
        }
    }

    @Test
    fun `verifier is 64 url-safe characters and random`() {
        val a = Pkce.newVerifier(Random(1))
        val b = Pkce.newVerifier(Random(2))
        assertEquals(64, a.length)
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' }, a)
        assertNotEquals(a, b)
    }
}

class AuthCallbackTest {
    @Test
    fun `code in the query`() {
        assertEquals(AuthCallback.Code("abc-123"), AuthCallback.parse("scriptgo://login-callback?code=abc-123"))
    }

    @Test
    fun `error in the fragment is decoded`() {
        assertEquals(
            AuthCallback.Error("Email link is invalid or has expired"),
            AuthCallback.parse("scriptgo://login-callback#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired"),
        )
    }

    @Test
    fun `error in the query without a description falls back to the code`() {
        assertEquals(AuthCallback.Error("access_denied"), AuthCallback.parse("scriptgo://login-callback?error=access_denied"))
    }

    @Test
    fun `other links are ignored`() {
        assertEquals(AuthCallback.Unknown, AuthCallback.parse("scriptgo://login-callback"))
        assertEquals(AuthCallback.Unknown, AuthCallback.parse("https://example.com/?code=x"))
    }
}
