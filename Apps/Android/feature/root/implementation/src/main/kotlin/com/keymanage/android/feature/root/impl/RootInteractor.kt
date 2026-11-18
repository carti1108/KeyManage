package com.keymanage.android.feature.root.impl

import com.keymanage.android.domain.auth.AuthResult
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.session.OpenedVault
import com.keymanage.android.feature.auth.AuthListener
import com.keymanage.android.feature.root.VaultSessionGateway
import com.keymanage.android.feature.unlock.UnlockListener
import com.keymanage.android.feature.vaultlist.VaultListListener
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
    const val UNKNOWN = "알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
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
) : BasicInteractor<EmptyPresenter, RootRouter>(presenter), AuthListener, UnlockListener, VaultListListener, VaultSaver {
    private var opened: OpenedVault? = null
    private var session: Session? = null
    private var email: String? = null

    private val nav: RootRouting get() = routing ?: router

    override fun didBecomeActive(savedInstanceState: Bundle?) {
        super.didBecomeActive(savedInstanceState)
        actions.actions.onEach(::handle).launchIn(coroutineScope)
        coroutineScope.launch { start() }
    }

    override fun willResignActive() {
        wipe()
        super.willResignActive()
    }

    /** View 동작 하나를 처리해 상태를 바꾼다. 테스트는 이 함수를 직접 부른다. */
    fun handle(action: RootViewAction) {
        when (action) {
            RootViewAction.ViewAppeared -> store.update { it.copy(hasAppeared = true) }
            RootViewAction.DismissNotice -> store.update { it.copy(notice = null) }
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
        } catch (error: Exception) {
            result.encryptionKey.fill(0)
            store.update { it.copy(notice = RootMessages.VAULT_NOT_READY) }
            nav.showAuth(this)
            return
        }
        showVault(vault)
    }

    override fun onUnlocked(opened: OpenedVault) = showVault(opened)

    override fun onLockRequested() = lock()

    /** 다시 입력받아 로그인하도록 세션 없이 잠근다(SEC-003 규칙 12). */
    override fun onResyncRequested() = lock()

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
        return checkNotNull(gateway).save(current, vault)
    }

    private fun showVault(vault: OpenedVault) {
        wipe()
        opened = vault
        session = vault.session
        email = vault.account.email
        store.update { it.copy(notice = null) }
        nav.showVaultList(vault.vault, isOffline = vault.session == null, listener = this)
    }

    private fun wipe() {
        if (opened != null) onLocked()
        opened?.wipe()
        opened = null
    }
}
