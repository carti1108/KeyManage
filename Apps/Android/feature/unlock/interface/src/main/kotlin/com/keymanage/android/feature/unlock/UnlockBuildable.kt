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

    /**
     * 되돌림 경고(`SyncError.RevisionRollback`)를 사용자가 확인했다(VLT-001 규칙 9). 그 뒤 잠금 해제를 다시 부른다.
     * 기본 구현은 받아들이지 않아 다음 잠금 해제도 같은 경고로 끝난다.
     */
    fun acceptRollback(serverRevision: Int) = Unit
}

/**
 * 생체 인증으로 감싼 볼트 키를 꺼내 기기 볼트를 연다(SEC-003 규칙 4, ADR-AND-002).
 * 생체 인증 창은 메인 스레드에서 띄우고, 무거운 일은 구현이 메인 스레드 밖으로 옮긴다.
 */
interface BiometricUnlocker {
    /** 켜져 있고 키가 있으며 14일이 지나지 않았는지. */
    fun isAvailable(): Boolean

    /** 실패는 `BiometricError`·`UnlockError`·`ApiError`로 던진다. */
    suspend fun unlock(session: Session?): OpenedVault
}

interface UnlockDependency {
    val unlocker: Unlocker
    val workDispatcher: CoroutineDispatcher

    /** 생체 해제. 없으면 마스터 비밀번호만 쓴다. */
    val biometricUnlocker: BiometricUnlocker? get() = null
}

interface UnlockListener {
    /** 잠금이 풀렸다. [OpenedVault.encryptionKey]의 소유권은 받은 쪽으로 넘어간다. */
    fun onUnlocked(opened: OpenedVault)

    /** 마스터 비밀번호가 바뀌었거나 기기 기록이 없어 로그인 화면으로 가야 한다(SEC-003 규칙 11). */
    fun onLoginRequired(message: String?)

    /** 잠금 화면에서 로그아웃을 눌렀다. */
    fun onLogoutRequested()
}
