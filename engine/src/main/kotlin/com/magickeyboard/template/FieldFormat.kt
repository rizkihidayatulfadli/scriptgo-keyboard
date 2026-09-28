package com.magickeyboard.template

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Custom field types; [key] is the stored identifier used in docs/template-syntax.md. */
enum class FieldType(val key: String) {
    TEXT("text"),
    LONGTEXT("longtext"),
    NUMBER("number"),
    CURRENCY("currency"),
    DATE("date"),
    CHOICE("choice"),
    PHONE("phone"),
    EMAIL("email"),
    URL("url"),
    BOOLEAN("boolean"),
    MULTICHOICE("multichoice");

    companion object {
        fun fromKey(key: String): FieldType? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Currencies for [FieldType.CURRENCY]. A field stores its [code] as its only option; no option or an
 * unknown code means [IDR]. [symbol] is written before the amount; [cents] allows up to two decimals.
 */
enum class FieldCurrency(val code: String, val symbol: String, val cents: Boolean) {
    IDR("IDR", "Rp", false),
    USD("USD", "US$", true),
    SGD("SGD", "S$", true),
    MYR("MYR", "RM", true),
    EUR("EUR", "€", true),
    AUD("AUD", "A$", true),
    JPY("JPY", "¥", false),
    CNY("CNY", "CN¥", true),
    SAR("SAR", "SAR ", true);

    companion object {
        val DEFAULT = IDR

        fun fromCode(code: String?): FieldCurrency = entries.firstOrNull { it.code == code } ?: DEFAULT

        /** The currency of a currency field, from its stored options. */
        fun fromOptions(options: List<String>): FieldCurrency = fromCode(options.firstOrNull())
    }
}

private val NUMBER = Regex("(-?)(\\d+)(?:\\.(\\d+))?")
private val INTEGER = Regex("(-?)(\\d+)")
private val AMOUNT_WITH_CENTS = Regex("(-?)(\\d+)(?:\\.(\\d{1,2}))?")

/**
 * Turns a stored field value into template text. Empty input gives empty output, and a value
 * that is invalid for its type is returned unchanged. [currency] (a [FieldCurrency] code) is only
 * used by [FieldType.CURRENCY]. Never throws.
 */
fun formatFieldValue(type: FieldType, raw: String, currency: String? = null): String {
    if (raw.isEmpty()) return ""
    return when (type) {
        FieldType.NUMBER -> NUMBER.matchEntire(raw)?.let { m ->
            val (sign, whole, fraction) = m.destructured
            sign + groupThousands(whole) + if (fraction.isEmpty()) "" else ",$fraction"
        } ?: raw
        FieldType.CURRENCY -> formatCurrency(raw, FieldCurrency.fromCode(currency))
        FieldType.DATE -> try {
            IndonesianDates.longDate(LocalDate.parse(raw))
        } catch (_: DateTimeParseException) {
            raw
        }
        FieldType.PHONE -> if (raw.startsWith("+62")) "0" + raw.substring(3) else raw
        FieldType.BOOLEAN -> when (raw) {
            "true" -> "Ya"
            "false" -> "Tidak"
            else -> raw
        }
        FieldType.TEXT, FieldType.LONGTEXT, FieldType.CHOICE,
        FieldType.EMAIL, FieldType.URL, FieldType.MULTICHOICE -> raw
    }
}

/** Formats a list value, as stored by [FieldType.MULTICHOICE]. */
@Suppress("UNUSED_PARAMETER")
fun formatFieldValue(type: FieldType, raw: List<String>): String = raw.joinToString(", ")

private fun formatCurrency(raw: String, currency: FieldCurrency): String {
    val match = (if (currency.cents) AMOUNT_WITH_CENTS else INTEGER).matchEntire(raw) ?: return raw
    val sign = match.groupValues[1]
    val whole = match.groupValues[2]
    val cents = match.groupValues.getOrNull(3).orEmpty()
    return sign + currency.symbol + groupThousands(whole) + if (cents.isEmpty()) "" else "," + cents.padEnd(2, '0')
}

private fun groupThousands(digits: String): String =
    digits.reversed().chunked(3).joinToString(".").reversed()

internal object IndonesianDates {
    private val MONTHS = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember",
    )

    fun monthName(month: Int): String = MONTHS[month - 1]

    fun dayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Senin"
        DayOfWeek.TUESDAY -> "Selasa"
        DayOfWeek.WEDNESDAY -> "Rabu"
        DayOfWeek.THURSDAY -> "Kamis"
        DayOfWeek.FRIDAY -> "Jumat"
        DayOfWeek.SATURDAY -> "Sabtu"
        DayOfWeek.SUNDAY -> "Minggu"
    }

    fun longDate(date: LocalDate): String = "${date.dayOfMonth} ${monthName(date.monthValue)} ${date.year}"
}
