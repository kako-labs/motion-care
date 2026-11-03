package dev.motioncare.data.profile

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun `people survive a new repository instance`() = runTest {
        val added = createProfileRepository(context).addPerson("민준")

        val reopened = createProfileRepository(context)
        assertEquals(emptyList<Any>(), reopened.people.value)
        reopened.initialize()

        assertEquals(listOf(added), reopened.people.value)
    }

    @Test fun `people keep the order they were added`() = runTest {
        val repository = createProfileRepository(context)

        val first = repository.addPerson("하늘")
        val second = repository.addPerson("가람")

        assertEquals(listOf(first, second), repository.people.value)
        assertNotEquals(first.id, second.id)
    }

    @Test fun `name is stored without surrounding spaces`() = runTest {
        val added = createProfileRepository(context).addPerson("  서아  ")

        assertEquals("서아", added.displayName)
    }

    @Test fun `blank name is rejected without saving`() = runTest {
        val repository = createProfileRepository(context)

        try {
            repository.addPerson("   ")
            fail("Expected blank name rejection")
        } catch (_: IllegalArgumentException) { }

        repository.initialize()
        assertEquals(emptyList<Any>(), repository.people.value)
    }
}
