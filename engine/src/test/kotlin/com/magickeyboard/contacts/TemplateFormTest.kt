package com.magickeyboard.contacts

import com.magickeyboard.template.FieldType
import com.magickeyboard.template.TemplateParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class TemplateFormTest {
    private val parsed = TemplateParser.parse(
        "Halo {nama|Kak}, total {total} ke {kota} via {kurir:JNE,J&T}. WA {nomor}. Catatan: {catatan}"
    )
    private val fieldTypes = mapOf("total" to FieldType.CURRENCY, "kota" to FieldType.TEXT)
    private val budi = ContactData("Budi", listOf("+6281234567890"), mapOf("kota" to "Bandung", "total" to ""))
    private val now = LocalDateTime.of(2026, 9, 26, 10, 0)

    @Test
    fun `prefills from the contact, then defaults and options`() {
        assertEquals(
            mapOf("nama" to "Budi", "total" to "", "kota" to "Bandung", "kurir" to "JNE", "nomor" to "081234567890", "catatan" to ""),
            TemplateForm.prefill(parsed.variables, budi, fieldTypes),
        )
        assertEquals(
            mapOf("nama" to "Kak", "total" to "", "kota" to "", "kurir" to "JNE", "nomor" to "", "catatan" to ""),
            TemplateForm.prefill(parsed.variables, null, fieldTypes),
        )
    }

    @Test
    fun `renders raw field values with their type format`() {
        val values = TemplateForm.prefill(parsed.variables, budi, fieldTypes) + mapOf("total" to "150000", "catatan" to "cepat ya")
        assertEquals(
            "Halo Budi, total Rp150.000 ke Bandung via JNE. WA 081234567890. Catatan: cepat ya",
            TemplateForm.render(parsed, values, fieldTypes, now).text,
        )
    }

    @Test
    fun `formats currency fields in their own currency`() {
        val values = TemplateForm.prefill(parsed.variables, budi, fieldTypes) + mapOf("total" to "1500.5")
        assertEquals(
            "Halo Budi, total US$1.500,50 ke Bandung via JNE. WA 081234567890. Catatan: ",
            TemplateForm.render(parsed, values, fieldTypes, now, currencies = mapOf("total" to "USD")).text,
        )
    }

    @Test
    fun `marks values that come from the fallback because the contact has none`() {
        val template = TemplateParser.parse("{nama|Kak} {kota|kotamu} {total|0} {catatan|-} {nomor}")
        // Budi has a name and a city but no total; catatan is not a field, so its default is just a default.
        assertEquals(setOf("total"), TemplateForm.fallbackKeys(template.variables, budi, fieldTypes))
        assertEquals(setOf("nama", "kota", "total"), TemplateForm.fallbackKeys(template.variables, ContactData(""), fieldTypes))
        assertEquals(emptySet<String>(), TemplateForm.fallbackKeys(template.variables, null, fieldTypes))
    }

    @Test
    fun `saves back only changed custom fields`() {
        val values = mapOf("nama" to "Pak Budi", "total" to "150000", "kota" to "Bandung", "catatan" to "x", "kurir" to "J&T")
        assertEquals(mapOf("total" to "150000"), TemplateForm.changedFields(values, budi, fieldTypes))
    }

    @Test
    fun `uses a field's fallback when neither the contact nor the template has a value`() {
        val template = TemplateParser.parse("{kota} {total|0} {kurir:JNE,J&T} {nama}")
        val types = fieldTypes + ("kurir" to FieldType.CHOICE)
        val fallbacks = mapOf("kota" to "Jakarta", "total" to "5000", "kurir" to "SiCepat")
        assertEquals(
            mapOf("kota" to "Jakarta", "total" to "0", "kurir" to "JNE", "nama" to ""),
            TemplateForm.prefill(template.variables, null, types, fallbacks),
        )
        assertEquals("Bandung", TemplateForm.prefill(template.variables, budi, types, fallbacks)["kota"])
        // Only kota comes from the field's fallback; total from the template's, kurir from its options.
        assertEquals(setOf("kota", "total"), TemplateForm.fallbackKeys(template.variables, ContactData("Sari"), types, fallbacks))
        assertEquals(setOf("total"), TemplateForm.fallbackKeys(template.variables, budi, types, fallbacks))
    }

    @Test
    fun `does not save a field's fallback to a contact without a value`() {
        val fallbacks = mapOf("kota" to "Jakarta", "total" to "0")
        val sari = ContactData("Sari", fields = mapOf("total" to "5000"))
        assertEquals(emptyMap<String, String>(), TemplateForm.changedFields(mapOf("kota" to "Jakarta"), sari, fieldTypes, fallbacks))
        assertEquals(mapOf("kota" to "Solo"), TemplateForm.changedFields(mapOf("kota" to "Solo"), sari, fieldTypes, fallbacks))
        // A contact that has a value keeps getting what the user picked, even if it equals the fallback.
        assertEquals(mapOf("total" to "0"), TemplateForm.changedFields(mapOf("total" to "0"), sari, fieldTypes, fallbacks))
    }
}
