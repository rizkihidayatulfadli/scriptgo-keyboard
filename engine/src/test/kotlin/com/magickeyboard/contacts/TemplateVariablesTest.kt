package com.magickeyboard.contacts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TemplateVariablesTest {
    @Test
    fun `lists template variables that are not fields yet, most used first`() {
        val templates = listOf(
            "Halo {nama}, ongkir ke {kota} Rp{ongkir}",
            "Kirim via {kurir:JNE,J&T} ke {kota}. Ongkir {ongkir}, total {Total}",
            "Tanggal {@tanggal}, {nomor}, {ukuran}",
        )
        assertEquals(
            listOf(
                UnlinkedVariable("ongkir", null, 2),
                UnlinkedVariable("kurir", listOf("JNE", "J&T"), 1),
                UnlinkedVariable("total", null, 1),
            ),
            TemplateVariables.unlinked(templates, fieldKeys = setOf("kota", "ukuran")),
        )
    }

    @Test
    fun `a variable used twice in one template counts once`() {
        assertEquals(listOf(UnlinkedVariable("kode", null, 1)), TemplateVariables.unlinked(listOf("{kode} {kode}"), emptySet()))
    }

    @Test
    fun `suggests a field name from the key`() {
        assertEquals("Kota asal", UnlinkedVariable("kota_asal", null, 1).suggestedLabel)
    }
}
