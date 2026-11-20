package com.keymanage.android.feature.main.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.carti.ribs.ViewControllable
import com.carti.ribs.compose.ComposeRenderable
import com.carti.ribs.compose.ComposeScreenSlot
import com.carti.ribs.compose.RibSlot
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.component.KMButton
import com.keymanage.android.designsystem.component.KMIcons
import com.keymanage.android.designsystem.component.KMPasswordField
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.feature.main.MainPresentableListener
import com.keymanage.feature.main.MainTab
import com.keymanage.feature.main.MainViewAction
import com.keymanage.feature.main.MainViewContract
import com.keymanage.feature.main.MainViewState
import com.keymanage.feature.messages.ErrorMessage
import com.keymanage.feature.messages.PendingLogoutConfirm
import com.keymanage.feature.messages.ReconnectPrompt
import com.keymanage.feature.messages.RollbackWarning

/**
 * 볼트를 연 뒤의 화면 틀(탭, 안내, 다시 연결)의 Compose 뷰. 공유 Main RIB이 [render]로 상태를 넘기고, 입력은 [listener]로 돌려보낸다.
 * 탭 안의 화면은 자식 RIB의 화면이라 Router가 [showScreen]으로 붙이는 것을 그린다 (modularization-plan 2장)
 */
class ComposeMainView : MainViewContract, ComposeRenderable {
    override var listener: MainPresentableListener? = null
    private var state by mutableStateOf(MainViewState())
    private val slot = ComposeScreenSlot()

    override fun render(state: MainViewState) {
        this.state = state
    }

    override fun showScreen(view: ViewControllable) = slot.show(view)

    override fun hideScreen(view: ViewControllable) = slot.hide(view)

    @Composable
    override fun Content() {
        MainScreen(state, onAction = { listener?.handle(it) }) { RibSlot(slot) }
    }
}

/** 위에는 안내, 가운데는 고른 탭의 화면, 아래는 탭 바 */
@Composable
fun MainScreen(state: MainViewState, onAction: (MainViewAction) -> Unit, tabContent: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Notices(state, onAction)
        Box(modifier = Modifier.weight(1f)) { tabContent() }
        NavigationBar {
            MainTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == state.selectedTab,
                    onClick = { onAction(MainViewAction.SelectTab(tab)) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(tab.label) },
                )
            }
        }
    }
    if (state.isConfirmingReconnectRollback) {
        ConfirmDialog(
            title = RollbackWarning.TITLE,
            body = RollbackWarning.BODY,
            confirm = RollbackWarning.ACCEPT,
            cancel = RollbackWarning.CANCEL,
            onConfirm = { onAction(MainViewAction.AcceptReconnectRollback) },
            onCancel = { onAction(MainViewAction.CancelReconnectRollback) },
        )
    }
    // 올리지 못한 변경 사항이 있을 때 로그아웃 전에 묻는다 (VLT-002)
    if (state.isConfirmingLogout) {
        ConfirmDialog(
            title = PendingLogoutConfirm.TITLE,
            body = PendingLogoutConfirm.BODY,
            confirm = PendingLogoutConfirm.CONFIRM,
            cancel = PendingLogoutConfirm.CANCEL,
            onConfirm = { onAction(MainViewAction.ConfirmLogout) },
            onCancel = { onAction(MainViewAction.CancelLogout) },
        )
    }
}

@Composable
private fun Notices(state: MainViewState, onAction: (MainViewAction) -> Unit) {
    val hasNotice = state.offlineNotice != null || state.readOnlyNotice != null || state.deviceStorageWarning != null ||
        state.syncError != null || state.needsReconnect
    if (!hasNotice) return
    Column(
        modifier = Modifier.padding(horizontal = DesignTokens.Spacing.space4).padding(top = DesignTokens.Spacing.space3),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space2),
    ) {
        state.offlineNotice?.let { Notice(it) }
        state.readOnlyNotice?.let { Notice(it) }
        state.deviceStorageWarning?.let { Notice(it, isError = true) }
        state.syncError?.let { Notice(it.text(), isError = true) }
        if (state.needsReconnect) ReconnectCard(state, onAction)
    }
}

/** 세션 없이 연 볼트를 마스터 비밀번호로 다시 연결한다 (SEC-003, ADR-0019) */
@Composable
private fun ReconnectCard(state: MainViewState, onAction: (MainViewAction) -> Unit) {
    NoticeSurface(isError = false) {
        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space2)) {
            Text(ReconnectPrompt.BODY, style = MaterialTheme.typography.bodySmall)
            KMPasswordField(
                text = state.reconnectPassword,
                onTextChange = { onAction(MainViewAction.UpdateReconnectPassword(it)) },
                label = "마스터 비밀번호",
                placeholder = "",
                showCopyAction = false,
                readOnly = state.isReconnecting,
            )
            state.reconnectError?.let {
                Text(
                    it.text(),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            KMButton(
                title = ReconnectPrompt.ACTION,
                onClick = { onAction(MainViewAction.Reconnect) },
                modifier = Modifier.fillMaxWidth(),
                isLoading = state.isReconnecting,
                isDisabled = state.reconnectPassword.isEmpty(),
            )
        }
    }
}

@Composable
private fun Notice(text: String, isError: Boolean = false) {
    NoticeSurface(isError) {
        Text(text, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NoticeSurface(isError: Boolean, content: @Composable () -> Unit) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(DesignTokens.Radius.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.padding(DesignTokens.Spacing.space3)) { content() }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, cancel: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(cancel) } },
    )
}

private fun ErrorMessage.text(): String = title?.let { "$it\n$body" } ?: body

/** 탭 이름과 아이콘(platform-bindings.md 아이콘 표) */
private val MainTab.label: String
    get() = when (this) {
        MainTab.VAULT -> "볼트"
        MainTab.GENERATOR -> "생성기"
        MainTab.SETTINGS -> "설정"
    }

private val MainTab.icon: ImageVector
    get() = when (this) {
        MainTab.VAULT -> KMIcons.Lock
        MainTab.GENERATOR -> KMIcons.Key
        MainTab.SETTINGS -> KMIcons.Settings
    }

@Preview
@Composable
private fun MainScreenPreview() {
    KeyManageTheme { MainScreen(MainViewState(offline = true, needsReconnect = true), onAction = {}) {} }
}
