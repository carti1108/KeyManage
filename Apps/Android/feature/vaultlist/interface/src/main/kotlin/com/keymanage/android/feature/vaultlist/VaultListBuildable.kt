package com.keymanage.android.feature.vaultlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.keymanage.android.domain.vault.VaultData
import com.uber.rib.core.Router

/**
 * 볼트 목록 RIB(목업 4단계). 평문 볼트는 이 RIB이 붙어 있는 동안만 화면 상태에 둔다.
 * [isOffline]이면 세션 없이 연 상태라 다시 입력 안내를 보인다(SEC-003 규칙 12).
 */
interface VaultListBuildable {
    fun build(slot: MutableState<@Composable () -> Unit>, vault: VaultData, isOffline: Boolean, listener: VaultListListener): Router<*>
}

interface VaultListListener {
    /** 잠금 버튼을 눌렀다. */
    fun onLockRequested()

    /** 세션 없이 열린 상태에서 '동기화'를 눌렀다. 마스터 비밀번호를 다시 받는다. */
    fun onResyncRequested()
}
