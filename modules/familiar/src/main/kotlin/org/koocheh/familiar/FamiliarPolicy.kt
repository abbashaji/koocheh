package org.koocheh.familiar

/** A prompt to show: the same overlap was seen on [encounters] distinct days within [windowDays]. */
data class FamiliarPrompt(val encounters: Int, val windowDays: Int)

/**
 * Tunable limits. [threshold] is user-set (default 3 distinct days), [windowDays] is the counting
 * window (7), [expiryDays] is how long counts are kept on device (14).
 */
data class FamiliarPolicy(
    val threshold: Int = 3,
    val windowDays: Int = 7,
    val expiryDays: Int = 14,
) {
    init {
        require(threshold >= 1) { "threshold must be at least 1" }
        require(windowDays >= 1) { "windowDays must be at least 1" }
        require(expiryDays >= windowDays) { "expiryDays must cover the window" }
    }

    /** Plain statement of fact: no urgency, no teaser. */
    fun promptText(prompt: FamiliarPrompt): String {
        val times = if (prompt.encounters == 1) "1 time" else "${prompt.encounters} times"
        val span = if (prompt.windowDays == 7) "this week" else "in the last ${prompt.windowDays} days"
        return "You have crossed paths with someone who shares interests, $times $span."
    }
}
