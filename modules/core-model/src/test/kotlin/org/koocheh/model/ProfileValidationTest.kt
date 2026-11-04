package org.koocheh.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileValidationTest {
    private fun profile(
        tags: List<InterestTag> = listOf(InterestTag("Chess")),
        line: String = "hi",
        nickname: String = "sam",
    ) = Profile(ContextId.SOCIAL, nickname, AgeRange.R25_34, tags, line)

    @Test
    fun validProfileHasNoViolations() {
        assertTrue(profile().validate().isEmpty())
    }

    @Test
    fun tooManyTagsIsViolation() {
        val tags = (1..11).map { InterestTag("t$it") }
        assertEquals(1, profile(tags = tags).validate().size)
    }

    @Test
    fun longLineIsViolation() {
        assertEquals(1, profile(line = "x".repeat(141)).validate().size)
        assertTrue(profile(line = "x".repeat(140)).validate().isEmpty())
    }

    @Test
    fun blankNicknameIsViolation() {
        assertEquals(1, profile(nickname = "  ").validate().size)
    }

    @Test
    fun tagIsNormalisedAndBounded() {
        assertEquals("chess", InterestTag("  Chess ").value)
        assertFailsWith<IllegalArgumentException> { InterestTag("   ") }
        assertFailsWith<IllegalArgumentException> { InterestTag("a".repeat(33)) }
    }

    @Test
    fun ephemeralIdContentEqualityAndRedaction() {
        val a = EphemeralId(byteArrayOf(1, 2, 3))
        val b = EphemeralId(byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a.toString().contains("1"))
    }

    @Test
    fun fakeClockAdvances() {
        val c = FakeClock()
        c.advance(50)
        assertEquals(50, c.nowMs())
        assertEquals(50, c.elapsedMs())
    }
}
