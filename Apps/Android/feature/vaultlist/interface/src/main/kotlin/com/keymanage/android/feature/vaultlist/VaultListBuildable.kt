package com.keymanage.android.feature.vaultlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.keymanage.android.domain.clipboard.ClipboardBurner
import com.keymanage.android.domain.session.SavedVault
import com.keymanage.android.domain.vault.VaultData
import com.uber.rib.core.Router
import kotlinx.coroutines.CoroutineDispatcher
import java.time.Instant

/**
 * 볼트 목록·항목 상세·편집 RIB(목업 4·5단계). 평문 볼트는 이 RIB이 붙어 있는 동안만 화면 상태에 둔다.
 * [isOffline]이면 세션 없이 연 상태라 다시 입력 안내를 보인다(SEC-003 규칙 12).
 * 저장은 키를 쥔 부모가 [saver]로 맡는다.
 */
interface VaultListBuildable {
    fun build(
        slot: MutableState<@Composable () -> Unit>,
        vault: VaultData,
        isOffline: Boolean,
        saver: VaultSaver,
        listener: VaultListListener,
    ): Router<*>
}

/** 수정한 볼트를 저장한다. 실패는 `SyncError`·`ApiError`로 던진다. 메인 스레드 밖에서 부른다. */
fun interface VaultSaver {
    fun save(vault: VaultData): SavedVault
}

interface VaultListDependency {
    val clipboard: ClipboardBurner
    val workDispatcher: CoroutineDispatcher
    val clock: () -> Instant
}

interface VaultListListener {
    /** 잠금 버튼을 눌렀다. */
    fun onLockRequested()

    /** 세션 없이 열린 상태에서 '동기화'를 눌렀거나 저장 중 세션이 만료됐다. 마스터 비밀번호를 다시 받는다. */
    fun onResyncRequested()
}
