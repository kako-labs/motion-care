package dev.motioncare.data.profile.storage

import android.content.ContentValues
import dev.motioncare.core.model.PersonId
import dev.motioncare.core.model.PersonProfile
import dev.motioncare.data.profile.local.ProfileDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class SqlitePersonStore(
    private val database: ProfileDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val mutablePeople = MutableStateFlow<List<PersonProfile>>(emptyList())
    val people = mutablePeople.asStateFlow()

    suspend fun load() = withContext(io) { mutex.withLock { refresh() } }

    suspend fun insert(person: PersonProfile) = withContext(io) {
        mutex.withLock {
            database.writableDatabase.insertOrThrow("people", null, ContentValues().apply {
                put("id", person.id.value); put("display_name", person.displayName)
            })
            refresh()
        }
    }

    private fun refresh() {
        mutablePeople.value = database.readableDatabase
            .rawQuery("SELECT id, display_name FROM people ORDER BY rowid", null)
            .use { c -> buildList {
                while (c.moveToNext()) add(PersonProfile(PersonId(c.getString(0)), c.getString(1)))
            } }
    }
}
