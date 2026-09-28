package com.magickeyboard.contacts

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

/** Phone numbers are stored and compared as E.164; raw user input is never compared directly. */
object PhoneNumbers {
    const val DEFAULT_REGION = "ID"

    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    /**
     * Returns [raw] as E.164 (`+6281234567890`), or null when it is not a possible phone number.
     * Accepts local (`0812-3456-7890`), international (`+62 812…`) and bare country code (`62812…`) forms.
     */
    fun normalize(raw: String, defaultRegion: String = DEFAULT_REGION): String? {
        val cleaned = raw.trim()
        if (cleaned.count { it.isDigit() } < 6) return null
        return try {
            val number = util.parse(withPlusIfCountryCode(cleaned, defaultRegion), defaultRegion)
            if (util.isPossibleNumber(number)) util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164) else null
        } catch (_: NumberParseException) {
            null
        }
    }

    /**
     * Indonesian exports often write `62812…` without the plus sign. Treat a leading country code as
     * international when the number does not start with the national prefix `0`.
     */
    private fun withPlusIfCountryCode(raw: String, region: String): String {
        if (raw.startsWith("+") || raw.startsWith("0")) return raw
        val countryCode = util.getCountryCodeForRegion(region).toString()
        val digits = raw.filter { it.isDigit() }
        return if (digits.startsWith(countryCode)) "+$digits" else raw
    }
}
