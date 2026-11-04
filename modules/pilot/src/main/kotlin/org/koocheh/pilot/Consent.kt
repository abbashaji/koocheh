package org.koocheh.pilot

/**
 * The participant's recorded informed consent for the pilot (spec section 10).
 * Only a day index is kept, never a finer timestamp.
 */
data class Consent(
    val granted: Boolean,
    val version: Int = CURRENT_VERSION,
    val recordedOnDay: Int,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

class ConsentRequiredException :
    IllegalStateException("Pilot export refused: no recorded consent from the participant")
