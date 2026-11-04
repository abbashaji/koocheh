package org.koocheh.ui

/**
 * All user-facing strings that carry safety meaning. Plain language.
 * Rules: no streaks, daily rewards, "someone liked you" teasers, fake counts or urgency.
 */
object UiCopy {
    const val APP_NAME = "Koocheh"

    // Onboarding risk briefing
    const val BRIEFING_TITLE = "Before you start: what this app can and cannot do"
    val BRIEFING_POINTS: List<String> = listOf(
        "This app cannot remove the legal and social consequences of what you do. " +
            "Those consequences are real and they are yours to weigh.",
        "It reduces risk. It does not remove it. Treat every meeting as something that can still go wrong.",
        "If someone forces you to unlock your phone, this app does not defend against that. " +
            "A decoy PIN may help in some situations, but it is not a guarantee.",
        "You decide what to share, and you can stop at any time."
    )
    const val BRIEFING_ACK = "I have read this and I understand the consequences."
    const val BRIEFING_CONTINUE = "Continue"

    // Invite activation
    const val INVITE_TITLE = "Enter your invite"
    const val INVITE_HINT = "Invite code"
    const val INVITE_ACTIVATE = "Activate"
    const val INVITE_FAILED = "This invite could not be activated."

    // PIN creation
    const val PIN_TITLE = "Create a PIN"
    const val PIN_HINT = "PIN (at least 6 digits)"
    const val PIN_CONFIRM_HINT = "Repeat PIN"
    const val PIN_MISMATCH = "The two PINs do not match."
    const val PIN_TOO_SHORT = "The PIN is too short."
    const val PIN_SAVE = "Save PIN"

    // Unlock. WRONG gives no hint about decoy or wipe PINs.
    const val UNLOCK_TITLE = "Enter your PIN"
    const val UNLOCK_ACTION = "Unlock"
    const val UNLOCK_ERROR = "That PIN did not work."
    const val FRESH_INSTALL_TITLE = "Welcome"
    const val FRESH_INSTALL_BODY = "Set up the app to get started."
    const val FRESH_INSTALL_ACTION = "Start setup"

    // Profile
    const val PROFILE_TITLE = "Profile"
    const val PROFILE_CONTEXT_NOTE = "Each context has its own profile. Nothing is shared between contexts."
    const val NICKNAME_LABEL = "Nickname"
    const val AGE_RANGE_LABEL = "Age range"
    const val TAGS_LABEL = "Tags (up to 10)"
    const val TAG_ADD = "Add tag"
    const val LINE_LABEL = "One line about you (140 characters max)"
    const val PHOTO_LABEL = "Show a photo"
    const val PHOTO_NOTE = "Off by default. A photo can identify you."
    const val PHOTO_PICK = "Choose photo"
    const val PROFILE_SAVE = "Save"
    const val MAX_TAGS = 10
    const val MAX_LINE = 140

    // Settings
    const val SETTINGS_TITLE = "Settings"
    const val SCAN_MODE_LABEL = "Scan mode"
    const val SCAN_PASSIVE = "Passive"
    const val SCAN_NORMAL = "Normal"
    const val SCAN_COMMUTE = "Commute"
    const val SCAN_COMMUTE_NOTE = "Scans only during the time windows you set."
    const val WINDOW_ADD = "Add time window"
    const val TIER0_LABEL = "Tier 0 threshold"
    const val FAMILIAR_LABEL = "Familiar-stranger threshold"

    // Security settings
    const val SECURITY_TITLE = "Security"
    const val DECOY_PIN_LABEL = "Decoy PIN"
    const val WIPE_PIN_LABEL = "Wipe PIN"
    const val WIPE_PIN_NOTE = "Entering this PIN erases app data. This cannot be undone."
    const val PIN_SET = "Set"
    const val PANIC_LABEL = "Panic gesture"
    const val PANIC_NOTE = "Wipes app data. This cannot be undone."
    const val ALIAS_LABEL = "App icon and name"
    const val ALIAS_NOTE = "Changes how the app looks on your home screen. It does not hide the app from a careful search."
}
