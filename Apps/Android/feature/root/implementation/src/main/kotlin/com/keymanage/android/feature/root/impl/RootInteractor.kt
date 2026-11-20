package com.keymanage.android.feature.root.impl

import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.domain.sync.SyncError
import com.keymanage.android.feature.auth.AuthListener
import com.keymanage.android.feature.root.VaultSessionGateway
import com.keymanage.android.feature.unlock.UnlockListener
import com.keymanage.android.domain.lock.AutoLockOption
import com.keymanage.android.feature.main.MainListener
import com.keymanage.android.feature.root.AppEvents
import com.keymanage.android.feature.root.BiometricGateway
import com.keymanage.android.domain.lock.BiometricError
import com.keymanage.android.feature.settings.AccountPort
import com.keymanage.android.feature.settings.BiometricPort
import com.keymanage.android.feature.settings.BackupPort
import com.keymanage.android.feature.settings.RestoreOutcome
import java.time.Instant
import com.keymanage.android.feature.vaultlist.VaultSaver
import com.keymanage.android.domain.session.SavedVault
import com.keymanage.android.domain.vault.VaultData
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicInteractor
import com.uber.rib.core.Bundle
import com.uber.rib.core.EmptyPresenter
import com.uber.rib.core.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Root 문구(SEC-003·COM-002). */
object RootMessages {
    const val VAULT_NOT_READY = "계정은 만들었지만 볼트를 준비하지 못했습니다. 다시 로그인해 주세요."
    const val ACCOUNT_DELETED = "계정을 삭제했습니다."
    const val SESSION_EXPIRED = "로그인이 만료되었습니다. 다시 로그인해 주세요."
    const val BIOMETRIC_PROMPT_TITLE = "생체 인증으로 잠금을 풀까요?"
    const val BIOMETRIC_PROMPT_BODY = "잠금 화면에서 마스터 비밀번호 대신 생체 인증을 쓸 수 있습니다. 14일마다 한 번은 마스터 비밀번호를 입력해야 합니다. 설정에서 언제든 바꿀 수 있습니다."
    const val BIOMETRIC_UNAVAILABLE = "생체 인증을 켜지 못했습니다. 설정에서 다시 시도해 주세요."
    const val ROLLBACK_TITLE = "서버 볼트가 이전 상태로 돌아감"
    const val ROLLBACK_BODY = "서버의 볼트가 이 기기에서 마지막으로 본 상태보다 이전으로 돌아갔습니다. 서버를 복구한 경우가 아니라면 받아들이지 마세요."
    const val ROLLBACK_ACCEPT = "서버 볼트 받아들이기"
    const val UNKNOWN ="알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
}

/**
 * 앱 최상위 흐름: 기기 기록이 있으면 잠금 화면, 없으면 로그인. 인증·잠금 해제 뒤 볼트 목록, 잠그면 잠금 화면.
 * 잠금이 풀린 동안의 암호화 키는 [opened]에만 두고, 잠그면 0으로 덮어쓴다(SEC-003 규칙 1·6). 세션은 잠겨도 유지한다.
 *
 * @param routing 테스트용. 없으면 붙어 있는 [RootRouter]를 쓴다.
 */
class RootInteractor(
    presenter: EmptyPresenter,
    private val store: ViewStateStore<RootViewState>,
    private val actions: ViewActionStream<RootViewAction>,
    private val gateway: VaultSessionGateway? = null,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val routing: RootRouting? = null,
    private val onLocked: () -> Unit = {},
    private val autoLock: RootAutoLock? = null,
    private val appEvents: AppEvents? = null,
    private val biometrics: BiometricGateway? = null,
) : BasicInteractor<EmptyPresenter, RootRouter>(presenter), AuthListener, UnlockListener, MainListener, VaultSaver, BackupPort, AccountPort,
    BiometricPort {
    private var opened: OpenedVault? = null
    private var session: Session? = null
    private var email: String? = null

    /** 잠금이 풀린 동안 마지막으로 저장한 볼트. 내보내기·복원의 기준이다. */
    private var latestVault: VaultData? = null

    /** 로그인 직후 되돌림으로 열지 못한 인증 결과. 사용자가 답할 때까지 키를 쥐고 있다(VLT-001 규칙 9). */
    private class PendingRollback(val result: AuthResult, val serverRevision: Int)

    private var pendingRollback: PendingRollback? = null

    private val nav: RootRouting get() = routing ?: router

    override fun didBecomeActive(savedInstanceState: Bundle?) {
        super.didBecomeActive(savedInstanceState)
        actions.actions.onEach(::handle).launchIn(coroutineScope)
        appEvents?.setListener(autoLock)
        coroutineScope.launch { start() }
    }

    override fun willResignActive() {
        appEvents?.setListener(null)
        pendingRollback?.result?.encryptionKey?.fill(0)
        pendingRollback = null
        wipe()
        super.willResignActive()
    }

    /** View 동작 하나를 처리해 상태를 바꾼다. 테스트는 이 함수를 직접 부른다. */
    fun handle(action: RootViewAction) {
        when (action) {
            RootViewAction.ViewAppeared -> store.update { it.copy(hasAppeared = true) }
            RootViewAction.DismissNotice -> store.update { it.copy(notice = null) }
            is RootViewAction.AnswerBiometricPrompt -> coroutineScope.launch { answerBiometricPrompt(action.enable) }
            is RootViewAction.AnswerRollback -> coroutineScope.launch { answerRollback(action.accept) }
        }
    }

    /** 처음 화면을 고른다(SEC-003 규칙 9). */
    suspend fun start() {
        val locked = gateway?.let { withContext(workDispatcher) { it.lockedAccountEmail() } }
        if (locked == null) {
            nav.showAuth(this)
        } else {
            email = locked
            nav.showUnlock(locked, null, this)
        }
    }

    override fun onAuthenticated(result: AuthResult) {
        coroutineScope.launch { openAfterAuth(result) }
    }

    /** 인증 직후 볼트를 연다. 실패하면 키를 지우고 로그인 화면에 문구를 남긴다. */
    suspend fun openAfterAuth(result: AuthResult) {
        val gateway = gateway ?: return
        val vault = try {
            withContext(workDispatcher) { gateway.openAfterAuth(result) }
        } catch (error: CancellationException) {
            result.encryptionKey.fill(0)
            throw error
        } catch (error: SyncError.RevisionRollback) {
            pendingRollback = PendingRollback(result, error.serverRevision)
            store.update { it.copy(isConfirmingRollback = true) }
            return
        } catch (error: Exception) {
            result.encryptionKey.fill(0)
            store.update { it.copy(notice = RootMessages.VAULT_NOT_READY) }
            nav.showAuth(this)
            return
        }
        showVault(vault)
        if (result.isNewAccount && isAvailable && !isEnabled) store.update { it.copy(isAskingBiometrics = true) }
    }

    /**
     * 로그인 직후 되돌림 경고의 답(VLT-001 규칙 9). 받아들이면 그 revision까지 허용하고 볼트를 다시 연다.
     * 취소하면 키를 지우고 로그인 화면으로 간다. 테스트는 직접 부른다.
     */
    suspend fun answerRollback(accept: Boolean) {
        val pending = pendingRollback ?: return
        pendingRollback = null
        store.update { it.copy(isConfirmingRollback = false) }
        if (!accept) {
            pending.result.encryptionKey.fill(0)
            nav.showAuth(this)
            return
        }
        try {
            gateway?.let { withContext(workDispatcher) { it.acceptRollback(pending.serverRevision) } }
        } catch (error: CancellationException) {
            pending.result.encryptionKey.fill(0)
            throw error
        } catch (error: Exception) {
            pending.result.encryptionKey.fill(0)
            store.update { it.copy(notice = RootMessages.UNKNOWN) }
            nav.showAuth(this)
            return
        }
        openAfterAuth(pending.result)
    }

    /** 가입 직후 안내의 답. 켜기를 고르면 생체 인증 창을 거쳐 볼트 키를 감싼다. 테스트는 직접 부른다. */
    suspend fun answerBiometricPrompt(enable: Boolean) {
        store.update { it.copy(isAskingBiometrics = false) }
        if (!enable) return
        try {
            enable()
        } catch (error: CancellationException) {
            throw error
        } catch (error: BiometricError.Cancelled) {
            // 나중에 설정에서 켤 수 있다
        } catch (error: Exception) {
            store.update { it.copy(notice = RootMessages.BIOMETRIC_UNAVAILABLE) }
        }
    }

    override fun onUnlocked(opened: OpenedVault) = showVault(opened)

    override fun onLockRequested() = lock()

    /** 다시 입력받아 로그인하도록 세션 없이 잠근다(SEC-003 규칙 12). */
    override fun onResyncRequested() = lock()

    /** 설정에서 자동 잠금 시간을 바꿨다. 그때부터 새 시간으로 잰다. */
    override fun onAutoLockChanged(option: AutoLockOption) {
        autoLock?.restart()
    }

    override fun onLoginRequired(message: String?) {
        wipe()
        session = null
        store.update { it.copy(notice = message) }
        nav.showAuth(this)
    }

    override fun onLogoutRequested() {
        coroutineScope.launch { logout() }
    }

    suspend fun logout() {
        val current = session
        wipe()
        session = null
        email = null
        try {
            gateway?.let { withContext(workDispatcher) { it.logout(current) } }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            store.update { it.copy(notice = RootMessages.UNKNOWN) }
        }
        nav.showAuth(this)
    }

    /** 잠근다. 키를 지우고 목록 RIB을 떼며 세션은 남긴다(SEC-003 규칙 1). */
    fun lock() {
        val lockedEmail = opened?.account?.email ?: email ?: return
        wipe()
        nav.showUnlock(lockedEmail, session, this)
    }

    /** 목록 RIB의 저장 요청. 키를 쥔 [opened]로 저장한다. 메인 스레드 밖에서 불린다. */
    override fun save(vault: VaultData): SavedVault {
        val current = checkNotNull(opened) { "잠긴 동안에는 저장할 수 없다" }
        return checkNotNull(gateway).save(current, vault).also { latestVault = it.vault }
    }

    /** 목록 RIB의 되돌림 수락. 메인 스레드 밖에서 불린다. */
    override fun acceptRollback(serverRevision: Int) = checkNotNull(gateway).acceptRollback(serverRevision)

    /** 설정 탭의 백업 내보내기. 마지막으로 저장한 볼트(대기 중인 변경 포함)를 쓴다(VLT-004 규칙 2). */
    override fun export(now: Instant): ByteArray {
        val current = checkNotNull(opened) { "잠긴 동안에는 내보낼 수 없다" }
        return checkNotNull(gateway).exportBackup(current, checkNotNull(latestVault), now)
    }

    /** 비밀번호를 바꾸면 옛 키를 지우고 새 키·새 세션으로 바꿔 든다(SEC-004 규칙 7). */
    override fun changePassword(current: String, new: String, confirmation: String) {
        val opened = checkNotNull(opened) { "잠긴 동안에는 바꿀 수 없다" }
        val changed = checkNotNull(gateway).changePassword(opened, checkNotNull(latestVault), current, new, confirmation)
        opened.wipe()
        this.opened = changed
        session = changed.session
    }

    override val isAvailable: Boolean get() = biometrics?.isAvailable == true
    override val isEnabled: Boolean get() = biometrics?.isEnabled == true

    /** 지금 든 볼트 키를 생체 키로 감싼다(ADR-AND-002). */
    override suspend fun enable() {
        val key = checkNotNull(opened) { "잠긴 동안에는 켤 수 없다" }.encryptionKey
        checkNotNull(biometrics).enable(key)
    }

    override fun disable() {
        biometrics?.disable()
    }

    override fun deleteAccount(password: String) {
        checkNotNull(gateway).deleteAccount(checkNotNull(opened) { "잠긴 동안에는 지울 수 없다" }, password)
    }

    /** 계정을 지웠다. 키와 세션을 버리고 로그인 화면에 완료 안내를 보인다(SEC-005 규칙 5). */
    override fun onAccountDeleted() {
        wipe()
        session = null
        email = null
        store.update { it.copy(notice = RootMessages.ACCOUNT_DELETED) }
        nav.showAuth(this)
    }

    override fun onSessionExpired() = onLoginRequired(RootMessages.SESSION_EXPIRED)

    override fun restore(bytes: ByteArray, password: String, now: Instant): RestoreOutcome {
        val current = checkNotNull(opened) { "잠긴 동안에는 복원할 수 없다" }
        val result = checkNotNull(gateway).restoreBackup(current, checkNotNull(latestVault), bytes, password, now)
        result.saved?.let { latestVault = it.vault }
        return RestoreOutcome(result.restored, result.saved?.vault)
    }

    private fun showVault(vault: OpenedVault) {
        wipe()
        opened = vault
        latestVault = vault.vault
        session = vault.session
        email = vault.account.email
        store.update { it.copy(notice = null) }
        nav.showMain(vault.vault, isOffline = vault.session == null, email = vault.account.email, listener = this)
        autoLock?.start(::lock)
    }

    private fun wipe() {
        autoLock?.stop()
        if (opened != null) onLocked()
        opened?.wipe()
        opened = null
        latestVault = null
    }
}
