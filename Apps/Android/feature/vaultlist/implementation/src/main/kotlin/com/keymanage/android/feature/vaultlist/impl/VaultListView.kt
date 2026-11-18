package com.keymanage.android.feature.vaultlist.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.component.KMBurnBadge
import com.keymanage.android.designsystem.generated.DesignTokens
import com.keymanage.android.domain.vault.ItemFields

/** 볼트 RIB 화면. 상태의 [VaultListViewState.screen]에 따라 목록·상세·편집을 그린다. */
@Composable
fun VaultListView(state: VaultListViewState, onAction: (VaultListViewAction) -> Unit) {
    BackHandler(enabled = state.screen != VaultScreen.List) { onAction(VaultListViewAction.Back) }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = DesignTokens.Spacing.space4)) {
            Banners(state, onAction)
            when (val screen = state.screen) {
                VaultScreen.List -> VaultItemList(state, onAction)
                is VaultScreen.Detail -> ItemDetailScreen(screen, state, onAction)
                is VaultScreen.Edit -> ItemFormScreen(screen, state, onAction)
            }
        }
        if (state.screen == VaultScreen.List && !state.isReadOnly) {
            FloatingActionButton(
                onClick = { onAction(VaultListViewAction.StartAdd) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(DesignTokens.Spacing.space5)
                    .semantics { contentDescription = "항목 추가" },
            ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
        }
    }
    if (state.isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { onAction(VaultListViewAction.CancelDelete) },
            title = { Text(VaultMessages.DELETE_TITLE) },
            text = { Text(VaultMessages.DELETE_BODY) },
            confirmButton = {
                TextButton(onClick = { onAction(VaultListViewAction.ConfirmDelete) }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { onAction(VaultListViewAction.CancelDelete) }) { Text("취소") } },
        )
    }
}

@Composable
private fun Banners(state: VaultListViewState, onAction: (VaultListViewAction) -> Unit) {
    Column(modifier = Modifier.padding(top = DesignTokens.Spacing.space3), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space2)) {
        if (state.isOffline) {
            Notice(VaultMessages.RESYNC_NOTICE, action = "동기화") { onAction(VaultListViewAction.Resync) }
        }
        if (state.isReadOnly) Notice(VaultMessages.READ_ONLY)
        state.errorMessage?.let { Notice(it, isError = true, action = "닫기") { onAction(VaultListViewAction.DismissError) } }
        state.burnSeconds?.let { KMBurnBadge(remainingSeconds = it) }
    }
}

@Composable
internal fun Notice(text: String, isError: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(DesignTokens.Radius.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(DesignTokens.Spacing.space3), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
            )
            action?.let { TextButton(onClick = onAction) { Text(it) } }
        }
    }
}

@Composable
internal fun TopBar(title: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = DesignTokens.Spacing.space3), verticalAlignment = Alignment.CenterVertically) {
        onBack?.let { TextButton(onClick = it) { Text("뒤로") } }
        Text(title, modifier = Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        trailing()
    }
}

@Composable
private fun VaultItemList(state: VaultListViewState, onAction: (VaultListViewAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space3)) {
        TopBar("볼트", onBack = null) { TextButton(onClick = { onAction(VaultListViewAction.Lock) }) { Text("잠금") } }
        androidx.compose.material3.OutlinedTextField(
            value = state.query,
            onValueChange = { onAction(VaultListViewAction.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("검색") },
            placeholder = { Text("서비스 이름이나 아이디") },
            singleLine = true,
        )
        Text("항목 ${state.totalCount}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space2)) {
            items(state.rows, key = { it.id }) { row -> VaultRowItem(row) { onAction(VaultListViewAction.OpenItem(row.id)) } }
        }
    }
}

@Composable
private fun VaultRowItem(row: VaultRow, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(DesignTokens.Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = DesignTokens.Spacing.space4, vertical = DesignTokens.Spacing.space3),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(row.serviceName.take(1), style = MaterialTheme.typography.titleSmall) }
            Column(modifier = Modifier.weight(1f)) {
                Text(row.serviceName, style = MaterialTheme.typography.titleSmall)
                Text(row.username ?: "(아이디 없음)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview
@Composable
private fun VaultListViewPreview() {
    KeyManageTheme {
        VaultListView(
            VaultListViewState(rows = listOf(VaultRow("1", "GitHub", "dev@example.com"), VaultRow("2", "은행", null)), totalCount = 2, isOffline = true),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun ItemDetailPreview() {
    KeyManageTheme {
        VaultListView(
            VaultListViewState(screen = VaultScreen.Detail("1", ItemFields("GitHub", "dev@example.com", "secret")), burnSeconds = 28),
            onAction = {},
        )
    }
}
