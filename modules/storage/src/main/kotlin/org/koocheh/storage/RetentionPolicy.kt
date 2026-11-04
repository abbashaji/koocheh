package org.koocheh.storage

/**
 * Retention rules. Days are epoch days (UTC): nowMs / 86_400_000.
 * An encounter is kept while its age is at most 14 days (13 days old: kept, 15 days old: deleted).
 * A chat message is expired once expiresAtMs <= nowMs.
 */
object RetentionPolicy {
    const val ENCOUNTER_RETENTION_DAYS: Int = 14
    const val MS_PER_DAY: Long = 86_400_000L

    fun epochDay(nowMs: Long): Int = Math.floorDiv(nowMs, MS_PER_DAY).toInt()

    fun isEncounterExpired(day: Int, nowMs: Long): Boolean =
        epochDay(nowMs) - day > ENCOUNTER_RETENTION_DAYS

    /** Encounter rows with day < this value are deleted. */
    fun encounterCutoffDay(nowMs: Long): Int =
        epochDay(nowMs) - ENCOUNTER_RETENTION_DAYS

    fun isMessageExpired(expiresAtMs: Long, nowMs: Long): Boolean = expiresAtMs <= nowMs
}
