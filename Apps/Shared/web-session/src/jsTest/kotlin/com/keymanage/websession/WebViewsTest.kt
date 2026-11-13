package com.keymanage.websession

import com.keymanage.domain.vault.ItemFields
import com.keymanage.domain.vault.activeItems
import com.keymanage.domain.vault.addItem
import com.keymanage.domain.vault.emptyVault
import com.keymanage.feature.auth.AuthMode
import com.keymanage.feature.auth.AuthPresentableListener
import com.keymanage.feature.auth.AuthViewAction
import com.keymanage.feature.auth.AuthViewState
import com.keymanage.feature.generator.GeneratedPassword
import com.keymanage.feature.generator.GeneratorCharset
import com.keymanage.feature.generator.PasswordGeneratorPresentableListener
import com.keymanage.feature.generator.PasswordGeneratorViewAction
import com.keymanage.feature.generator.PasswordGeneratorViewState
import com.keymanage.feature.main.MainPresentableListener
import com.keymanage.feature.main.MainTab
import com.keymanage.feature.main.MainViewAction
import com.keymanage.feature.main.MainViewState
import com.keymanage.feature.messages.ErrorMessage
import com.keymanage.feature.messages.OFFLINE_NOTICE
import com.keymanage.feature.settings.SettingsModal
import com.keymanage.feature.settings.SettingsPresentableListener
import com.keymanage.feature.settings.SettingsViewAction
import com.keymanage.feature.settings.SettingsViewState
import com.keymanage.feature.unlock.UnlockPresentableListener
import com.keymanage.feature.unlock.UnlockViewAction
import com.keymanage.feature.unlock.UnlockViewState
import com.keymanage.feature.vaultlist.VaultItemForm
import com.keymanage.feature.vaultlist.VaultItemFormField
import com.keymanage.feature.vaultlist.VaultListPresentableListener
import com.keymanage.feature.vaultlist.VaultListScreen
import com.keymanage.feature.vaultlist.VaultListViewAction
import com.keymanage.feature.vaultlist.VaultListViewState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

// 웹 뷰: 공유 RIB의 상태가 JS 친화 상태로 바뀌는지, 내보낸 함수가 RIB 입력으로 전달되는지 본다

private class Recorder<A> {
    val actions = mutableListOf<A>()
}

class WebViewsTest {
    @Test
    fun authStateMapsEnumsAndDerivedFields() {
        val view = WebAuthView()
        assertEquals("login", view.store.getSnapshot().mode)

        view.render(
            AuthViewState(
                mode = AuthMode.REGISTER,
                email = "a@b.c",
                password = "abc",
                notice = "세션 만료",
                error = ErrorMessage(title = "제목", body = "본문"),
            ),
        )
        val state = view.store.getSnapshot()
        assertEquals("register", state.mode)
        assertEquals("a@b.c", state.email)
        assertEquals("세션 만료", state.notice)
        assertEquals(WebMessage("제목", "본문"), state.error)
        assertTrue(state.isRegistering)
        assertTrue(state.showsStrengthGauge)
        assertTrue(state.showsWeakPasswordWarning)
        assertTrue(state.passwordStrengthScore in 0..100)
    }

    @Test
    fun authStoreKeepsSameSnapshotForEqualState() {
        val view = WebAuthView()
        view.render(AuthViewState(email = "x"))
        val first = view.store.getSnapshot()
        var notified = 0
        view.store.subscribe { notified += 1 }
        view.render(AuthViewState(email = "x"))
        assertSame(first, view.store.getSnapshot())
        assertEquals(0, notified)
    }

    @Test
    fun authFunctionsForwardActionsAndAreIgnoredWithoutListener() {
        val view = WebAuthView()
        view.submit() // 리스너가 없으면 무시한다

        val recorder = Recorder<AuthViewAction>()
        view.listener = object : AuthPresentableListener {
            override fun handle(action: AuthViewAction) {
                recorder.actions += action
            }
        }
        view.switchMode()
        view.updateEmail("a@b.c")
        view.updatePassword("pw")
        view.updateConfirmation("pw2")
        view.submit()
        view.acceptRollback()
        view.cancelRollback()
        assertEquals(
            listOf(
                AuthViewAction.SwitchMode,
                AuthViewAction.UpdateEmail("a@b.c"),
                AuthViewAction.UpdatePassword("pw"),
                AuthViewAction.UpdateConfirmation("pw2"),
                AuthViewAction.Submit,
                AuthViewAction.AcceptRollback,
                AuthViewAction.CancelRollback,
            ),
            recorder.actions,
        )
    }

    @Test
    fun unlockStateAndFunctions() {
        val view = WebUnlockView()
        view.render(UnlockViewState(email = "a@b.c", isLoading = true, isConfirmingLogout = true))
        val state = view.store.getSnapshot()
        assertEquals("a@b.c", state.email)
        assertTrue(state.isLoading)
        assertTrue(state.isConfirmingLogout)
        assertFalse(state.isBiometricsAvailable)

        val recorder = Recorder<UnlockViewAction>()
        view.listener = object : UnlockPresentableListener {
            override fun handle(action: UnlockViewAction) {
                recorder.actions += action
            }
        }
        view.updatePassword("pw")
        view.unlock()
        view.requestLogout()
        view.confirmLogout()
        view.cancelLogout()
        view.acceptRollback()
        view.cancelRollback()
        assertEquals(
            listOf(
                UnlockViewAction.UpdatePassword("pw"),
                UnlockViewAction.Unlock,
                UnlockViewAction.RequestLogout,
                UnlockViewAction.ConfirmLogout,
                UnlockViewAction.CancelLogout,
                UnlockViewAction.AcceptRollback,
                UnlockViewAction.CancelRollback,
            ),
            recorder.actions,
        )
    }

    @Test
    fun mainStateMapsTabAndNotices() {
        val view = WebMainView()
        view.render(MainViewState(email = "a@b.c", selectedTab = MainTab.SETTINGS, offline = true, readOnly = true))
        val state = view.store.getSnapshot()
        assertEquals("settings", state.selectedTab)
        assertEquals(OFFLINE_NOTICE, state.offlineNotice)
        assertTrue(state.readOnlyNotice != null)
        assertNull(state.deviceStorageWarning)

        val recorder = Recorder<MainViewAction>()
        view.listener = object : MainPresentableListener {
            override fun handle(action: MainViewAction) {
                recorder.actions += action
            }
        }
        view.selectTab("generator")
        view.requestLock()
        view.updateReconnectPassword("pw")
        view.reconnect()
        assertEquals(
            listOf(
                MainViewAction.SelectTab(MainTab.GENERATOR),
                MainViewAction.RequestLock,
                MainViewAction.UpdateReconnectPassword("pw"),
                MainViewAction.Reconnect,
            ),
            recorder.actions,
        )
        assertFailsWith<IllegalArgumentException> { view.selectTab("nope") }
    }

    @Test
    fun vaultListStateMapsItemsAndDerivedFields() {
        val now = Clock.System.now()
        val vault = addItem(emptyVault(now), ItemFields("GitHub", "octocat", "pw-1"), now, "item-1")
        val items = activeItems(vault)
        val view = WebVaultListView()

        view.render(VaultListViewState(items = items, isVaultEmpty = false, readOnly = true, selected = items[0], screen = VaultListScreen.DETAIL))
        val state = view.store.getSnapshot()
        assertEquals("detail", state.screen)
        assertEquals(listOf("item-1"), state.items.map { it.id })
        assertEquals("GitHub", state.items[0].serviceName)
        assertEquals("octocat", state.items[0].username)
        assertEquals("", state.items[0].url) // 없는 값은 빈 문자열
        assertEquals("item-1", state.selected?.id)
        assertFalse(state.canEdit)
        assertNull(state.emptyMessage)

        view.render(VaultListViewState(isVaultEmpty = true))
        assertEquals("저장된 항목이 없습니다.", view.store.getSnapshot().emptyMessage)
        assertEquals(0, view.store.getSnapshot().items.size)
    }

    @Test
    fun vaultListFormAndStrengthFields() {
        val view = WebVaultListView()
        view.render(VaultListViewState(screen = VaultListScreen.ADD, form = VaultItemForm(password = "123456")))
        val state = view.store.getSnapshot()
        assertEquals("add", state.screen)
        assertEquals("123456", state.form.password)
        assertTrue(state.showsFormStrength)
        assertTrue(state.showsFormWeakWarning)
        assertTrue(state.showsFormSequentialDigitsNotice)
    }

    @Test
    fun vaultListFunctionsForwardActions() {
        val view = WebVaultListView()
        val recorder = Recorder<VaultListViewAction>()
        view.listener = object : VaultListPresentableListener {
            override fun handle(action: VaultListViewAction) {
                recorder.actions += action
            }
        }
        view.search("git")
        view.openItem("i1")
        view.editForm("serviceName", "GitHub")
        view.editForm("notes", "메모")
        view.submitForm()
        view.copyPassword("i1")
        assertEquals(
            listOf(
                VaultListViewAction.Search("git"),
                VaultListViewAction.OpenItem("i1"),
                VaultListViewAction.EditForm(VaultItemFormField.SERVICE_NAME, "GitHub"),
                VaultListViewAction.EditForm(VaultItemFormField.NOTES, "메모"),
                VaultListViewAction.SubmitForm,
                VaultListViewAction.CopyPassword("i1"),
            ),
            recorder.actions,
        )
        assertFailsWith<IllegalArgumentException> { view.editForm("nope", "x") }
    }

    @Test
    fun generatorStateFlattensGeneratedPassword() {
        val view = WebGeneratorView()
        assertNull(view.store.getSnapshot().generatedText)
        assertEquals(0, view.store.getSnapshot().generatedScore)

        view.render(
            PasswordGeneratorViewState(
                lengthText = "20",
                generated = GeneratedPassword(text = "abc123", score = 10, hasSequentialDigits = true),
                showsCopy = true,
            ),
        )
        val state = view.store.getSnapshot()
        assertEquals("abc123", state.generatedText)
        assertEquals(10, state.generatedScore)
        assertTrue(state.generatedIsWeak)
        assertTrue(state.generatedHasSequentialDigits)
        assertTrue(state.canUseGenerated)
        assertTrue(state.showsCopy)

        view.render(PasswordGeneratorViewState(generated = null, problemMessage = "경고"))
        val invalid = view.store.getSnapshot()
        assertNull(invalid.generatedText)
        assertFalse(invalid.canUseGenerated)
        assertEquals("경고", invalid.problemMessage)
    }

    @Test
    fun generatorFunctionsForwardActions() {
        val view = WebGeneratorView()
        val recorder = Recorder<PasswordGeneratorViewAction>()
        view.listener = object : PasswordGeneratorPresentableListener {
            override fun handle(action: PasswordGeneratorViewAction) {
                recorder.actions += action
            }
        }
        view.changeLength("12")
        view.setCharset("symbols", false)
        view.finishEditingLength()
        view.regenerate()
        view.use()
        view.close()
        view.copy()
        assertEquals(
            listOf(
                PasswordGeneratorViewAction.ChangeLength("12"),
                PasswordGeneratorViewAction.SetCharset(GeneratorCharset.SYMBOLS, false),
                PasswordGeneratorViewAction.FinishEditingLength,
                PasswordGeneratorViewAction.Regenerate,
                PasswordGeneratorViewAction.Use,
                PasswordGeneratorViewAction.Close,
                PasswordGeneratorViewAction.Copy,
            ),
            recorder.actions,
        )
        assertFailsWith<IllegalArgumentException> { view.setCharset("nope", true) }
    }

    @Test
    fun settingsStateUsesMillisecondsAndWireStrings() {
        val view = WebSettingsView()
        val initial = view.store.getSnapshot()
        assertContentEquals(arrayOf(0, 60_000, 300_000, 900_000, 3_600_000), initial.autoLockOptionsMs)
        assertEquals(300_000, initial.autoLockTimeoutMs)
        assertEquals("none", initial.modal)

        view.render(
            SettingsViewState(
                email = "a@b.c",
                autoLockTimeout = 15.minutes,
                modal = SettingsModal.CHANGE_PASSWORD,
                accountOffline = true,
                currentPassword = "pw",
                readOnly = true,
            ),
        )
        val state = view.store.getSnapshot()
        assertEquals(900_000, state.autoLockTimeoutMs)
        assertEquals("changePassword", state.modal)
        assertFalse(state.canChangePassword) // 오프라인·읽기 전용이면 막는다
        assertFalse(state.canRestoreBackup)
        assertTrue(state.canSubmitChangePassword)
        assertTrue(state.canExportBackup)
    }

    @Test
    fun settingsFunctionsForwardActions() {
        val view = WebSettingsView()
        val recorder = Recorder<SettingsViewAction>()
        view.listener = object : SettingsPresentableListener {
            override fun handle(action: SettingsViewAction) {
                recorder.actions += action
            }
        }
        view.selectAutoLock(60_000)
        view.startExport()
        view.confirmExport()
        view.editDeletionPhrase("계정 삭제")
        view.cancelModal()
        view.dismissMessage()
        assertEquals(
            listOf(
                SettingsViewAction.SelectAutoLock(1.minutes),
                SettingsViewAction.StartExport,
                SettingsViewAction.ConfirmExport,
                SettingsViewAction.EditDeletionPhrase("계정 삭제"),
                SettingsViewAction.CancelModal,
                SettingsViewAction.DismissMessage,
            ),
            recorder.actions,
        )
    }
}
