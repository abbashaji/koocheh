package org.koocheh.model

/** Lowercase, trimmed interest tag, 1..32 chars. */
class InterestTag(raw: String) {
    val value: String = raw.trim().lowercase()

    init {
        require(value.length in 1..MAX_LENGTH) { "InterestTag length must be 1..$MAX_LENGTH" }
    }

    override fun equals(other: Any?): Boolean = other is InterestTag && other.value == value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "InterestTag(redacted)"

    companion object {
        const val MAX_LENGTH = 32
    }
}

class Profile(
    val context: ContextId,
    val nickname: String,
    val ageRange: AgeRange,
    val tags: List<InterestTag>,
    val line: String,
    val photo: ByteArray? = null,
) {
    /** Returns human-readable violations; empty list means valid. Never includes tag or profile content. */
    fun validate(): List<String> {
        val violations = mutableListOf<String>()
        if (nickname.isBlank()) violations += "nickname must not be blank"
        if (tags.size > MAX_TAGS) violations += "at most $MAX_TAGS tags allowed"
        if (line.length > MAX_LINE) violations += "line must be at most $MAX_LINE characters"
        return violations
    }

    override fun equals(other: Any?): Boolean =
        other is Profile &&
            context == other.context &&
            nickname == other.nickname &&
            ageRange == other.ageRange &&
            tags == other.tags &&
            line == other.line &&
            (photo?.contentEquals(other.photo) ?: (other.photo == null))

    override fun hashCode(): Int {
        var h = context.hashCode()
        h = 31 * h + nickname.hashCode()
        h = 31 * h + ageRange.hashCode()
        h = 31 * h + tags.hashCode()
        h = 31 * h + line.hashCode()
        h = 31 * h + (photo?.contentHashCode() ?: 0)
        return h
    }

    override fun toString(): String = "Profile(context=$context, redacted)"

    companion object {
        const val MAX_TAGS = 10
        const val MAX_LINE = 140
    }
}
