package org.koocheh.pilot

import android.content.SharedPreferences
import kotlin.math.roundToLong

/**
 * Tiny counter store, independent of ContextStore. Counts only: no peer ids, no tags,
 * nothing finer than a day index. (If the encrypted ContextStore is used later it can
 * implement this interface; the pilot code only codes against this contract.)
 */
interface PilotStore {
    fun all(): Map<String, Long>
    fun get(key: String): Long?
    fun put(key: String, value: Long)
}

class InMemoryPilotStore : PilotStore {
    private val map = HashMap<String, Long>()
    override fun all(): Map<String, Long> = HashMap(map)
    override fun get(key: String): Long? = map[key]
    override fun put(key: String, value: Long) {
        map[key] = value
    }
}

/** Small preferences file on the device. Never leaves it except by manual export. */
class SharedPrefsPilotStore(private val prefs: SharedPreferences) : PilotStore {
    override fun all(): Map<String, Long> =
        prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    override fun get(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    override fun put(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }
}

/** Discovery attempts and successes within one distance bucket (upper bound in metres). */
data class RangeBucket(val maxMeters: Int, val attempts: Int, val successes: Int)

/** Snapshot of the caller's RangeLog, already reduced to distance buckets. */
data class RangeSnapshot(val buckets: List<RangeBucket>)

data class RangeCounts(val attempts: Long, val successes: Long)

data class PilotSummary(
    val helloByWeek: Map<Int, Int>,
    val firstHelloDelayDays: Int?,
    val daysOpened: Int,
    val retainedDay14: Boolean,
    val batteryAvgPercentPerDayByDay: Map<Int, Float>,
    val rangeBuckets: Map<Int, RangeCounts>,
    val meetUpYesByWeek: Map<Int, Int>,
    val meetUpNoByWeek: Map<Int, Int>,
)

/**
 * Local pilot counters. Days are whole day indexes since the participant joined
 * (day 0 = first day); weeks are day / 7.
 */
class PilotRecorder(private val store: PilotStore) {

    fun firstSignal(day: Int) {
        touch(day)
        if (store.get(K_FIRST_SIGNAL) == null) store.put(K_FIRST_SIGNAL, day.toLong())
    }

    fun mutualHello(day: Int) {
        touch(day)
        inc("hello.w${week(day)}")
        if (store.get(K_FIRST_HELLO_DELAY) == null) {
            val start = store.get(K_FIRST_SIGNAL) ?: day.toLong()
            store.put(K_FIRST_HELLO_DELAY, (day - start).coerceAtLeast(0L))
        }
    }

    fun appOpened(day: Int) {
        touch(day)
        store.put("open.d$day", 1L)
    }

    /** Battery cost sample, attributed to the most recent day seen. */
    fun batterySample(percentPerDay: Float) {
        val day = store.get(K_LAST_DAY)?.toInt() ?: 0
        inc("bat.d$day.n")
        add("bat.d$day.sum", (percentPerDay * 100f).roundToLong())
    }

    fun meetUp(happened: Boolean) {
        val day = store.get(K_LAST_DAY)?.toInt() ?: 0
        inc("meet.w${week(day)}.${if (happened) "yes" else "no"}")
    }

    fun recordRange(snapshot: RangeSnapshot) {
        for (b in snapshot.buckets) {
            add("range.${b.maxMeters}.a", b.attempts.toLong())
            add("range.${b.maxMeters}.s", b.successes.toLong())
        }
    }

    fun summary(): PilotSummary {
        val all = store.all()
        fun weekly(prefix: String, suffix: String = ""): Map<Int, Int> = all.entries
            .filter { it.key.startsWith(prefix) && it.key.endsWith(suffix) }
            .associate { it.key.removePrefix(prefix).removeSuffix(suffix).toInt() to it.value.toInt() }
            .toSortedMap()
        val openDays = all.keys.filter { it.startsWith("open.d") }.map { it.removePrefix("open.d").toInt() }
        val battery = all.keys.filter { it.startsWith("bat.d") && it.endsWith(".n") }.associate { k ->
            val day = k.removePrefix("bat.d").removeSuffix(".n").toInt()
            val n = all.getValue(k).coerceAtLeast(1L)
            day to (all["bat.d$day.sum"] ?: 0L) / 100f / n
        }.toSortedMap()
        val ranges = all.keys.filter { it.startsWith("range.") && it.endsWith(".a") }.associate { k ->
            val m = k.removePrefix("range.").removeSuffix(".a").toInt()
            m to RangeCounts(all.getValue(k), all["range.$m.s"] ?: 0L)
        }.toSortedMap()
        return PilotSummary(
            helloByWeek = weekly("hello.w"),
            firstHelloDelayDays = all[K_FIRST_HELLO_DELAY]?.toInt(),
            daysOpened = openDays.size,
            retainedDay14 = openDays.any { it >= 14 },
            batteryAvgPercentPerDayByDay = battery,
            rangeBuckets = ranges,
            meetUpYesByWeek = weekly("meet.w", ".yes"),
            meetUpNoByWeek = weekly("meet.w", ".no"),
        )
    }

    private fun touch(day: Int) {
        require(day >= 0) { "day must be non-negative" }
        if (day.toLong() > (store.get(K_LAST_DAY) ?: -1L)) store.put(K_LAST_DAY, day.toLong())
    }

    private fun week(day: Int) = day / 7
    private fun inc(key: String) = add(key, 1L)
    private fun add(key: String, delta: Long) = store.put(key, (store.get(key) ?: 0L) + delta)

    private companion object {
        const val K_FIRST_SIGNAL = "firstSignalDay"
        const val K_FIRST_HELLO_DELAY = "firstHelloDelayDays"
        const val K_LAST_DAY = "lastDay"
    }
}
