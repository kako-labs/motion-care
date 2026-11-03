package dev.motioncare.data.profile

import dev.motioncare.core.model.PersonProfile
import kotlinx.coroutines.flow.StateFlow

/** Data access contract for people. Vehicles, trip participants and feedback extend this module later. */
interface ProfileRepository {
    val people: StateFlow<List<PersonProfile>>
    suspend fun initialize()
    suspend fun addPerson(displayName: String): PersonProfile
}
