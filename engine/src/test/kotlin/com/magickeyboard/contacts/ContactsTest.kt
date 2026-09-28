package com.magickeyboard.contacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PhoneNumbersTest {
    @Test
    fun `normalizes common Indonesian forms to the same E164`() {
        listOf("081234567890", "0812-3456-7890", "+62 812 3456 7890", "6281234567890", "(0812) 3456 7890")
            .forEach { assertEquals("+6281234567890", PhoneNumbers.normalize(it), it) }
    }

    @Test
    fun `keeps foreign numbers`() {
        assertEquals("+6591234567", PhoneNumbers.normalize("+65 9123 4567"))
    }

    @Test
    fun `rejects values that are not phone numbers`() {
        listOf("", "abc", "12", "Budi").forEach { assertNull(PhoneNumbers.normalize(it), it) }
    }
}

class CsvTest {
    @Test
    fun `parses comma separated values with quotes and line breaks`() {
        val text = "﻿Nama,No HP,Alamat\r\nBudi,0812,\"Jl. Merdeka 1,\nBandung\"\r\n\"Siti \"\"Ani\"\"\",0813,\n\n"
        assertEquals(
            listOf(
                listOf("Nama", "No HP", "Alamat"),
                listOf("Budi", "0812", "Jl. Merdeka 1,\nBandung"),
                listOf("Siti \"Ani\"", "0813", ""),
            ),
            Csv.parse(text),
        )
    }

    @Test
    fun `detects semicolon delimiter from Excel`() {
        assertEquals(listOf(listOf("Nama", "Kota"), listOf("Budi", "Bandung")), Csv.parse("Nama;Kota\nBudi;Bandung"))
    }

    @Test
    fun `empty input gives no rows`() {
        assertEquals(emptyList<List<String>>(), Csv.parse(""))
    }
}

class FieldKeysTest {
    @Test
    fun `derives keys from labels`() {
        assertEquals("kota_asal", FieldKeys.fromLabel("Kota Asal"))
        assertEquals("diskon", FieldKeys.fromLabel("  Diskon (%) "))
        assertEquals("cafe", FieldKeys.fromLabel("Café"))
        assertEquals("field", FieldKeys.fromLabel("!!!"))
    }

    @Test
    fun `avoids reserved and existing keys`() {
        assertEquals("nama_2", FieldKeys.fromLabel("Nama"))
        assertEquals("kota_3", FieldKeys.fromLabel("Kota", setOf("kota", "kota_2")))
    }
}

class ContactImportTest {
    private val budi = ExistingContact("1", ContactData("Budi", listOf("+6281234567890"), mapOf("kota" to "Bandung")))

    @Test
    fun `suggests mapping from headers and field labels`() {
        assertEquals(
            listOf(ColumnTarget.Name, ColumnTarget.Phone, ColumnTarget.Field("kota"), ColumnTarget.Ignore),
            ContactImport.suggestMapping(listOf("Nama", "No HP", "Kota", "Catatan lain"), mapOf("kota" to "Kota")),
        )
    }

    @Test
    fun `creates new contacts and drops invalid phones`() {
        val plan = ContactImport.planCsv(
            rows = listOf(listOf("Siti", "0813-1111-2222", "Jakarta"), listOf("Tono", "abc", ""), listOf("", "", "")),
            mapping = listOf(ColumnTarget.Name, ColumnTarget.Phone, ColumnTarget.Field("kota")),
            existing = listOf(budi),
            policy = DuplicatePolicy.SKIP,
        )
        assertEquals(
            listOf(
                ImportAction.Create(ContactData("Siti", listOf("+6281311112222"), mapOf("kota" to "Jakarta"))),
                ImportAction.Create(ContactData("Tono")),
            ),
            plan.actions,
        )
        assertEquals(1, plan.emptyRows)
        assertEquals(1, plan.invalidPhones)
    }

    private fun duplicateOfBudi(policy: DuplicatePolicy) = ContactImport.plan(
        incoming = listOf(ContactData("Pak Budi", listOf("+6281234567890"), mapOf("kota" to "Bogor", "diskon" to "10"))),
        existing = listOf(budi),
        policy = policy,
    ).actions.single()

    @Test
    fun `skip keeps the existing contact`() {
        assertEquals(
            ImportAction.Skip(ContactData("Pak Budi", listOf("+6281234567890"), mapOf("kota" to "Bogor", "diskon" to "10"))),
            duplicateOfBudi(DuplicatePolicy.SKIP),
        )
    }

    @Test
    fun `overwrite replaces values`() {
        assertEquals(
            ImportAction.Update("1", ContactData("Pak Budi", listOf("+6281234567890"), mapOf("kota" to "Bogor", "diskon" to "10"))),
            duplicateOfBudi(DuplicatePolicy.OVERWRITE),
        )
    }

    @Test
    fun `merge only fills empty values`() {
        assertEquals(
            ImportAction.Update("1", ContactData("Budi", listOf("+6281234567890"), mapOf("kota" to "Bandung", "diskon" to "10"))),
            duplicateOfBudi(DuplicatePolicy.MERGE),
        )
    }

    @Test
    fun `duplicates within one import merge into the first row`() {
        val plan = ContactImport.plan(
            incoming = listOf(
                ContactData("", listOf("+6281311112222")),
                ContactData("Siti", listOf("+6281311112222", "+6285700000000"), mapOf("kota" to "Jakarta")),
            ),
            existing = emptyList(),
            policy = DuplicatePolicy.SKIP,
        )
        assertEquals(
            listOf(ImportAction.Create(ContactData("Siti", listOf("+6281311112222", "+6285700000000"), mapOf("kota" to "Jakarta")))),
            plan.actions,
        )
    }

    @Test
    fun `phone-only contacts are named after the number`() {
        val plan = ContactImport.plan(listOf(ContactData("", listOf("+6281311112222"))), emptyList(), DuplicatePolicy.SKIP)
        assertEquals(listOf(ImportAction.Create(ContactData("081311112222", listOf("+6281311112222")))), plan.actions)
    }
}
