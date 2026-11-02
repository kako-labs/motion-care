package dev.motioncare.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScaffoldSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
@Composable
fun FeatureSlot(title: String, description: String, onBack: () -> Unit) {
    ScaffoldSection(title) {
        Text(description)
        Text("기능 연결 자리입니다. 실제 기능은 아직 구현하지 않았습니다.")
        Button(onClick = onBack) { Text("돌아가기") }
    }
}
