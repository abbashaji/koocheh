package org.koocheh.security

/** Innocuous sample data shown in DECOY mode so the dummy view is not suspiciously empty. */
object DecoyContent {

    data class SampleProfile(val displayName: String, val tags: List<String>)

    val tags: List<String> = listOf(
        "hiking", "cooking", "board games", "photography", "book club", "gardening"
    )

    val profiles: List<SampleProfile> = listOf(
        SampleProfile("Sam", listOf("hiking", "photography")),
        SampleProfile("Alex", listOf("cooking", "book club")),
        SampleProfile("Jordan", listOf("board games", "gardening")),
        SampleProfile("Taylor", listOf("hiking", "cooking")),
        SampleProfile("Morgan", listOf("book club", "photography"))
    )
}
