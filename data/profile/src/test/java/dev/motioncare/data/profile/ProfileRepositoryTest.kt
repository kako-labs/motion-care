package dev.motioncare.data.profile

import dev.motioncare.core.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun resetDatabase() {
        context.deleteDatabase("motion-care-profile.db")
        context.getSharedPreferences("motion-care-profile-settings", 0).edit().clear().commit()
    }

    @Test fun `departure guidance is on by default and its switch survives a new repository instance`() = runTest {
        val repository = createProfileRepository(context)
        assertTrue(repository.departureGuidanceEnabled.value)

        repository.setDepartureGuidanceEnabled(false)

        assertFalse(createProfileRepository(context).departureGuidanceEnabled.value)
    }

    @Test fun `a picked character survives a new repository instance`() = runTest {
        val repository = createProfileRepository(context)
        val added = repository.addPerson("서아", PersonKind.CHILD)
        repository.updatePerson(added.copy(avatar = "character_07"))

        val reopened = createProfileRepository(context)
        reopened.initialize()

        assertEquals("character_07", reopened.people.value.single().avatar)
    }

    @Test fun `a version 2 database gains the character column and keeps its people`() = runTest {
        // The people table as version 2 created it, before characters existed.
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("motion-care-profile.db"), null).use { db ->
            db.execSQL("""CREATE TABLE people (
                id TEXT PRIMARY KEY, display_name TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'ADULT',
                sensitivity TEXT NOT NULL DEFAULT 'UNSPECIFIED', triggers TEXT NOT NULL DEFAULT '',
                rest_minutes INTEGER, preferred_temperature REAL
            )""")
            db.execSQL("CREATE TABLE current_seats (seat TEXT PRIMARY KEY, person_id TEXT NOT NULL UNIQUE REFERENCES people(id) ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE trip_participants (trip_id TEXT NOT NULL, seat TEXT NOT NULL, person_id TEXT NOT NULL REFERENCES people(id) ON DELETE CASCADE, revision INTEGER NOT NULL, PRIMARY KEY (trip_id, seat), UNIQUE (trip_id, person_id))")
            db.execSQL("CREATE TABLE trip_feedback (trip_id TEXT NOT NULL, target_person_id TEXT NOT NULL REFERENCES people(id) ON DELETE CASCADE, rating INTEGER NOT NULL, scale_version TEXT NOT NULL, reasons TEXT NOT NULL, respondent TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY (trip_id, target_person_id))")
            db.execSQL("INSERT INTO people (id, display_name, kind) VALUES ('p1', '지영', 'ADULT')")
            db.version = 2
        }

        val repository = createProfileRepository(context)
        repository.initialize()

        val person = repository.people.value.single()
        assertEquals("지영", person.displayName)
        assertNull(person.avatar)
        repository.updatePerson(person.copy(avatar = "character_01"))
        assertEquals("character_01", createProfileRepository(context).also { it.initialize() }.people.value.single().avatar)
    }

    @Test fun `people and comfort preferences survive a new repository instance`() = runTest {
        val repository = createProfileRepository(context)
        val added = repository.addPerson("민준")
        repository.updatePerson(added.copy(
            motionSensitivity = MotionSensitivity.HIGH,
            motionTriggers = setOf(MotionTrigger.START_STOP, MotionTrigger.CURVE),
            preferredRestMinutes = 45,
            preferredTemperatureCelsius = 22.5,
        ))

        val reopened = createProfileRepository(context)
        reopened.initialize()

        assertEquals(MotionSensitivity.HIGH, reopened.people.value.single().motionSensitivity)
        assertEquals(45, reopened.people.value.single().preferredRestMinutes)
    }

    @Test fun `people keep the order they were added and names are trimmed`() = runTest {
        val repository = createProfileRepository(context)

        val first = repository.addPerson("  하늘  ")
        val second = repository.addPerson("가온")

        assertEquals(listOf("하늘", "가온"), repository.people.value.map { it.displayName })
        assertNotEquals(first.id, second.id)
    }

    @Test fun `blank name is rejected without saving`() = runTest {
        val repository = createProfileRepository(context)

        try {
            repository.addPerson("   ")
            fail("Expected blank name rejection")
        } catch (_: IllegalArgumentException) { }

        repository.initialize()
        assertTrue(repository.people.value.isEmpty())
    }

    @Test fun `a child cannot be placed in the driver seat`() = runTest {
        val repository = createProfileRepository(context)
        val child = repository.addPerson("서아", PersonKind.CHILD)

        val result = repository.placePerson(child.id, SeatPosition.DRIVER)

        assertTrue(result is SeatingResult.Rejected)
        assertNull(repository.seating.value.driverId)
    }

    @Test fun `trip keeps a snapshot of seating when current seats later change`() = runTest {
        val repository = createProfileRepository(context)
        val driver = repository.addPerson("민준")
        val passenger = repository.addPerson("서아", PersonKind.CHILD)
        repository.placePerson(driver.id, SeatPosition.DRIVER)
        repository.placePerson(passenger.id, SeatPosition.REAR_RIGHT)

        val tripId = TripId("trip-one")
        val assigned = repository.assignCurrentSeating(tripId)
        repository.clearSeat(SeatPosition.REAR_RIGHT)

        assertEquals(driver.id, assigned.driverId)
        assertEquals(passenger.id, assigned.seatAssignments[SeatPosition.REAR_RIGHT])
        val reopened = createProfileRepository(context)
        reopened.initialize()
        assertEquals(assigned, reopened.participants.value.single())
        assertFalse(passenger.id in reopened.seating.value.passengerIds)
    }

    @Test fun `editing a person preserves seating trip participants and feedback`() = runTest {
        val repository = createProfileRepository(context)
        val person = repository.addPerson("서아", PersonKind.CHILD)
        repository.placePerson(person.id, SeatPosition.REAR_RIGHT)
        repository.assignCurrentSeating(TripId("trip-one"))
        repository.saveFeedback(TripFeedback(
            tripId = TripId("trip-one"),
            targetPersonId = person.id,
            answer = FeedbackAnswer.Rating(3, "COMFORT_5_V1"),
            createdAtEpochMs = 1,
        ))

        repository.updatePerson(person.copy(motionSensitivity = MotionSensitivity.HIGH))

        assertEquals(person.id, repository.seating.value.assignments[SeatPosition.REAR_RIGHT])
        assertEquals(person.id, repository.participants.value.single().seatAssignments[SeatPosition.REAR_RIGHT])
        assertEquals(person.id, repository.feedback.value.single().targetPersonId)
    }
    @Test fun `passenger rating reasons and respondent persist`() = runTest {
        val repository = createProfileRepository(context)
        val passenger = repository.addPerson("서아", PersonKind.CHILD)
        val saved = TripFeedback(
            tripId = TripId("trip-one"),
            targetPersonId = passenger.id,
            answer = FeedbackAnswer.Rating(4, "COMFORT_5_V1"),
            reasons = setOf(FeedbackReason.START_STOP, FeedbackReason.FELT_HOT, FeedbackReason.STUFFY_AIR),
            respondent = FeedbackRespondent.GUARDIAN,
            createdAtEpochMs = 1234,
        )
        repository.saveFeedback(saved)

        val reopened = createProfileRepository(context)
        reopened.initialize()

        assertEquals(saved, reopened.feedback.value.single())
    }
}