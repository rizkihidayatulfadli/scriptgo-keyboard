package com.magickeyboard.plan

import java.time.Instant
import java.time.OffsetDateTime

/**
 * The account's plan from `my_plan()` (migration 20260928110000_account_plan.sql, PRD §9).
 * BETA: the closed beta, all Pro features. TRIAL: 14 days of Pro after release. PRO: paid (or
 * granted). FREE: after the trial, with the free limits.
 */
enum class PlanKind { BETA, TRIAL, PRO, FREE }

data class AccountPlan(
    val kind: PlanKind,
    /** When the trial ends (TRIAL only). */
    val trialEndsAt: Instant? = null,
    /** When Pro ends (PRO only); null = no end date. */
    val proUntil: Instant? = null,
) {
    /** Whether Pro features (sync, extension, no limits) are open. */
    val hasProFeatures: Boolean get() = kind != PlanKind.FREE

    /** The end of the trial or of Pro, or null when the plan does not end. */
    val endsAt: Instant? get() = when (kind) {
        PlanKind.TRIAL -> trialEndsAt
        PlanKind.PRO -> proUntil
        else -> null
    }

    /**
     * The plan as of [now]: a trial or Pro whose end has passed is FREE, so a phone that has not
     * refreshed my_plan since still applies the limits on time.
     */
    fun at(now: Instant): AccountPlan {
        val end = endsAt ?: return this
        return if (end.isAfter(now)) this else AccountPlan(PlanKind.FREE)
    }

    /** Days left until [endsAt], rounded up (1 hour left = 1), 0 once passed; null without an end. */
    fun daysLeft(now: Instant): Int? {
        val end = endsAt ?: return null
        val seconds = end.epochSecond - now.epochSecond
        if (seconds <= 0) return 0
        return ((seconds + DAY - 1) / DAY).toInt()
    }

    companion object {
        private const val DAY = 86_400L

        /**
         * The plan from `my_plan()`'s fields: [plan] ("beta", "trial", "pro", "free") and the ISO
         * timestamps. Null for an unknown plan, so callers keep what they had.
         */
        fun parse(plan: String?, trialEndsAt: String?, proUntil: String?): AccountPlan? {
            val kind = when (plan) {
                "beta" -> PlanKind.BETA
                "trial" -> PlanKind.TRIAL
                "pro" -> PlanKind.PRO
                "free" -> PlanKind.FREE
                else -> return null
            }
            val trialEnd = instant(trialEndsAt)
            if (kind == PlanKind.TRIAL && trialEnd == null) return null
            return AccountPlan(kind, trialEnd.takeIf { kind == PlanKind.TRIAL }, instant(proUntil).takeIf { kind == PlanKind.PRO })
        }

        private fun instant(value: String?): Instant? =
            value?.takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
    }
}
