package dev.motioncare.feature.history.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.motioncare.core.ui.ScaffoldSection
import dev.motioncare.data.profile.ProfileRepository

@Composable
internal fun ProfileRoute(repository: ProfileRepository, onBack: () -> Unit) {
    val model: ProfileViewModel = viewModel(factory = viewModelFactory {
        initializer { ProfileViewModel(repository) }
    })
    val state by model.uiState.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        ProfileContent(state, onRetry = model::retry, onBack = onBack)
    }
}

@Composable
private fun ProfileContent(state: ProfileUiState, onRetry: () -> Unit, onBack: () -> Unit) {
    ScaffoldSection("프로필") {
        when {
            state.loading -> Text("프로필 확인 중")
            state.failed -> {
                Text("프로필을 불러오지 못했습니다.")
                Button(onClick = onRetry) { Text("다시 시도") }
            }
            state.people.isEmpty() -> Text("등록된 사람이 없습니다.")
            else -> state.people.forEach { Text(it.displayName) }
        }
        Text("사람·차량 등록과 주행별 인물 연결은 아직 구현하지 않았습니다.")
        Button(onClick = onBack) { Text("돌아가기") }
    }
}
