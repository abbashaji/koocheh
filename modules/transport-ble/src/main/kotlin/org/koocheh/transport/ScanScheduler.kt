package org.koocheh.transport

import org.koocheh.model.ScanMode

const val MINUTES_PER_DAY = 1440

/**
 * User-selected scan window as minutes of day. If start > end the window
 * wraps past midnight. start == end is an empty window.
 */
data class TimeWindow(val startMinute: Int, val endMinute: Int) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY)
        require(endMinute in 0 until MINUTES_PER_DAY)
    }

    fun contains(minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> false
        startMinute < endMinute -> minuteOfDay >= startMinute && minuteOfDay < endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    /** Minutes until the next start or end edge, in 1..1440. */
    fun minutesToNextEdge(minuteOfDay: Int): Int {
        fun dist(edge: Int): Int {
            val d = (edge - minuteOfDay + MINUTES_PER_DAY) % MINUTES_PER_DAY
            return if (d == 0) MINUTES_PER_DAY else d
        }
        return minOf(dist(startMinute), dist(endMinute))
    }
}

data class ScanDecision(val scan: Boolean, val recheckAfterMs: Long)

/**
 * Pure duty-cycle logic; no Android types, so it is unit-testable.
 * PASSIVE scans only in the foreground, NORMAL duty-cycles, COMMUTE scans
 * only inside the user's windows. Callers re-run decide() on any state change.
 */
class ScanScheduler(
    private val normalOnMs: Long = 10_000L,
    private val normalPeriodMs: Long = 60_000L,
    private val maxRecheckMs: Long = 15 * 60_000L,
) {
    private var mode: ScanMode? = null
    private var windows: List<TimeWindow> = emptyList()
    private var foreground = false

    init {
        require(normalOnMs in 1 until normalPeriodMs)
    }

    @Synchronized fun start(mode: ScanMode, windows: List<TimeWindow> = emptyList()) {
        this.mode = mode
        this.windows = windows.toList()
    }

    @Synchronized fun stop() { mode = null }

    @Synchronized fun setForeground(value: Boolean) { foreground = value }

    @Synchronized fun currentMode(): ScanMode? = mode

    @Synchronized
    fun decide(elapsedMs: Long, minuteOfDay: Int): ScanDecision = when (mode) {
        null -> ScanDecision(false, maxRecheckMs)
        ScanMode.PASSIVE -> ScanDecision(foreground, maxRecheckMs)
        ScanMode.NORMAL -> {
            val pos = elapsedMs.mod(normalPeriodMs)
            if (pos < normalOnMs) ScanDecision(true, normalOnMs - pos)
            else ScanDecision(false, normalPeriodMs - pos)
        }
        ScanMode.COMMUTE -> {
            val active = windows.any { it.contains(minuteOfDay) }
            val nextEdge = windows.minOfOrNull { it.minutesToNextEdge(minuteOfDay) }
            // Minute granularity: wake up to a minute early and re-evaluate.
            val wait = if (nextEdge == null) maxRecheckMs
            else ((nextEdge - 1) * 60_000L).coerceIn(5_000L, maxRecheckMs)
            ScanDecision(active, wait)
        }
    }
}
