package com.keymanage.android.feature.settings.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.keymanage.android.designsystem.component.KMScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.android.domain.lock.AutoLockOption
import com.keymanage.android.feature.settings.AccountPort
import com.keymanage.android.feature.settings.BackupPort
import com.keymanage.android.feature.settings.BiometricPort
import com.keymanage.android.feature.settings.SettingsBuildable
import com.keymanage.android.feature.settings.SettingsDependency
import com.keymanage.android.feature.settings.SettingsListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicComposeRouter
import com.uber.rib.core.ComposePresenter
import com.uber.rib.core.Router

class SettingsPresenter(
    private val store: ViewStateStore<SettingsViewState>,
    private val actions: ViewActionStream<SettingsViewAction>,
) : ComposePresenter() {
    override val composable: @Composable () -> Unit = {
        val state by store.state.collectAsState()
        SettingsView(state = state, onAction = { actions.send(it) })
    }
}

class SettingsBuilder(private val dependency: SettingsDependency) : SettingsBuildable {
    override fun build(slot: MutableState<@Composable () -> Unit>, email: String, backup: BackupPort, account: AccountPort, biometric: BiometricPort, listener: SettingsListener): Router<*> {
        val store = ViewStateStore(SettingsViewState(email = email))
        val actions = ViewActionStream<SettingsViewAction>()
        val presenter = SettingsPresenter(store, actions)
        val interactor = SettingsInteractor(
            presenter, store, actions, dependency.autoLockStore, dependency.pendingChanges, listener,
            backup, dependency.documents, dependency.workDispatcher, dependency.clock, dependency.today, account, biometric,
        )
        return BasicComposeRouter(presenter, interactor, slot)
    }
}

/** 설정 화면(목업 7단계). 백업·비밀번호 변경·계정 삭제는 하위 화면으로 연다. */
@Composable
fun SettingsView(state: SettingsViewState, onAction: (SettingsViewAction) -> Unit) {
    when (state.screen) {
        SettingsScreen.BACKUP -> return BackupView(state.backup, onAction)
        SettingsScreen.PASSWORD -> return PasswordChangeView(state.password, onAction)
        SettingsScreen.DELETE -> Unit
        SettingsScreen.MAIN -> Unit
    }
    KMScreen(title = "설정", onBack = null) {
        Section("계정") { SettingRow("이메일", state.email) }
        Section("보안") {
            SettingRow("자동 잠금", state.autoLock.label) { onAction(SettingsViewAction.OpenAutoLock) }
            if (state.biometric.isAvailable) BiometricRow(state.biometric, onAction)
            SettingRow("클립보드 자동 삭제", "30초")
            SettingRow("암호화 구조", "AES-256-GCM")
        }
        Section("백업") {
            SettingRow("백업 내보내기", null) { onAction(SettingsViewAction.OpenBackup) }
            SettingRow("백업에서 복원", null) { onAction(SettingsViewAction.OpenBackup) }
        }
        Section("세션 제어") {
            SettingRow("마스터 비밀번호 변경", null) { onAction(SettingsViewAction.OpenPasswordChange) }
            SettingRow("로그아웃", null) { onAction(SettingsViewAction.Logout) }
            SettingRow("계정 삭제", null, color = MaterialTheme.colorScheme.error) { onAction(SettingsViewAction.OpenDeleteAccount) }
        }
    }
    if (state.screen == SettingsScreen.DELETE) DeleteAccountView(state.delete, onAction)
    if (state.isChoosingAutoLock) {
        AlertDialog(
            onDismissRequest = { onAction(SettingsViewAction.DismissDialog) },
            title = { Text("자동 잠금") },
            text = {
                Column {
                    AutoLockOption.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = option == state.autoLock, role = Role.RadioButton) {
                                    onAction(SettingsViewAction.ChooseAutoLock(option))
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == state.autoLock, onClick = null)
                            Text(option.label, modifier = Modifier.padding(start = DesignTokens.Spacing.space2))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onAction(SettingsViewAction.DismissDialog) }) { Text("닫기") } },
        )
    }
    if (state.isConfirmingLogout) {
        AlertDialog(
            onDismissRequest = { onAction(SettingsViewAction.DismissDialog) },
            title = { Text(SettingsMessages.PENDING_LOGOUT_TITLE) },
            text = { Text(SettingsMessages.PENDING_LOGOUT_BODY) },
            confirmButton = {
                TextButton(onClick = { onAction(SettingsViewAction.ConfirmLogout) }) {
                    Text("로그아웃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { onAction(SettingsViewAction.DismissDialog) }) { Text("취소") } },
        )
    }
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
private fun SettingRow(label: String, value: String?, color: Color = Color.Unspecified, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = DesignTokens.Spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = color)
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** 생체 인증 스위치(SEC-003 값 표: 기본 꺼짐). 켤 때 생체 인증 창이 뜬다. */
@Composable
private fun BiometricRow(state: BiometricState, onAction: (SettingsViewAction) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = state.isEnabled,
                    enabled = !state.isWorking,
                    role = Role.Switch,
                    onValueChange = { onAction(SettingsViewAction.ToggleBiometric(it)) },
                )
                .padding(vertical = DesignTokens.Spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("생체 인증으로 잠금 해제", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = state.isEnabled, onCheckedChange = null, enabled = !state.isWorking)
        }
        state.message?.let {
            Text(
                it,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Preview
@Composable
private fun SettingsViewPreview() {
    KeyManageTheme { SettingsView(SettingsViewState(email = "dev@example.com"), onAction = {}) }
}
