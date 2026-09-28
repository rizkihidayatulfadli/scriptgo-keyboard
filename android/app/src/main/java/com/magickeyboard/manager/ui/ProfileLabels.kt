package com.magickeyboard.manager.ui

import android.content.Context
import com.magickeyboard.profile.BusinessProfile
import com.magickeyboard.profile.BusinessType
import com.magickeyboard.profile.ProfileOption
import com.magickeyboard.profile.TeamSize
import helium314.keyboard.latin.R

/**
 * Labels of the business profile options in the app language. The engine keeps the Indonesian
 * labels; codes without an entry here (channel names, number ranges) are shown as they are.
 */
private val OPTION_LABELS = mapOf(
    "fashion" to R.string.sg_opt_fashion,
    "food" to R.string.sg_opt_food,
    "beauty" to R.string.sg_opt_beauty,
    "electronics" to R.string.sg_opt_electronics,
    "services" to R.string.sg_opt_services,
    "muslim_fashion" to R.string.sg_opt_muslim_fashion,
    "shoes_bags" to R.string.sg_opt_shoes_bags,
    "accessories" to R.string.sg_opt_accessories,
    "health" to R.string.sg_opt_health,
    "frozen_catering" to R.string.sg_opt_frozen_catering,
    "bakery" to R.string.sg_opt_bakery,
    "grocery" to R.string.sg_opt_grocery,
    "ppob" to R.string.sg_opt_ppob,
    "home" to R.string.sg_opt_home,
    "furniture" to R.string.sg_opt_furniture,
    "baby" to R.string.sg_opt_baby,
    "toys_hobby" to R.string.sg_opt_toys_hobby,
    "books_stationery" to R.string.sg_opt_books_stationery,
    "automotive" to R.string.sg_opt_automotive,
    "agriculture" to R.string.sg_opt_agriculture,
    "pets" to R.string.sg_opt_pets,
    "crafts" to R.string.sg_opt_crafts,
    "digital" to R.string.sg_opt_digital,
    "printing" to R.string.sg_opt_printing,
    "laundry" to R.string.sg_opt_laundry,
    "education" to R.string.sg_opt_education,
    "travel" to R.string.sg_opt_travel,
    "property" to R.string.sg_opt_property,
    "beauty_salon" to R.string.sg_opt_beauty_salon,
    "other" to R.string.sg_opt_other,
    "owner" to R.string.sg_opt_owner,
    "admin" to R.string.sg_opt_admin,
    "employee" to R.string.sg_opt_employee,
    "freelance" to R.string.sg_opt_freelance,
    "student" to R.string.sg_opt_student,
    "solo" to R.string.sg_opt_solo,
    "lt_10m" to R.string.sg_opt_lt_10m,
    "10_50m" to R.string.sg_opt_10_50m,
    "50_200m" to R.string.sg_opt_50_200m,
    "gt_200m" to R.string.sg_opt_gt_200m,
    "secret" to R.string.sg_opt_secret,
)

fun ProfileOption.text(context: Context): String = OPTION_LABELS[code]?.let(context::getString) ?: label

/** "Fashion & clothing · Bandung · 2–3 admins", like [BusinessProfile.summary] in the app language. */
fun BusinessProfile.summary(context: Context): String = listOfNotNull(
    businessTypeOther.trim().takeIf { businessType == BusinessType.OTHER && it.isNotEmpty() }
        ?: BusinessType.options.firstOrNull { it.code == businessType }?.text(context),
    city.trim().ifEmpty { null },
    TeamSize.options.firstOrNull { it.code == teamSize }?.let {
        if (teamSize == "solo") it.text(context) else context.getString(R.string.sg_profile_team_admins, it.label)
    },
).joinToString(" · ")
