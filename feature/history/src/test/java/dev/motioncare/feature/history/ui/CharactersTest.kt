package dev.motioncare.feature.history.ui

import dev.motioncare.core.model.PersonId
import dev.motioncare.core.model.PersonKind
import dev.motioncare.core.model.PersonProfile
import dev.motioncare.feature.history.R
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharactersTest {
    @Test fun `keys are stable and unique`() {
        val keys = characterAvatars.map { it.first }
        assertEquals(24, keys.size)
        assertEquals(keys.size, keys.toSet().size)
        assertEquals("character_01", keys.first())
        assertEquals("character_24", keys.last())
    }

    @Test fun `another character is never the current one`() {
        val random = Random(7)
        repeat(200) { assertNotEquals("character_05", randomCharacter("character_05", random)) }
    }

    @Test fun `a picked character wins over the face for the kind`() {
        val child = PersonProfile(PersonId("p1"), "서아", PersonKind.CHILD, avatar = "character_07")
        assertEquals(R.drawable.mc_character_07, avatarFor(child))
    }

    @Test fun `an unknown key falls back to the face for the kind`() {
        assertNull(characterDrawable("character_99"))
        val child = PersonProfile(PersonId("p1"), "서아", PersonKind.CHILD, avatar = "character_99")
        assertEquals(avatarFor(child.copy(avatar = null)), avatarFor(child))
    }
}
