package com.rooznegar.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RolloverTest {
    @Test fun unfinishedPastTaskNeedsDecision() =
        assertTrue(Rollover.needsDecision("2026-10-23", false, false, null, "2026-10-24"))

    @Test fun finishedTaskIsNeverAsked() =
        assertFalse(Rollover.needsDecision("2026-10-23", true, false, null, "2026-10-24"))

    @Test fun todayTaskIsNotOverdue() =
        assertFalse(Rollover.needsDecision("2026-10-24", false, false, null, "2026-10-24"))

    @Test fun snoozedTodayIsHiddenButBackTomorrow() {
        assertFalse(Rollover.needsDecision("2026-10-23", false, false, "2026-10-24", "2026-10-24"))
        assertTrue(Rollover.needsDecision("2026-10-23", false, false, "2026-10-24", "2026-10-25"))
    }
}
