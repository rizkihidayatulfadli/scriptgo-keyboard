package com.magickeyboard.plan

import java.time.Instant

/**
 * The free plan's limits (PRD §9): at most 20 templates, and 30 template uses once (never reset).
 * They apply only when [limited]: a FREE plan, or no account while the limits are switched on
 * (`limits_active()`); during the closed beta nothing is limited. Checked on the phone, so a rebuilt
 * app can remove them (GPL); sync and the extension are the server-side value.
 */
object FreeLimits {
    const val TEMPLATES = 20
    const val USES = 30

    /**
     * Whether the free limits apply at [now]: [plan] from my_plan (null when signed out or not loaded
     * yet), where a trial or Pro that has ended counts as free.
     */
    fun limited(plan: AccountPlan?, limitsActive: Boolean, now: Instant): Boolean =
        plan?.let { !it.at(now).hasProFeatures } ?: limitsActive

    /** Whether one more template may be added when [templates] already exist. */
    fun canAddTemplate(limited: Boolean, templates: Int): Boolean = !limited || templates < TEMPLATES

    /** Template uses left after [used] uses counted while limited. */
    fun usesLeft(used: Long): Int = (USES - used).coerceIn(0, USES.toLong()).toInt()

    /** Whether a template may be inserted after [used] uses counted while limited. */
    fun canInsert(limited: Boolean, used: Long): Boolean = !limited || used < USES
}
