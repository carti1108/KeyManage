package com.keymanage.android.feature.unlock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.uber.rib.core.Router
import kotlinx.coroutines.CoroutineDispatcher

/** 잠금 화면 RIB(SEC-003). [session]은 잠기기 전 세션이며 없으면(재시작 뒤) 잠금 해제 때 다시 로그인한다. */
interface UnlockBuildable {
    fun build(slot: MutableState<@Composable () -> Unit>, email: String, session: Session?, listener: UnlockListener): Router<*>
}

/** 마스터 비밀번호로 기기 볼트를 연다. 실패는 `UnlockError`·`ApiError`로 던진다. */
fun interface Unlocker {
    fun unlock(password: String, session: Session?): OpenedVault
}

interface UnlockDependency {
    val unlocker: Unlocker
    val workDispatcher: CoroutineDispatcher
}

interface UnlockListener {
    /** 잠금이 풀렸다. [OpenedVault.encryptionKey]의 소유권은 받은 쪽으로 넘어간다. */
    fun onUnlocked(opened: OpenedVault)

    /** 마스터 비밀번호가 바뀌었거나 기기 기록이 없어 로그인 화면으로 가야 한다(SEC-003 규칙 11). */
    fun onLoginRequired(message: String?)

    /** 잠금 화면에서 로그아웃을 눌렀다. */
    fun onLogoutRequested()
}
