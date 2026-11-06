package org.koocheh.familiar

import org.koocheh.model.ContextId

/**
 * The slice of org.koocheh.storage.ContextStore this module uses (same signatures). A port is
 * used because :modules:storage is an Android library and this module is pure JVM; the app
 * wires ContextStore in through a one-line adapter.
 */
interface EncounterStore {
    suspend fun incrementEncounter(linkKey: ByteArray, day: Int)
    suspend fun encountersFor(linkKey: ByteArray, sinceDay: Int): List<Int>
    suspend fun purgeExpired(nowMs: Long)
}

/**
 * Counts how many distinct days the same overlap (linkKey) was seen, per context.
 *
 * Storage only ever sees epoch-day ints (and a day-aligned millis value for purgeExpired);
 * no place and no finer timestamp is passed anywhere. Link keys are never logged.
 *
 * The store cannot enumerate keys, so the tracker keeps an in-memory map per context of
 * linkKey -> days seen, filled by [record] and pruned once the last day is older than
 * policy.expiryDays. LIMIT: after a process restart the map is empty. Stored counts survive,
 * but a prompt for an overlap appears again only when that overlap is next recorded.
 */
class FamiliarTracker(
    private val store: EncounterStore,
    private val policy: FamiliarPolicy = FamiliarPolicy(),
) {
    private class KeyRef(val bytes: ByteArray) {
        override fun equals(other: Any?) = other is KeyRef && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
        override fun toString() = "LinkKey(redacted)"
    }

    private class Entry(val key: KeyRef, var lastDay: Int) {
        val days = HashSet<Int>()
    }

    private val lock = Any()
    private val seen = HashMap<ContextId, HashMap<KeyRef, Entry>>()

    /** Records one encounter. The same (context, key, day) is stored once. */
    suspend fun record(context: ContextId, linkKey: ByteArray, day: Int) {
        val ref = KeyRef(linkKey.copyOf())
        val isNewDay = synchronized(lock) {
            pruneLocked(day)
            val entry = seen.getOrPut(context) { HashMap() }.getOrPut(ref) { Entry(ref, day) }
            if (day > entry.lastDay) entry.lastDay = day
            entry.days.add(day)
        }
        if (isNewDay) {
            try {
                store.incrementEncounter(linkKey, day)
            } catch (t: Throwable) {
                synchronized(lock) { seen[context]?.get(ref)?.days?.remove(day) }
                throw t
            }
        }
        store.purgeExpired(DayBucket(day).startMs)
    }

    /** Prompts for overlaps seen on at least policy.threshold distinct days in the last windowDays. */
    suspend fun prompts(context: ContextId, today: Int): List<FamiliarPrompt> {
        val keys = synchronized(lock) {
            pruneLocked(today)
            seen[context]?.values?.map { it.key.bytes.copyOf() } ?: emptyList()
        }
        store.purgeExpired(DayBucket(today).startMs)
        val sinceDay = today - (policy.windowDays - 1)
        return keys
            .map { key -> store.encountersFor(key, sinceDay).filter { it in sinceDay..today }.distinct().size }
            .filter { it >= policy.threshold }
            .sortedDescending()
            .map { FamiliarPrompt(it, policy.windowDays) }
    }

    private fun pruneLocked(today: Int) {
        val cutoff = today - policy.expiryDays
        val contexts = seen.values.iterator()
        while (contexts.hasNext()) {
            val keys = contexts.next()
            val entries = keys.values.iterator()
            while (entries.hasNext()) {
                val entry = entries.next()
                if (entry.lastDay < cutoff) entries.remove() else entry.days.removeAll { it < cutoff }
            }
            if (keys.isEmpty()) contexts.remove()
        }
    }
}
