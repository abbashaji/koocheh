package org.koocheh.familiar

/**
 * A calendar day as whole days since 1970-01-01 UTC. This is the only time unit the
 * familiar module hands to storage: no timestamp finer than a day is ever stored or passed on.
 */
@JvmInline
value class DayBucket(val epochDay: Int) {
    operator fun minus(days: Int): DayBucket = DayBucket(epochDay - days)

    /** Midnight UTC of this day, for APIs that take epoch millis (purgeExpired). Always a day multiple. */
    val startMs: Long get() = epochDay.toLong() * MS_PER_DAY

    companion object {
        const val MS_PER_DAY: Long = 86_400_000L

        fun ofEpochMs(ms: Long): DayBucket = DayBucket(Math.floorDiv(ms, MS_PER_DAY).toInt())
    }
}
