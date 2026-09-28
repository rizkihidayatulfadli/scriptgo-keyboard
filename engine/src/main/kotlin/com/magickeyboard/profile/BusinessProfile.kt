package com.magickeyboard.profile

import com.magickeyboard.contacts.PhoneNumbers

/** One choice in the business profile form: a stable code stored on the server and its label. */
data class ProfileOption(val code: String, val label: String)

private fun options(vararg pairs: Pair<String, String>) = pairs.map { ProfileOption(it.first, it.second) }

// The codes match the check constraints in supabase/migrations/*_business_profiles.sql.
// The first six codes predate the longer list; keep them so saved profiles stay valid.
object BusinessType {
    /** "Lainnya": the user types what the business is in [BusinessProfile.businessTypeOther]. */
    const val OTHER = "other"

    val options = options(
        "fashion" to "Fashion & pakaian",
        "food" to "Makanan & minuman",
        "beauty" to "Kecantikan & skincare",
        "electronics" to "Elektronik & gadget",
        "services" to "Jasa lainnya",
        "muslim_fashion" to "Hijab & busana muslim",
        "shoes_bags" to "Sepatu & tas",
        "accessories" to "Aksesori & perhiasan",
        "health" to "Kesehatan & herbal",
        "frozen_catering" to "Frozen food & katering",
        "bakery" to "Kue & bakery",
        "grocery" to "Sembako & kebutuhan harian",
        "ppob" to "Pulsa, token & PPOB",
        "home" to "Rumah tangga & dekorasi",
        "furniture" to "Furnitur",
        "baby" to "Ibu & bayi",
        "toys_hobby" to "Mainan & hobi",
        "books_stationery" to "Buku & alat tulis",
        "automotive" to "Otomotif & sparepart",
        "agriculture" to "Pertanian & peternakan",
        "pets" to "Hewan peliharaan",
        "crafts" to "Kerajinan tangan",
        "digital" to "Produk digital",
        "printing" to "Percetakan & sablon",
        "laundry" to "Laundry",
        "education" to "Pendidikan & kursus",
        "travel" to "Travel & tiket",
        "property" to "Properti",
        "beauty_salon" to "Salon & perawatan",
        "other" to "Lainnya",
    )
}

/** Indonesia's 38 provinces (2023), used when location detection is unavailable or refused. */
object Province {
    val names = listOf(
        "Aceh", "Sumatera Utara", "Sumatera Barat", "Riau", "Kepulauan Riau", "Jambi", "Bengkulu", "Sumatera Selatan",
        "Kepulauan Bangka Belitung", "Lampung", "DKI Jakarta", "Banten", "Jawa Barat", "Jawa Tengah", "DI Yogyakarta",
        "Jawa Timur", "Bali", "Nusa Tenggara Barat", "Nusa Tenggara Timur", "Kalimantan Barat", "Kalimantan Tengah",
        "Kalimantan Selatan", "Kalimantan Timur", "Kalimantan Utara", "Sulawesi Utara", "Gorontalo", "Sulawesi Tengah",
        "Sulawesi Barat", "Sulawesi Selatan", "Sulawesi Tenggara", "Maluku", "Maluku Utara", "Papua", "Papua Barat",
        "Papua Barat Daya", "Papua Tengah", "Papua Pegunungan", "Papua Selatan",
    )

    /**
     * Maps a geocoder's admin area (e.g. "Daerah Khusus Ibukota Jakarta", "Special Region of Yogyakarta",
     * "West Java", "Jawa Barat") to a name in [names], or null.
     */
    fun match(adminArea: String?): String? {
        val a = adminArea?.lowercase()?.trim().orEmpty()
        if (a.isEmpty()) return null
        names.firstOrNull { it.lowercase() == a }?.let { return it }
        val english = mapOf(
            "jakarta" to "DKI Jakarta", "yogyakarta" to "DI Yogyakarta", "west java" to "Jawa Barat", "central java" to "Jawa Tengah",
            "east java" to "Jawa Timur", "north sumatra" to "Sumatera Utara", "west sumatra" to "Sumatera Barat",
            "south sumatra" to "Sumatera Selatan", "riau islands" to "Kepulauan Riau", "bangka belitung" to "Kepulauan Bangka Belitung",
            "west nusa tenggara" to "Nusa Tenggara Barat", "east nusa tenggara" to "Nusa Tenggara Timur",
            "west kalimantan" to "Kalimantan Barat", "central kalimantan" to "Kalimantan Tengah", "south kalimantan" to "Kalimantan Selatan",
            "east kalimantan" to "Kalimantan Timur", "north kalimantan" to "Kalimantan Utara", "north sulawesi" to "Sulawesi Utara",
            "central sulawesi" to "Sulawesi Tengah", "west sulawesi" to "Sulawesi Barat", "south sulawesi" to "Sulawesi Selatan",
            "southeast sulawesi" to "Sulawesi Tenggara", "north maluku" to "Maluku Utara", "southwest papua" to "Papua Barat Daya",
            "west papua" to "Papua Barat", "central papua" to "Papua Tengah", "highland papua" to "Papua Pegunungan",
            "south papua" to "Papua Selatan",
        )
        // Longest key first, so "southwest papua" wins over "west papua" and "papua".
        english.entries.sortedByDescending { it.key.length }.firstOrNull { a.contains(it.key) }?.let { return it.value }
        return names.sortedByDescending { it.length }.firstOrNull { a.contains(it.lowercase()) }
    }
}

/**
 * What the user does. The business name question follows it: a business for an owner, a company
 * for an employee, a brand for a freelancer, a school for a student.
 */
object Role {
    const val OWNER = "owner"
    const val EMPLOYEE = "employee"
    const val FREELANCE = "freelance"
    const val STUDENT = "student"

    val options = options(OWNER to "Pemilik", EMPLOYEE to "Karyawan", FREELANCE to "Freelance", STUDENT to "Pelajar")

    /** Codes offered before; an "Admin / CS" is an employee, and "Lainnya" is asked again. */
    fun migrate(code: String?): String? = if (code == "admin") EMPLOYEE else code
}

object Channel {
    val options = options("whatsapp" to "WhatsApp", "instagram" to "Instagram", "tiktok_shop" to "TikTok Shop", "shopee" to "Shopee", "tokopedia" to "Tokopedia", "website" to "Website")
}

object TeamSize {
    val options = options("solo" to "Saya saja", "2_3" to "2–3", "4_10" to "4–10", "10_plus" to "10+")
}

object ChatsPerDay {
    val options = options("lt_20" to "<20", "20_50" to "20–50", "50_100" to "50–100", "100_plus" to "100+")
}

object MonthlyRevenue {
    val options = options("lt_10m" to "< Rp10 jt", "10_50m" to "Rp10–50 jt", "50_200m" to "Rp50–200 jt", "gt_200m" to "> Rp200 jt", "secret" to "Rahasia")
}

/**
 * The business profile asked during onboarding (PRD 9, business analysis). Every question must be
 * answered before continuing ([missing] lists what is left); single choices hold an option code or null.
 */
data class BusinessProfile(
    val businessName: String = "",
    val businessType: String? = null,
    /** What the business is, in the user's words, when [businessType] is `other`. */
    val businessTypeOther: String = "",
    val city: String = "",
    /** One of [Province.names], or empty. */
    val province: String = "",
    val role: String? = null,
    val channels: List<String> = emptyList(),
    val teamSize: String? = null,
    val chatsPerDay: String? = null,
    val monthlyRevenue: String? = null,
    /** E.164 once [cleaned]; raw input while editing. */
    val whatsapp: String = "",
    val whatsappOptIn: Boolean = false,
) {
    /** Trims text, drops codes the server would reject, and normalizes the WhatsApp number. */
    fun cleaned(): BusinessProfile {
        val number = normalizeWhatsapp(whatsapp).orEmpty()
        return copy(
            businessName = businessName.trim().take(MAX_NAME),
            businessType = businessType.validIn(BusinessType.options),
            businessTypeOther = if (businessType == BusinessType.OTHER) businessTypeOther.trim().take(MAX_TYPE_OTHER) else "",
            city = city.trim().take(MAX_CITY),
            province = province.trim().takeIf { it in Province.names }.orEmpty(),
            role = Role.migrate(role).validIn(Role.options),
            channels = channels.distinct().filter { code -> Channel.options.any { it.code == code } },
            teamSize = teamSize.validIn(TeamSize.options),
            chatsPerDay = chatsPerDay.validIn(ChatsPerDay.options),
            monthlyRevenue = monthlyRevenue.validIn(MonthlyRevenue.options),
            whatsapp = number,
            whatsappOptIn = whatsappOptIn && number.isNotEmpty(),
        )
    }

    fun isEmpty(): Boolean = cleaned() == BusinessProfile()

    /** Questions still unanswered, as their field names; empty when the profile is complete. */
    fun missing(): List<String> {
        val c = cleaned()
        return buildList {
            if (c.businessName.isEmpty()) add("businessName")
            if (c.businessType == null) add("businessType")
            if (c.businessType == BusinessType.OTHER && c.businessTypeOther.isEmpty()) add("businessTypeOther")
            if (c.province.isEmpty()) add("province")
            if (c.city.isEmpty()) add("city")
            if (c.role == null) add("role")
            if (c.channels.isEmpty()) add("channels")
            if (c.teamSize == null) add("teamSize")
            if (c.chatsPerDay == null) add("chatsPerDay")
            if (c.monthlyRevenue == null) add("monthlyRevenue")
            if (c.whatsapp.isEmpty()) add("whatsapp")
        }
    }

    fun isComplete(): Boolean = missing().isEmpty()

    /** One line for the Akun tab, e.g. "Fashion · Bandung · 2–3 admin". */
    fun summary(): String = listOfNotNull(
        businessTypeOther.trim().takeIf { businessType == BusinessType.OTHER && it.isNotEmpty() } ?: label(BusinessType.options, businessType),
        city.trim().ifEmpty { null },
        label(TeamSize.options, teamSize)?.let { if (teamSize == "solo") it else "$it admin" },
    ).joinToString(" · ")

    companion object {
        const val MAX_NAME = 120
        const val MAX_CITY = 80
        const val MAX_TYPE_OTHER = 60

        /** The number as E.164 (region ID), or null when empty or not a phone number. */
        fun normalizeWhatsapp(raw: String): String? = raw.takeIf { it.isNotBlank() }?.let { PhoneNumbers.normalize(it) }

        fun label(options: List<ProfileOption>, code: String?): String? = options.firstOrNull { it.code == code }?.label

        private fun String?.validIn(options: List<ProfileOption>): String? = this?.takeIf { code -> options.any { it.code == code } }
    }
}
