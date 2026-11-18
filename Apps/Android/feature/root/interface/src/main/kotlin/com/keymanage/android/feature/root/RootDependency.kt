package com.keymanage.android.feature.root

import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.domain.clipboard.ClipboardBurner
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.session.SavedVault
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.feature.auth.AuthBuildable
import com.keymanage.android.feature.unlock.UnlockBuildable
import com.keymanage.android.domain.clipboard.Scheduler
import com.keymanage.android.domain.lock.AutoLockSettingStore
import com.keymanage.android.feature.main.MainBuildable
import kotlinx.coroutines.CoroutineDispatcher

/** Root RIB이 부모(앱)에게서 받는 의존성. */
interface RootDependency {
    val authBuildable: AuthBuildable
    val unlockBuildable: UnlockBuildable
    val mainBuildable: MainBuildable
    val sessionGateway: VaultSessionGateway
    val workDispatcher: CoroutineDispatcher

    /** 잠글 때 바로 비운다(SEC-003 규칙 7, COM-001 규칙 3). */
    val clipboard: ClipboardBurner

    val autoLockStore: AutoLockSettingStore
    val scheduler: Scheduler

    /** 단조 증가 시계(밀리초). 자동 잠금 경과 시간을 잰다. */
    val monotonicClock: () -> Long
    val appEvents: AppEvents
}

/** 앱 생명주기와 사용자 조작. Activity가 알린다. */
interface AppEvents {
    fun setListener(listener: AppEventListener?)
}

interface AppEventListener {
    fun onForeground()
    fun onBackground()
    fun onUserInteraction()
}

/** Root가 쓰는 볼트 세션 기능. 앱은 `VaultSessionUseCase`로 구현한다. 모두 메인 스레드 밖에서 부른다. */
interface VaultSessionGateway {
    /** 재시작 뒤 잠금 화면으로 열 계정 이메일(SEC-003 규칙 9). 없으면 null. */
    fun lockedAccountEmail(): String?

    /** 인증 직후 볼트를 연다. */
    fun openAfterAuth(result: AuthResult): OpenedVault

    /** 로그아웃. 기기 기록을 지우지 못하면 `DeviceStorageError`를 던진다. */
    fun logout(session: Session?)

    /** 잠금이 풀린 볼트의 수정을 저장한다. */
    fun save(opened: OpenedVault, vault: VaultData): SavedVault
}
