package dev.motioncare.data.profile

import android.content.Context
import dev.motioncare.core.model.PersonId
import dev.motioncare.core.model.PersonProfile
import dev.motioncare.data.profile.local.ProfileDatabase
import dev.motioncare.data.profile.storage.SqlitePersonStore
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun createProfileRepository(context: Context): ProfileRepository =
    DefaultProfileRepository(SqlitePersonStore(ProfileDatabase(context.applicationContext)))

internal class DefaultProfileRepository(private val store: SqlitePersonStore) : ProfileRepository {
    override val people = store.people
    private val mutex = Mutex()
    private var initialized = false

    override suspend fun initialize() = mutex.withLock {
        if (!initialized) {
            store.load()
            initialized = true
        }
    }

    override suspend fun addPerson(displayName: String): PersonProfile {
        val name = displayName.trim()
        require(name.isNotEmpty()) { "Person name must not be blank" }
        initialize()
        val person = PersonProfile(PersonId(UUID.randomUUID().toString()), name)
        store.insert(person)
        return person
    }
}
