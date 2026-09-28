package com.magickeyboard.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BusinessProfileTest {
    @Test
    fun `option codes are unique and match the database checks`() {
        for (options in listOf(BusinessType.options, Role.options, Channel.options, TeamSize.options, ChatsPerDay.options, MonthlyRevenue.options)) {
            assertEquals(options.size, options.map { it.code }.toSet().size)
            assertTrue(options.all { it.code.matches(Regex("[a-z0-9_]+")) }, options.toString())
        }
        // Codes saved before the longer list must stay valid.
        assertTrue(BusinessType.options.map { it.code }.containsAll(listOf("fashion", "food", "beauty", "electronics", "services", "other")))
        assertTrue(BusinessType.options.size >= 25)
        assertEquals(listOf("solo", "2_3", "4_10", "10_plus"), TeamSize.options.map { it.code })
    }

    @Test
    fun `whatsapp is stored as E164`() {
        assertEquals("+6281234567890", BusinessProfile.normalizeWhatsapp("812 3456 7890"))
        assertEquals("+6281234567890", BusinessProfile.normalizeWhatsapp("0812-3456-7890"))
        assertNull(BusinessProfile.normalizeWhatsapp(""))
        assertNull(BusinessProfile.normalizeWhatsapp("123"))
    }

    @Test
    fun `cleaned trims text, drops unknown codes, and caps lengths`() {
        val profile = BusinessProfile(
            businessName = "  Toko Rina Hijab  ",
            businessType = "fashion",
            city = " Bandung ",
            role = "boss",
            channels = listOf("whatsapp", "myspace", "shopee", "whatsapp"),
            teamSize = "2_3",
            chatsPerDay = null,
            monthlyRevenue = "secret",
            whatsapp = "0812-3456-7890",
            whatsappOptIn = true,
        ).cleaned()
        assertEquals("Toko Rina Hijab", profile.businessName)
        assertEquals("Bandung", profile.city)
        assertNull(profile.role)
        assertEquals(listOf("whatsapp", "shopee"), profile.channels)
        assertEquals("+6281234567890", profile.whatsapp)
        assertTrue(profile.whatsappOptIn)
        assertEquals(120, BusinessProfile(businessName = "x".repeat(300)).cleaned().businessName.length)
    }

    @Test
    fun `roles offered before map to the new ones`() {
        assertEquals(listOf("owner", "employee", "freelance", "student"), Role.options.map { it.code })
        assertEquals("employee", BusinessProfile(role = "admin").cleaned().role)
        assertNull(BusinessProfile(role = "other").cleaned().role)
        assertEquals("student", BusinessProfile(role = "student").cleaned().role)
    }

    @Test
    fun `opt-in needs a number`() {
        assertFalse(BusinessProfile(whatsapp = "", whatsappOptIn = true).cleaned().whatsappOptIn)
    }

    @Test
    fun `summary for the account tab`() {
        val profile = BusinessProfile(businessType = "fashion", city = "Bandung", teamSize = "2_3")
        assertEquals("Fashion & pakaian · Bandung · 2–3 admin", profile.summary())
        assertEquals("", BusinessProfile().summary())
        assertTrue(BusinessProfile().isEmpty())
        assertFalse(profile.isEmpty())
    }

    @Test
    fun `a profile is complete only when every question is answered`() {
        val full = BusinessProfile(
            businessName = "Toko Rina", businessType = "muslim_fashion", city = "Kota Bandung", province = "Jawa Barat",
            role = "owner", channels = listOf("whatsapp"), teamSize = "2_3", chatsPerDay = "20_50",
            monthlyRevenue = "secret", whatsapp = "0812-3456-7890",
        )
        assertTrue(full.isComplete())
        assertEquals(listOf("whatsapp"), full.copy(whatsapp = "123").missing())
        assertEquals(listOf("province"), full.copy(province = "Atlantis").missing())
        assertEquals(10, BusinessProfile().missing().size)
    }

    @Test
    fun `other business type asks what the business is`() {
        val other = BusinessProfile(businessType = "other", businessTypeOther = "  Rental kamera ", city = "Bandung")
        assertEquals("Rental kamera", other.cleaned().businessTypeOther)
        assertEquals("Rental kamera · Bandung", other.summary())
        assertTrue("businessTypeOther" in other.copy(businessTypeOther = " ").missing())
        // Only kept for "other".
        assertEquals("", other.copy(businessType = "fashion").cleaned().businessTypeOther)
        assertEquals(60, other.copy(businessTypeOther = "x".repeat(100)).cleaned().businessTypeOther.length)
    }

    @Test
    fun `provinces from the geocoder in Indonesian or English`() {
        assertEquals("Jawa Barat", Province.match("Jawa Barat"))
        assertEquals("Jawa Barat", Province.match("West Java"))
        assertEquals("DKI Jakarta", Province.match("Daerah Khusus Ibukota Jakarta"))
        assertEquals("DI Yogyakarta", Province.match("Special Region of Yogyakarta"))
        assertEquals("Papua Barat Daya", Province.match("Southwest Papua"))
        assertEquals("Papua Barat", Province.match("West Papua"))
        assertEquals("Sulawesi Tenggara", Province.match("Southeast Sulawesi"))
        assertNull(Province.match("California"))
        assertNull(Province.match(null))
        assertEquals(38, Province.names.size)
    }
}
