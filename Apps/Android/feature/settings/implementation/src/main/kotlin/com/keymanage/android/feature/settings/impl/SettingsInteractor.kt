package com.keymanage.android.feature.settings.impl

import com.keymanage.android.domain.lock.AutoLockOption
import com.keymanage.android.domain.lock.AutoLockSettingStore
import com.keymanage.android.feature.settings.PendingChanges
import com.keymanage.android.feature.settings.SettingsListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import com.uber.rib.core.BasicComposeRouter
import com.uber.rib.core.BasicInteractor
import com.uber.rib.core.Bundle
import com.uber.rib.core.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class SettingsViewState(
    val email: String,
    val autoLock: AutoLockOption = AutoLockOption.DEFAULT,
    val isChoosingAutoLock: Boolean = false,
    val isConfirmingLogout: Boolean = false,
)

sealed interface SettingsViewAction {
    data object OpenAutoLock : SettingsViewAction
    data class ChooseAutoLock(val option: AutoLockOption) : SettingsViewAction
    data object DismissDialog : SettingsViewAction
    data object Logout : SettingsViewAction
    data object ConfirmLogout : SettingsViewAction
}

/** VLT-002 사용자 문구. */
object SettingsMessages {
    const val PENDING_LOGOUT_TITLE = "동기화되지 않은 변경 사항"
    const val PENDING_LOGOUT_BODY = "로그아웃하면 서버에 올리지 못한 변경 사항이 사라집니다."
}

/** 자동 잠금 시간 선택과 로그아웃. 대기 내용이 있으면 로그아웃 전에 확인을 받는다(VLT-002 규칙 11). */
class SettingsInteractor(
    presenter: SettingsPresenter,
    private val store: ViewStateStore<SettingsViewState>,
    private val actions: ViewActionStream<SettingsViewAction>,
    private val autoLockStore: AutoLockSettingStore,
    private val pendingChanges: PendingChanges,
    private val listener: SettingsListener,
) : BasicInteractor<SettingsPresenter, BasicComposeRouter<SettingsInteractor>>(presenter) {
    init {
        store.update { it.copy(autoLock = autoLockStore.read()) }
    }

    override fun didBecomeActive(savedInstanceState: Bundle?) {
        super.didBecomeActive(savedInstanceState)
        actions.actions.onEach(::handle).launchIn(coroutineScope)
    }

    fun handle(action: SettingsViewAction) {
        when (action) {
            SettingsViewAction.OpenAutoLock -> store.update { it.copy(isChoosingAutoLock = true) }
            is SettingsViewAction.ChooseAutoLock -> {
                autoLockStore.write(action.option)
                store.update { it.copy(autoLock = action.option, isChoosingAutoLock = false) }
                listener.onAutoLockChanged(action.option)
            }
            SettingsViewAction.DismissDialog -> store.update { it.copy(isChoosingAutoLock = false, isConfirmingLogout = false) }
            SettingsViewAction.Logout ->
                if (pendingChanges.exist()) store.update { it.copy(isConfirmingLogout = true) } else listener.onLogoutRequested()
            SettingsViewAction.ConfirmLogout -> {
                store.update { it.copy(isConfirmingLogout = false) }
                listener.onLogoutRequested()
            }
        }
    }
}
