package org.koocheh.model

interface Clock {
    /** Wall-clock time in epoch milliseconds. */
    fun nowMs(): Long

    /** Monotonic elapsed time in milliseconds. */
    fun elapsedMs(): Long
}

object SystemClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun elapsedMs(): Long = System.nanoTime() / 1_000_000L
}

/** Settable clock for tests. */
class FakeClock(var now: Long = 0L, var elapsed: Long = 0L) : Clock {
    override fun nowMs(): Long = now
    override fun elapsedMs(): Long = elapsed

    fun advance(ms: Long) {
        now += ms
        elapsed += ms
    }
}
