package com.keymanage.android.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.carti.ribs.compose.ComposeRenderable
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.component.KMScreen
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.feature.settings.SettingsModal
import com.keymanage.feature.settings.SettingsNoticeKind
import com.keymanage.feature.settings.SettingsPresentableListener
import com.keymanage.feature.settings.SettingsViewAction
import com.keymanage.feature.settings.SettingsViewContract
import com.keymanage.feature.settings.SettingsViewState
import kotlin.time.Duration

/**
 * 설정 화면의 Compose 뷰. 공유 Settings RIB이 [render]로 상태를 넘기고, 입력은 [listener]로 돌려보낸다.
 * 상태와 흐름은 공유 Kotlin이 맡고 여기에는 그리는 일만 둔다 (modularization-plan 2장)
 */
class ComposeSettingsView : SettingsViewContract, ComposeRenderable {
    override var listener: SettingsPresentableListener? = null
    private var state by mutableStateOf(SettingsViewState())

    override fun render(state: SettingsViewState) {
        this.state = state
    }

    @Composable
    override fun Content() {
        SettingsScreen(state) { listener?.handle(it) }
    }
}

/** 설정 화면(목업 7단계). 내보내기 안내·복원·비밀번호 변경·계정 삭제는 상태의 [SettingsViewState.modal]에 따라 대화상자로 띄운다 */
@Composable
fun SettingsScreen(state: SettingsViewState, onAction: (SettingsViewAction) -> Unit) {
    // 자동 잠금 선택 창을 열었는지는 화면만 아는 값이다. 고른 값은 공유 RIB에 보낸다
    var isChoosingAutoLock by remember { mutableStateOf(false) }
    KMScreen(title = "설정", onBack = null) {
        Section("계정") {
            SettingRow("이메일", state.email)
            if (state.serverAddress.isNotEmpty()) SettingRow("서버", state.serverAddress)
            SettingRow("동기화", if (state.syncOffline) "오프라인" else "최신 상태 (동기화 완료)")
        }
        Section("보안") {
            SettingRow("자동 잠금", autoLockLabel(state.autoLockTimeout)) { isChoosingAutoLock = true }
            SettingRow("클립보드 자동 삭제", "30초")
            SettingRow("암호화 구조", "AES-256-GCM")
        }
        Section("백업") {
            SettingRow("백업 내보내기", null, enabled = state.canExportBackup) { onAction(SettingsViewAction.StartExport) }
            SettingRow("백업에서 복원", null, enabled = state.canRestoreBackup) { onAction(SettingsViewAction.StartRestore) }
        }
        Section("세션 제어") {
            SettingRow("잠그기", null) { onAction(SettingsViewAction.RequestLock) }
            SettingRow("마스터 비밀번호 변경", null, enabled = state.canStartChangePassword) { onAction(SettingsViewAction.StartChangePassword) }
            SettingRow("로그아웃", null) { onAction(SettingsViewAction.RequestLogout) }
            SettingRow("계정 삭제", null, color = MaterialTheme.colorScheme.error, enabled = state.canStartDeleteAccount) {
                onAction(SettingsViewAction.StartDeleteAccount)
            }
        }
        // 모달이 열려 있으면 오류는 모달 안에 보인다
        if (state.modal == SettingsModal.NONE) {
            state.error?.let { Message(it, isError = true) }
            state.notice?.let { Message(it, isError = state.noticeKind == SettingsNoticeKind.ERROR) }
        }
    }
    SettingsModalDialog(state, onAction)
    if (isChoosingAutoLock) {
        AutoLockDialog(
            options = state.autoLockOptions,
            selected = state.autoLockTimeout,
            onSelect = { onAction(SettingsViewAction.SelectAutoLock(it)) },
            onClose = { isChoosingAutoLock = false },
        )
    }
}

/** 자동 잠금 시간 표시. 선택지는 공유 도메인이 정하고 여기서는 글자로만 바꾼다 (SEC-003 값 표) */
internal fun autoLockLabel(timeout: Duration): String = when {
    timeout == Duration.ZERO -> "즉시"
    timeout.inWholeMinutes >= 60 && timeout.inWholeMinutes % 60 == 0L -> "${timeout.inWholeHours}시간"
    else -> "${timeout.inWholeMinutes}분"
}

@Composable
private fun AutoLockDialog(options: List<Duration>, selected: Duration, onSelect: (Duration) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("자동 잠금") },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(autoLockLabel(option), modifier = Modifier.padding(start = DesignTokens.Spacing.space2))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } },
    )
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space2)) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column { content() }
    }
}

@Composable
private fun SettingRow(label: String, value: String?, color: Color = Color.Unspecified, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(enabled = enabled, onClick = onClick) else it }
            .padding(vertical = DesignTokens.Spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun Message(text: String, isError: Boolean) {
    Text(
        text,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    KeyManageTheme { SettingsScreen(SettingsViewState(email = "dev@example.com", serverAddress = "https://keymanage.example"), onAction = {}) }
}
