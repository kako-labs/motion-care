package dev.motioncare.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import dev.motioncare.core.model.DataOrigin
import dev.motioncare.core.model.TripStatus
import kotlinx.coroutines.runBlocking

class ScaffoldFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitText(text: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun click(text: String) {
        awaitText(text)
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    @Test fun profileScreenReadsTheProfileStoreAndReturnsHome() {
        click("함께 타는 사람 · 출발 안내")
        awaitText("출발 전 안내")
        awaitText("탑승 인원 0명")
        awaitText("차량 좌석을 눌러 운전자와 동승자를 선택해 주세요.")
        compose.onNodeWithContentDescription("뒤로").performClick()
        awaitText("샘플 주행 시작")
    }

    @Test fun sampleTripSurvivesRecreationAndFeedbackReturnsToItsDetail() {
        click("샘플 주행 시작")
        for (version in 1..3) {
            click("샘플 신호 추가")
            awaitText("원본 버전: $version")
        }
        val repository = (compose.activity.application as MotionCareApplication).graph.trips
        val id = repository.records.value.single { it.status == TripStatus.RECORDING }.id
        assertEquals(3, runBlocking { repository.get(id) }!!.samples.size)
        compose.activityRule.scenario.recreate()
        awaitText("원본 버전: 3")
        val recreatedRepository = (compose.activity.application as MotionCareApplication).graph.trips
        assertEquals(3, runBlocking { recreatedRepository.get(id) }!!.samples.size)
        click("샘플 주행 종료")
        click("주행 상세")
        awaitText("완료된 운행")
        click("동승자 평가")
        awaitText("평가할 동승자가 없어요.")
        click("완료")
        awaitText("운행 상세")
        awaitText("완료된 운행")
        val stored = runBlocking { recreatedRepository.get(id) }
        assertNotNull(stored)
        assertEquals(3, stored!!.samples.size)
        assertEquals(DataOrigin.SYNTHETIC, stored.trip.origin)
        assertEquals(TripStatus.COMPLETED, stored.trip.status)
    }
}
