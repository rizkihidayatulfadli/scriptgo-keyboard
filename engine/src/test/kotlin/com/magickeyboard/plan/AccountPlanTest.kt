package com.magickeyboard.plan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AccountPlanTest {
    private val now = Instant.parse("2026-10-01T08:00:00Z")

    @Test
    fun `reads each plan from my_plan`() {
        assertEquals(AccountPlan(PlanKind.BETA), AccountPlan.parse("beta", null, null))
        assertEquals(
            AccountPlan(PlanKind.TRIAL, trialEndsAt = Instant.parse("2026-10-12T08:40:38.261541Z")),
            AccountPlan.parse("trial", "2026-10-12T08:40:38.261541+00:00", null),
        )
        assertEquals(AccountPlan(PlanKind.PRO, proUntil = Instant.parse("2026-10-03T01:00:00Z")), AccountPlan.parse("pro", null, "2026-10-03T08:00:00+07:00"))
        assertEquals(AccountPlan(PlanKind.PRO), AccountPlan.parse("pro", null, "null"))
        assertEquals(AccountPlan(PlanKind.FREE), AccountPlan.parse("free", "2026-10-12T08:00:00Z", null))
    }

    @Test
    fun `an unknown plan or a trial without an end is not used`() {
        assertNull(AccountPlan.parse("team", null, null))
        assertNull(AccountPlan.parse(null, null, null))
        assertNull(AccountPlan.parse("trial", null, null))
        assertNull(AccountPlan.parse("trial", "besok", null))
    }

    @Test
    fun `days left round up and stop at zero`() {
        val trial = { end: String -> AccountPlan(PlanKind.TRIAL, trialEndsAt = Instant.parse(end)) }
        assertEquals(14, trial("2026-10-15T08:00:00Z").daysLeft(now))
        assertEquals(3, trial("2026-10-03T09:00:00Z").daysLeft(now))
        assertEquals(1, trial("2026-10-01T09:00:00Z").daysLeft(now))
        assertEquals(0, trial("2026-10-01T07:00:00Z").daysLeft(now))
        assertNull(AccountPlan(PlanKind.PRO).daysLeft(now))
        assertNull(AccountPlan(PlanKind.BETA).daysLeft(now))
    }

    @Test
    fun `only free lacks Pro features`() {
        assertTrue(AccountPlan(PlanKind.BETA).hasProFeatures)
        assertTrue(AccountPlan(PlanKind.TRIAL, trialEndsAt = now).hasProFeatures)
        assertTrue(AccountPlan(PlanKind.PRO).hasProFeatures)
        assertFalse(AccountPlan(PlanKind.FREE).hasProFeatures)
    }
}
