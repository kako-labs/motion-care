package dev.motioncare.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.motioncare.app.home.EnvironmentCard
import dev.motioncare.app.home.EnvironmentItem
import dev.motioncare.app.home.HomeLinks
import dev.motioncare.app.home.HomeScreen
import dev.motioncare.app.home.SeatedPerson
import dev.motioncare.core.ui.MotionTheme
import dev.motioncare.app.home.PeopleSummary
import dev.motioncare.app.home.RecentTripItem
import dev.motioncare.app.home.StartButton
import dev.motioncare.core.model.PersonId
import dev.motioncare.core.model.PersonProfile
import dev.motioncare.core.model.TripId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private val UNSUPPORTED = EnvironmentCard(emptyList(), updatedAt = null, showsAirSource = false, unsupported = true)

class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private var started = 0
    private var peopleOpened = 0
    private var recordsOpened = 0
    private val opened = mutableListOf<TripId>()

    /** Wide screens show everything at once; phones scroll the home column. */
    private fun see(text: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        return node.assertIsDisplayed()
    }

    private fun show(
        start: StartButton = StartButton("샘플 시작", enabled = true) { started += 1 },
        people: PeopleSummary = PeopleSummary(emptyList(), more = 0, registered = 0),
        environment: EnvironmentCard = UNSUPPORTED,
        recent: List<RecentTripItem> = emptyList(),
    ) = compose.setContent {
        MotionTheme {
            HomeScreen(
                vehicleConnected = false,
                people = people,
                environment = environment,
                recent = recent,
                start = start,
                links = HomeLinks(
                    onSeats = { peopleOpened += 1 },
                    onRecords = { recordsOpened += 1 },
                    onProfile = {},
                    onTrip = { opened += it },
                ),
                notice = null,
                guidance = { Text("출발 안내") },
                hero = { Text("히어로 자리") },
            )
        }
    }

    @Test fun startButtonStartsATrip() {
        show()
        compose.onNodeWithText("샘플 시작").performClick()
        assertEquals(1, started)
    }

    @Test fun startButtonIsOffWhileATripIsOpen() {
        show(start = StartButton("기록 중", enabled = false) { started += 1 })
        compose.onNodeWithText("기록 중").assertIsNotEnabled().performClick()
        assertEquals(0, started)
    }

    @Test fun emptyStatesSayWhatIsMissing() {
        show()
        see("합성 샘플 전용")
        see("아직 등록한 사람이 없어요. \"변경\"에서 좌석을 눌러 추가해요.")
        see("이 기기에서는 차량 환경 값을 읽을 수 없어요.")
        see("아직 기록이 없어요. 운행을 시작하면 여기에 쌓여요.")
    }

    @Test fun peopleCardOpensTheProfiles() {
        show(people = PeopleSummary(listOf(SeatedPerson(PersonProfile(PersonId("p1"), "민준"), "운전자")), 0, 1))
        see("민준")
        see("운전자")
        see("변경").performClick()
        assertEquals(1, peopleOpened)
    }

    @Test fun environmentShowsValuesAndTheAirSource() {
        show(
            environment = EnvironmentCard(
                listOf(
                    EnvironmentItem("외부 대기질", "나쁨", hasValue = true, warning = true, note = null),
                    EnvironmentItem("외부 기온", "8°C", hasValue = true, warning = false, note = null),
                    EnvironmentItem("실내 온도", "확인 중", hasValue = false, warning = false, note = null),
                    EnvironmentItem("내기 순환", "꺼짐", hasValue = true, warning = false, note = "오래된 값"),
                ),
                updatedAt = "08:05 갱신",
                showsAirSource = true,
                unsupported = false,
            ),
        )
        listOf("나쁨", "8°C", "확인 중", "꺼짐", "오래된 값", "08:05 갱신", "대기질: Open-Meteo, CAMS 예측").forEach {
            see(it)
        }
    }

    @Test fun recentTripOpensItsDetail() {
        show(recent = listOf(RecentTripItem(TripId("t1"), "11월 2일 08:12", "38분", synthetic = false)))
        see("11월 2일 08:12").performClick()
        assertEquals(listOf(TripId("t1")), opened)
    }

    @Test fun guidanceCardHoldsTheSwitches() {
        show()
        see("안내")
        see("출발 안내")
    }

    @Test fun recordsTabAndFullRecordsLinkOpenTheRecords() {
        show()
        see("기록").performClick()
        see("전체 기록 보기").performClick()
        assertEquals(2, recordsOpened)
    }
}
