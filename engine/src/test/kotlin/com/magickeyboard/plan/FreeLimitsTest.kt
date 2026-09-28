package com.magickeyboard.plan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class FreeLimitsTest {
    private val now = Instant.parse("2026-11-15T08:00:00Z")
    private val later = Instant.parse("2026-11-20T08:00:00Z")
    private val earlier = Instant.parse("2026-11-10T08:00:00Z")

    @Test
    fun `only a free plan, or no account once the limits are on, is limited`() {
        assertFalse(FreeLimits.limited(AccountPlan(PlanKind.BETA), limitsActive = true, now))
        assertFalse(FreeLimits.limited(AccountPlan(PlanKind.TRIAL, trialEndsAt = later), limitsActive = true, now))
        assertFalse(FreeLimits.limited(AccountPlan(PlanKind.PRO), limitsActive = true, now))
        assertFalse(FreeLimits.limited(AccountPlan(PlanKind.PRO, proUntil = later), limitsActive = true, now))
        assertTrue(FreeLimits.limited(AccountPlan(PlanKind.FREE), limitsActive = false, now))
        assertFalse(FreeLimits.limited(null, limitsActive = false, now))
        assertTrue(FreeLimits.limited(null, limitsActive = true, now))
    }

    @Test
    fun `a trial or Pro that ended since my_plan was read is free`() {
        assertTrue(FreeLimits.limited(AccountPlan(PlanKind.TRIAL, trialEndsAt = earlier), limitsActive = false, now))
        assertTrue(FreeLimits.limited(AccountPlan(PlanKind.PRO, proUntil = earlier), limitsActive = false, now))
        assertEquals(AccountPlan(PlanKind.FREE), AccountPlan(PlanKind.TRIAL, trialEndsAt = now).at(now))
        assertEquals(AccountPlan(PlanKind.BETA), AccountPlan(PlanKind.BETA).at(now))
    }

    @Test
    fun `twenty templates and thirty uses when limited, no limit otherwise`() {
        assertTrue(FreeLimits.canAddTemplate(limited = true, templates = 19))
        assertFalse(FreeLimits.canAddTemplate(limited = true, templates = 20))
        assertTrue(FreeLimits.canAddTemplate(limited = false, templates = 500))
        assertTrue(FreeLimits.canInsert(limited = true, used = 29))
        assertFalse(FreeLimits.canInsert(limited = true, used = 30))
        assertTrue(FreeLimits.canInsert(limited = false, used = 1000))
        assertEquals(30, FreeLimits.usesLeft(0))
        assertEquals(1, FreeLimits.usesLeft(29))
        assertEquals(0, FreeLimits.usesLeft(45))
    }
}
