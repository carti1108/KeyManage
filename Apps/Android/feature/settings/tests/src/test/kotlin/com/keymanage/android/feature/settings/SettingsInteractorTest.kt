package com.keymanage.android.feature.settings

import com.keymanage.android.domain.lock.AutoLockOption
import com.keymanage.android.feature.settings.impl.SettingsInteractor
import com.keymanage.android.feature.settings.impl.SettingsPresenter
import com.keymanage.android.feature.settings.impl.SettingsViewAction
import com.keymanage.android.feature.settings.impl.SettingsViewState
import com.keymanage.android.feature.settings.testing.MemoryAutoLockStore
import com.keymanage.android.feature.settings.testing.RecordingSettingsListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsInteractorTest {
    private val autoLockStore = MemoryAutoLockStore(AutoLockOption.FIFTEEN_MINUTES)
    private val listener = RecordingSettingsListener()
    private var pending = false
    private val store = ViewStateStore(SettingsViewState(email = "dev@example.com"))
    private val interactor = SettingsInteractor(
        SettingsPresenter(store, ViewActionStream()), store, ViewActionStream(), autoLockStore, { pending }, listener,
    )

    @Test
    fun `저장된 자동 잠금 시간을 보인다`() {
        assertEquals(AutoLockOption.FIFTEEN_MINUTES, store.current.autoLock)
    }

    @Test
    fun `자동 잠금 시간을 고르면 저장하고 부모에 알린다`() {
        interactor.handle(SettingsViewAction.OpenAutoLock)
        interactor.handle(SettingsViewAction.ChooseAutoLock(AutoLockOption.IMMEDIATELY))
        assertEquals(AutoLockOption.IMMEDIATELY, autoLockStore.option)
        assertEquals(listOf(AutoLockOption.IMMEDIATELY), listener.autoLockChanges)
        assertFalse(store.current.isChoosingAutoLock)
    }

    @Test
    fun `대기 내용이 없으면 바로 로그아웃한다`() {
        interactor.handle(SettingsViewAction.Logout)
        assertEquals(1, listener.logoutRequests)
    }

    @Test
    fun `대기 내용이 있으면 확인을 받고 취소하면 로그아웃하지 않는다`() {
        pending = true
        interactor.handle(SettingsViewAction.Logout)
        assertTrue(store.current.isConfirmingLogout)
        assertEquals(0, listener.logoutRequests)

        interactor.handle(SettingsViewAction.DismissDialog)
        assertEquals(0, listener.logoutRequests)

        interactor.handle(SettingsViewAction.Logout)
        interactor.handle(SettingsViewAction.ConfirmLogout)
        assertEquals(1, listener.logoutRequests)
    }
}
