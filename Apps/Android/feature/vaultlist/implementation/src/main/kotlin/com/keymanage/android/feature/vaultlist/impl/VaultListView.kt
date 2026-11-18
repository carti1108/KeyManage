package com.keymanage.android.feature.vaultlist.impl

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.keymanage.android.designsystem.KeyManageTheme
import com.keymanage.android.designsystem.generated.DesignTokens

/** 다시 입력 요청 문구(SEC-003 규칙 12). */
const val RESYNC_NOTICE = "서버와 동기화하려면 마스터 비밀번호를 다시 입력해 주세요."

/** 볼트 목록(목업 4단계). */
@Composable
fun VaultListView(state: VaultListViewState, onAction: (VaultListViewAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = DesignTokens.Spacing.space4),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space3),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = DesignTokens.Spacing.space4), verticalAlignment = Alignment.CenterVertically) {
            Text("볼트", modifier = Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = { onAction(VaultListViewAction.Lock) }) { Text("잠금") }
        }
        if (state.isOffline) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(DesignTokens.Radius.md)) {
                Row(modifier = Modifier.padding(DesignTokens.Spacing.space3), verticalAlignment = Alignment.CenterVertically) {
                    Text(RESYNC_NOTICE, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onAction(VaultListViewAction.Resync) }) { Text("동기화") }
                }
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = { onAction(VaultListViewAction.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("검색") },
            placeholder = { Text("서비스 이름이나 아이디") },
            singleLine = true,
        )
        Text("항목 ${state.totalCount}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.space2)) {
            items(state.rows, key = { it.id }) { row -> VaultRowItem(row) }
        }
    }
}

@Composable
private fun VaultRowItem(row: VaultRow) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
            ) {
                Text(row.serviceName.take(1), style = MaterialTheme.typography.titleSmall)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(row.serviceName, style = MaterialTheme.typography.titleSmall)
                Text(
                    row.username ?: "(아이디 없음)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun VaultListViewPreview() {
    KeyManageTheme {
        VaultListView(
            VaultListViewState(
                rows = listOf(VaultRow("1", "GitHub", "dev@example.com"), VaultRow("2", "은행", null)),
                totalCount = 2,
                isOffline = true,
            ),
            onAction = {},
        )
    }
}
