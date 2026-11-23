package com.keymanage.android.feature.vaultlist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carti.ribs.ViewControllable
import com.carti.ribs.compose.ComposeRenderable
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.component.KMCard
import com.keymanage.android.designsystem.component.KMCardPadding
import com.keymanage.android.designsystem.component.KMIcons
import com.keymanage.android.designsystem.component.KMScreen
import com.keymanage.android.designsystem.component.KMTextField
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.domain.vault.VaultItem
import com.keymanage.feature.vaultlist.VaultListMessages
import com.keymanage.feature.vaultlist.VaultListPresentableListener
import com.keymanage.feature.vaultlist.VaultListScreen
import com.keymanage.feature.vaultlist.VaultListViewAction
import com.keymanage.feature.vaultlist.VaultListViewContract
import com.keymanage.feature.vaultlist.VaultListViewState

/**
 * 볼트 목록·상세·폼의 Compose 뷰. 공유 VaultList RIB이 [render]로 상태를 넘기고, 입력은 [listener]로 돌려보낸다.
 * 폼 안의 비밀번호 생성기는 자식 RIB의 화면이라 Router가 [showGenerator]로 붙이면 그 화면을 대신 그린다
 */
class ComposeVaultListView : VaultListViewContract, ComposeRenderable {
    override var listener: VaultListPresentableListener? = null
    private var state by mutableStateOf(VaultListViewState())
    private var generator by mutableStateOf<ViewControllable?>(null)

    override fun render(state: VaultListViewState) {
        this.state = state
    }

    override fun showGenerator(view: ViewControllable) {
        require(view is ComposeRenderable) { "View must implement ComposeRenderable: $view" }
        generator = view
    }

    override fun hideGenerator(view: ViewControllable) {
        if (generator === view) generator = null
    }

    @Composable
    override fun Content() {
        val shownGenerator = generator as? ComposeRenderable
        if (shownGenerator != null) {
            shownGenerator.Content()
        } else {
            VaultListContent(state) { listener?.handle(it) }
        }
    }
}

/** 상태의 [VaultListViewState.screen]에 따라 목록·상세·폼을 그린다 */
@Composable
fun VaultListContent(state: VaultListViewState, onAction: (VaultListViewAction) -> Unit) {
    BackHandler(enabled = state.screen != VaultListScreen.LIST && !state.isSaving) {
        onAction(if (state.screen == VaultListScreen.DETAIL) VaultListViewAction.Back else VaultListViewAction.CancelForm)
    }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state.screen) {
            VaultListScreen.LIST -> ItemListScreen(state, onAction)
            VaultListScreen.DETAIL -> state.selected?.let { ItemDetailScreen(it, state, onAction) }
            VaultListScreen.ADD, VaultListScreen.EDIT -> ItemFormScreen(state, onAction)
        }
        if (state.screen == VaultListScreen.LIST && state.canEdit) {
            FloatingActionButton(
                onClick = { onAction(VaultListViewAction.StartAdd) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(DesignTokens.Spacing.space5)
                    .semantics { contentDescription = "항목 추가" },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
        }
    }
    if (state.isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { onAction(VaultListViewAction.CancelDelete) },
            title = { Text(VaultListMessages.DeleteConfirm.TITLE) },
            text = { Text(VaultListMessages.DeleteConfirm.BODY) },
            confirmButton = {
                TextButton(onClick = { onAction(VaultListViewAction.ConfirmDelete) }) {
                    Text(VaultListMessages.DeleteConfirm.CONFIRM, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { onAction(VaultListViewAction.CancelDelete) }) { Text(VaultListMessages.DeleteConfirm.CANCEL) } },
        )
    }
}

@Composable
private fun ItemListScreen(state: VaultListViewState, onAction: (VaultListViewAction) -> Unit) {
    KMScreen(title = "볼트", onBack = null, scrollable = false) {
        KMTextField(
            label = "검색",
            text = state.query,
            onTextChange = { onAction(VaultListViewAction.Search(it)) },
            placeholder = "서비스 이름이나 아이디",
        )
        state.copyError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        state.emptyMessage?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.Spacing.space6),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space2)) {
            items(state.items, key = { it.id }) { item ->
                ItemRow(
                    item,
                    onCopy = { onAction(VaultListViewAction.CopyPassword(item.id)) },
                    onClick = { onAction(VaultListViewAction.OpenItem(item.id)) },
                )
            }
        }
    }
}

@Composable
private fun ItemRow(item: VaultItem, onCopy: () -> Unit, onClick: () -> Unit) {
    val name = item.serviceName.orEmpty()
    KMCard(modifier = Modifier.fillMaxWidth(), padding = KMCardPadding.SM, onClick = onClick) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(name.take(1), style = MaterialTheme.typography.titleSmall) }
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(
                    item.username?.ifEmpty { null } ?: "(아이디 없음)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCopy) {
                Icon(KMIcons.Copy, contentDescription = "$name 비밀번호 복사", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview
@Composable
private fun EmptyVaultPreview() {
    KeyManageTheme { VaultListContent(VaultListViewState(), onAction = {}) }
}
