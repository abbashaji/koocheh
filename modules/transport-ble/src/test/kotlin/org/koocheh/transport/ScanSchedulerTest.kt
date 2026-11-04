package org.koocheh.transport

import org.koocheh.model.ScanMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// NOTE [UNVERIFIED]: Android BLE address rotation may not align with
// ephemeral id rotation. The tracking test must check this; it cannot be
// verified by these pure-logic tests.
class ScanSchedulerTest {

    private val morning = TimeWindow(7 * 60 + 30, 9 * 60)
    private val night = TimeWindow(22 * 60, 2 * 60)

    @Test fun windowContainsBoundaries() {
        assertTrue(morning.contains(7 * 60 + 30))
        assertTrue(morning.contains(8 * 60))
        assertFalse(morning.contains(9 * 60))
        assertFalse(morning.contains(7 * 60 + 29))
    }

    @Test fun windowWrapsMidnight() {
        assertTrue(night.contains(23 * 60))
        assertTrue(night.contains(0))
        assertTrue(night.contains(2 * 60 - 1))
        assertFalse(night.contains(2 * 60))
        assertFalse(night.contains(12 * 60))
    }

    @Test fun emptyWindowNeverMatches() {
        val w = TimeWindow(600, 600)
        assertFalse(w.contains(600))
        assertFalse(w.contains(0))
    }

    @Test fun minutesToNextEdge() {
        assertEquals(30, morning.minutesToNextEdge(7 * 60))
        assertEquals(90, morning.minutesToNextEdge(7 * 60 + 30))
        assertEquals(1440, TimeWindow(600, 600).minutesToNextEdge(600))
    }

    @Test fun commuteScansOnlyInsideWindows() {
        val s = ScanScheduler()
        s.start(ScanMode.COMMUTE, listOf(morning, night))
        assertTrue(s.decide(0, 8 * 60).scan)
        assertFalse(s.decide(0, 12 * 60).scan)
        assertTrue(s.decide(0, 23 * 60).scan)
    }

    @Test fun commuteWithoutWindowsNeverScans() {
        val s = ScanScheduler()
        s.start(ScanMode.COMMUTE)
        assertFalse(s.decide(0, 8 * 60).scan)
    }

    @Test fun commuteRecheckIsBoundedByNextEdge() {
        val s = ScanScheduler()
        s.start(ScanMode.COMMUTE, listOf(morning))
        val d = s.decide(0, 7 * 60)
        assertFalse(d.scan)
        assertTrue(d.recheckAfterMs <= 30 * 60_000L)
        assertTrue(d.recheckAfterMs >= 5_000L)
    }

    @Test fun passiveFollowsForeground() {
        val s = ScanScheduler()
        s.start(ScanMode.PASSIVE)
        assertFalse(s.decide(0, 0).scan)
        s.setForeground(true)
        assertTrue(s.decide(0, 0).scan)
        s.setForeground(false)
        assertFalse(s.decide(0, 0).scan)
    }

    @Test fun normalDutyCycles() {
        val s = ScanScheduler(normalOnMs = 10_000, normalPeriodMs = 60_000)
        s.start(ScanMode.NORMAL)
        val on = s.decide(65_000, 0)
        assertTrue(on.scan)
        assertEquals(5_000, on.recheckAfterMs)
        val off = s.decide(75_000, 0)
        assertFalse(off.scan)
        assertEquals(50_000, off.recheckAfterMs)
    }

    @Test fun modeTransitions() {
        val s = ScanScheduler()
        assertNull(s.currentMode())
        assertFalse(s.decide(0, 8 * 60).scan)
        s.start(ScanMode.COMMUTE, listOf(morning))
        assertTrue(s.decide(0, 8 * 60).scan)
        s.start(ScanMode.PASSIVE)
        assertFalse(s.decide(0, 8 * 60).scan)
        s.start(ScanMode.NORMAL)
        assertTrue(s.decide(0, 12 * 60).scan)
        s.stop()
        assertNull(s.currentMode())
        assertFalse(s.decide(0, 0).scan)
    }
}
