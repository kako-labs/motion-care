package dev.motioncare.feature.history.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.motioncare.core.model.PersonId
import dev.motioncare.core.model.TripId
import dev.motioncare.feature.history.navigation.HistoryRoutes
import dev.motioncare.feature.history.navigation.historyGraph
import dev.motioncare.feature.history.report.FinishedRoute
import dev.motioncare.feature.history.ui.MotionCareBackground

/** Debug source set only. No AppGraph, vehicle SDK, service or user database is constructed. */
class HistoryDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repositories = DemoRepositories(applicationContext, intent.getBooleanExtra("empty", false), intent.getBooleanExtra("unanswered", false))
        setContent { DemoHost(repositories) }
    }
}

@Composable private fun DemoHost(repositories: DemoRepositories) {
    val navigation = rememberNavController()
    val id = remember { TripId("j1") }
    MotionCareBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            NavHost(navigation, startDestination = HistoryRoutes.PROFILES, modifier = Modifier.weight(1f)) {
                historyGraph(navigation, repositories.trips, repositories.profiles,
                    onBackToHome = { navigation.popBackStack(HistoryRoutes.PROFILES, false) })
                composable("demo-finished") {
                    FinishedRoute(id, repositories.trips, repositories.profiles,
                        onDetail = { navigation.navigate(HistoryRoutes.trip(id)) },
                        onFeedback = { navigation.navigate(HistoryRoutes.feedback(id)) },
                        onDone = { navigation.popBackStack(HistoryRoutes.PROFILES, false) })
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("출발 안내" to HistoryRoutes.PROFILES, "운행 기록" to HistoryRoutes.RECORDS,
                    "프로필" to HistoryRoutes.profile(PersonId("jiyoung")), "완료 평가" to HistoryRoutes.feedback(id),
                    "종료 요약" to "demo-finished").forEach { (label, route) ->
                    TextButton(onClick = { navigation.navigate(route) { launchSingleTop = true } }, modifier = Modifier.heightIn(min = 56.dp)) { Text(label) }
                }
            }
        }
    }
}
