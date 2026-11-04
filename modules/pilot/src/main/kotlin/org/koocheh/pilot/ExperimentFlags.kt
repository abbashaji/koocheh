package org.koocheh.pilot

/**
 * The three pilot experiments, as explicit flags. They are assigned locally by the
 * pilot coordinator code (an arm number handed to the participant in person),
 * never by a server.
 *
 * bit 0 -> familiarPrompts : familiar-stranger prompts vs a plain nearby list
 * bit 1 -> tieredDisclosure : tiered disclosure vs single-step reveal
 * bit 2 -> inviteTokens    : invite token vs plain APK link
 */
data class ExperimentFlags(
    val familiarPrompts: Boolean,
    val tieredDisclosure: Boolean,
    val inviteTokens: Boolean,
) {
    /** The arm number (0..7) this combination corresponds to. */
    val arm: Int
        get() = (if (familiarPrompts) 1 else 0) or
            (if (tieredDisclosure) 2 else 0) or
            (if (inviteTokens) 4 else 0)

    companion object {
        const val ARM_COUNT = 8

        /** Local assignment by the coordinator: arm is any non-negative number. */
        fun assign(arm: Int): ExperimentFlags {
            require(arm >= 0) { "arm must be non-negative" }
            val a = arm % ARM_COUNT
            return ExperimentFlags(
                familiarPrompts = a and 1 != 0,
                tieredDisclosure = a and 2 != 0,
                inviteTokens = a and 4 != 0,
            )
        }
    }
}
