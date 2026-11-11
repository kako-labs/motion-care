package dev.motioncare.feature.history.ui

import dev.motioncare.core.model.PersonId
import dev.motioncare.core.model.PersonKind
import dev.motioncare.core.model.PersonProfile
import dev.motioncare.feature.history.R
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharactersTest {
    @Test fun `ten characters, women then men, youngest first`() {
        assertEquals(
            listOf(
                "girl_child", "girl_teen", "woman_young", "woman_middle", "woman_senior",
                "boy_child", "boy_teen", "man_young", "man_middle", "man_senior",
            ),
            characterAvatars.map { it.first },
        )
    }

    @Test fun `another character is never the current one`() {
        val random = Random(7)
        repeat(200) { assertNotEquals("man_young", randomCharacter("man_young", random)) }
    }

    @Test fun `a picked character wins over the face for the kind`() {
        val child = PersonProfile(PersonId("p1"), "서아", PersonKind.CHILD, avatar = "woman_senior")
        assertEquals(R.drawable.mc_person_woman_senior, avatarFor(child))
    }

    @Test fun `a key from the earlier character set falls back to the face for the kind`() {
        assertNull(characterDrawable("character_07"))
        val child = PersonProfile(PersonId("p1"), "서아", PersonKind.CHILD, avatar = "character_07")
        assertEquals(avatarFor(child.copy(avatar = null)), avatarFor(child))
    }

    @Test fun `without a pick, children get a child face and adults an adult one`() {
        val childFaces = setOf(R.drawable.mc_person_girl_child, R.drawable.mc_person_boy_child)
        val adultFaces = setOf(R.drawable.mc_person_woman_young, R.drawable.mc_person_man_young)
        listOf("seoa", "doyun", "p1", "p2", "p3").forEach { id ->
            assertTrue(avatarFor(PersonProfile(PersonId(id), id, PersonKind.CHILD)) in childFaces)
            assertTrue(avatarFor(PersonProfile(PersonId(id), id, PersonKind.ADULT)) in adultFaces)
        }
    }
}
