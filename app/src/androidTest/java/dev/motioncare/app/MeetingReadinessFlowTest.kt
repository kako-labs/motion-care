package dev.motioncare.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.motioncare.core.model.*
import dev.motioncare.data.environment.createUnsupportedEnvironmentRepository
import dev.motioncare.data.profile.createProfileRepository
import dev.motioncare.data.trip.createSampleTripRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** The runner copies this directory before Gradle uninstalls the disposable test app. */
internal fun captureReadinessScreen(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val outputDirectory = requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir"))
    val output = File(outputDirectory, "$name.png")
    output.parentFile!!.mkdirs()
    val bitmap = instrumentation.uiAutomation.takeScreenshot()
    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
}

/** Exercises the actual app host and public SQLite repositories in a test-only namespace. */
class MeetingReadinessFlowTest {
    @get:Rule val compose = createComposeRule()

    private class TestStorage(base: Context) : ContextWrapper(base) {
        val prefix = "meeting-test-${UUID.randomUUID()}-"
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String): File = baseContext.getDatabasePath(prefix + name)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            baseContext.openOrCreateDatabase(prefix + name, mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?): SQLiteDatabase =
            baseContext.openOrCreateDatabase(prefix + name, mode, factory, handler)
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences(prefix + name, mode)
    }

    private fun awaitText(text: String) {
        try {
            compose.waitUntil(20_000) {
                compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Exception) {
            capture("diagnostic-wait")
            throw AssertionError("Waiting for: $text\n" + compose.onRoot(useUnmergedTree = true).printToString(), error)
        }
    }
    private fun click(text: String) {
        awaitText(text)
        val node = compose.onAllNodesWithText(text, substring = true).onFirst()
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun seat(label: String) {
        val node = compose.onNodeWithContentDescription(label, substring = true)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun back() {
        val node = compose.onNodeWithContentDescription("뒤로")
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun addPerson(seatLabel: String, name: String, child: Boolean = false) {
        seat(seatLabel)
        click("사람 추가")
        compose.onNode(hasSetTextAction()).performTextInput(name)
        if (child) click("어린이")
        click("추가하고 앉히기")
        awaitText(name)
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        captureReadinessScreen(name)
    }
    private fun normalDatabases(context: Context): Map<String, String> = context.databaseList()
        .filterNot { it.startsWith("meeting-test-") }
        .associateWith { name ->
            MessageDigest.getInstance("SHA-256").digest(context.getDatabasePath(name).readBytes())
                .joinToString("") { "%02x".format(it) }
        }

    private fun withStorage(test: (TestStorage) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = TestStorage(target)
        val before = normalDatabases(target)
        try {
            test(storage)
            assertEquals("Normal application databases must stay unchanged", before, normalDatabases(target))
        } finally {
            target.databaseList().filter { it.startsWith(storage.prefix) }.forEach { target.deleteDatabase(it) }
            target.deleteSharedPreferences(storage.prefix + "motion-care-profile-settings")
        }
    }

    @Test fun peopleToSampleTripToFeedbackToNextDeparturePersists() = withStorage { storage ->
        val trips = createSampleTripRepository(storage)
        val profiles = createProfileRepository(storage)
        val environment = createUnsupportedEnvironmentRepository()
        compose.setContent {
            MaterialTheme { MainHost(trips, profiles, environment, false, null, {}) }
        }
        click("함께 타는 사람 · 출발 안내")
        awaitText("탑승 인원 0명")
        addPerson("운전석", "테스트 운전자")
        addPerson("뒤 오른쪽", "테스트 동승자", child = true)
        awaitText("탑승 인원 2명")
        awaitText("미지원")
        capture("01-seating-unsupported")
        click("이번 이동의 제안")
        awaitText("분석할 운전 기록이 더 필요해요.")
        capture("02-no-analysis")
        back()
        click("완료  ✓")
        click("샘플 주행 시작")
        for (version in 1..3) {
            click("샘플 신호 추가")
            awaitText("원본 버전: $version")
        }
        val id = trips.records.value.single { it.status == TripStatus.RECORDING }.id
        compose.waitUntil(20_000) { profiles.participants.value.any { it.tripId == id } }
        val assigned = profiles.participants.value.single { it.tripId == id }
        assertNotNull(assigned.driverId)
        assertEquals(1, assigned.passengerIds.size)
        click("샘플 주행 종료")
        click("이동 평가")
        awaitText("이동 평가")
        // The title renders before the repository-backed passenger state is ready.
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("4점").fetchSemanticsNodes().size == 1
        }
        val rating = compose.onNodeWithContentDescription("4점")
        if (!rating.isDisplayed()) rating.performScrollTo()
        rating.performClick()
        click("출발·정차")
        click("나의 응답 남기기")
        compose.waitUntil(20_000) { profiles.feedback.value.any { it.tripId == id } }
        click("주행 상세")
        awaitText("완료된 운행")
        click("평가 남기기 · 수정")
        awaitText("편안했어요")
        capture("03-saved-feedback")
        back()
        awaitText("운행 상세")
        back()
        awaitText("운행을 마쳤어요")
        compose.onNodeWithText("완료").performScrollTo().performClick()
        click("함께 타는 사람 · 출발 안내")
        awaitText("최근 운행")
        click("이번 이동의 제안")
        awaitText("분석할 기록이 부족해요.")
        capture("04-next-departure-insufficient")
        runBlocking {
            val reopenedProfiles = createProfileRepository(storage).also { it.initialize() }
            val reopenedTrips = createSampleTripRepository(storage).also { it.initialize() }
            assertEquals(2, reopenedProfiles.people.value.size)
            assertEquals(assigned, reopenedProfiles.participants.value.single { it.tripId == id })
            val feedback = reopenedProfiles.feedback.value.single { it.tripId == id }
            assertEquals(FeedbackAnswer.Rating(4, "COMFORT_5_V1"), feedback.answer)
            assertEquals(FeedbackRespondent.GUARDIAN, feedback.respondent)
            assertEquals(setOf(FeedbackReason.START_STOP), feedback.reasons)
            assertEquals(TripStatus.COMPLETED, reopenedTrips.get(id)!!.trip.status)
            assertEquals(3, reopenedTrips.get(id)!!.samples.size)
        }
    }

    @Test fun emptyHistoryAndSkippedFeedbackNeverBecomeAZeroOrARating() = withStorage { storage ->
        val trips = createSampleTripRepository(storage)
        val profiles = createProfileRepository(storage)
        val environment = createUnsupportedEnvironmentRepository()
        runBlocking {
            profiles.initialize()
            val driver = profiles.addPerson("테스트 운전자")
            val child = profiles.addPerson("테스트 동승자", PersonKind.CHILD)
            profiles.placePerson(driver.id, SeatPosition.DRIVER)
            profiles.placePerson(child.id, SeatPosition.REAR_RIGHT)
        }
        compose.setContent {
            MaterialTheme { MainHost(trips, profiles, environment, false, null, {}) }
        }
        click("함께 타는 사람 · 출발 안내")
        click("전체 기록")
        awaitText("아직 기록이 없습니다.")
        capture("05-empty-history")
        back()
        click("완료  ✓")
        click("샘플 주행 시작")
        click("샘플 주행 종료")
        click("이동 평가")
        awaitText("이동은 어땠나요?")
        click("건너뛰기")
        click("이동 평가")
        awaitText("이동은 어땠나요?")
        capture("06-skipped-feedback")
        assertTrue(profiles.feedback.value.isEmpty())
        runBlocking {
            assertTrue(createProfileRepository(storage).also { it.initialize() }.feedback.value.isEmpty())
        }
    }
}
