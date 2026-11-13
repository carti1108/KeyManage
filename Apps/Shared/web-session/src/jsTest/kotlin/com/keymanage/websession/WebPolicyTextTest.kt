package com.keymanage.websession

import com.keymanage.feature.auth.AuthViewState
import com.keymanage.feature.main.MainViewState
import com.keymanage.feature.settings.SettingsNoticeKind
import com.keymanage.feature.settings.SettingsViewState
import com.keymanage.feature.unlock.UnlockViewState
import com.keymanage.feature.vaultlist.VaultListViewState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 웹 State가 정책 문구(확인 창·안내·대화상자)를 들고 내려가는지 본다. React는 이 값을 그대로 그리므로 원문이 그대로여야 한다
// (Scripts/check-policy-acceptance.sh가 테스트 원문을 보므로 문구는 상수가 아니라 글자 그대로 쓴다)

private val ROLLBACK_TEXT = WebConfirmText(
    title = "서버 볼트가 이전 상태로 돌아감",
    body = "서버의 볼트가 이 기기에서 마지막으로 본 상태보다 이전으로 돌아갔습니다. 서버를 복구한 경우가 아니라면 받아들이지 마세요.",
    confirm = "서버 볼트 받아들이기",
    cancel = "취소",
)

private val PENDING_LOGOUT_TEXT = WebConfirmText(
    title = "동기화되지 않은 변경 사항",
    body = "로그아웃하면 서버에 올리지 못한 변경 사항이 사라집니다.",
    confirm = "로그아웃",
    cancel = "취소",
)

class WebPolicyTextTest {
    @Test
    fun authStateCarriesSetupNoticeWeakWarningAndRollbackText() {
        val state = WebAuthView().store.getSnapshot()

        assertEquals(ROLLBACK_TEXT, state.rollbackWarning)
        assertEquals(
            WebMessage(
                title = "마스터 비밀번호 설정",
                body = "볼트를 열기 위한 마스터 비밀번호를 입력해주세요. 마스터 비밀번호를 잃어버리면 복구할 수 없습니다.",
            ),
            state.masterPasswordSetup,
        )
        assertEquals("마스터 비밀번호가 보안에 취약합니다. 더 길게 만들거나 여러 종류의 문자를 섞어 주세요.", state.weakPasswordWarning)
    }

    @Test
    fun authTextDoesNotDependOnRenderedState() {
        val view = WebAuthView()
        view.render(AuthViewState(password = "abc", isConfirmingRollback = true))

        assertEquals(ROLLBACK_TEXT, view.store.getSnapshot().rollbackWarning)
    }

    @Test
    fun unlockStateCarriesRollbackAndPendingLogoutText() {
        val state = WebUnlockView().store.getSnapshot()

        assertEquals(ROLLBACK_TEXT, state.rollbackWarning)
        assertEquals(PENDING_LOGOUT_TEXT, state.pendingLogoutConfirm)
    }

    @Test
    fun mainStateCarriesLogoutReconnectAndRollbackText() {
        val view = WebMainView()
        view.render(MainViewState(needsReconnect = true, isConfirmingLogout = true, isConfirmingReconnectRollback = true))
        val state = view.store.getSnapshot()

        assertEquals(PENDING_LOGOUT_TEXT, state.pendingLogoutConfirm)
        assertEquals(ROLLBACK_TEXT, state.rollbackWarning)
        assertEquals(
            WebReconnectPrompt(body = "서버와 동기화하려면 마스터 비밀번호를 다시 입력해 주세요.", action = "동기화"),
            state.reconnectPrompt,
        )
    }

    @Test
    fun vaultListStateCarriesDeleteConfirmWeakWarningAndSequentialDigitsText() {
        val state = WebVaultListView().store.getSnapshot()

        assertEquals(
            WebConfirmText(
                title = "항목 삭제",
                body = "이 항목을 삭제할까요? 다른 기기에서도 삭제됩니다.",
                confirm = "삭제",
                cancel = "취소",
            ),
            state.deleteConfirm,
        )
        assertEquals(
            WebMessage(
                title = "취약한 비밀번호 경고",
                body = "현재 비밀번호는 보안에 취약합니다. 80점 이상의 강력한 비밀번호로 재생성하세요.",
            ),
            state.weakPasswordWarning,
        )
        assertEquals("123처럼 이어지는 숫자가 들어 있어 추측하기 쉽습니다.", state.sequentialDigitsNotice)
    }

    @Test
    fun vaultListTextSurvivesRender() {
        val view = WebVaultListView()
        view.render(VaultListViewState(isConfirmingDelete = true))

        assertEquals("항목 삭제", view.store.getSnapshot().deleteConfirm.title)
    }

    @Test
    fun settingsStateCarriesDialogText() {
        val state = WebSettingsView().store.getSnapshot()

        assertEquals(
            WebConfirmText(
                title = "백업 내보내기",
                body = "이 파일은 내보낸 시점의 마스터 비밀번호로만 열 수 있습니다. 비밀번호를 바꾼 뒤에도 이 파일은 예전 비밀번호로 엽니다.",
                confirm = "내보내기",
                cancel = "취소",
            ),
            state.exportNotice,
        )
        assertEquals(
            WebRestoreDialogText(
                title = "백업에서 복원",
                body = "백업에 있던 항목은 백업 시점 내용으로 돌아갑니다. 백업 뒤에 고치거나 지운 내용은 되돌아가고, 백업 뒤에 새로 만든 항목은 그대로 남습니다.",
                passwordLabel = "백업 파일 비밀번호",
                submit = "복원",
                cancel = "취소",
                progress = "백업을 복원하는 중입니다…",
            ),
            state.restoreDialog,
        )
        assertEquals(
            WebChangePasswordDialogText(
                title = "마스터 비밀번호 변경",
                body = "비밀번호를 바꾸면 다른 기기는 모두 로그아웃되고, 새 비밀번호로 다시 로그인해야 합니다. 다른 기기에서 아직 올리지 못한 변경 사항은 사라집니다. 예전에 내보낸 백업은 예전 비밀번호로 엽니다.",
                currentLabel = "현재 마스터 비밀번호",
                newLabel = "새 마스터 비밀번호",
                confirmLabel = "새 마스터 비밀번호 확인",
                newPasswordRule = "마스터 비밀번호는 12자 이상이어야 합니다.",
                submit = "변경",
                cancel = "취소",
                progress = "마스터 비밀번호를 바꾸는 중입니다…",
            ),
            state.changePasswordDialog,
        )
        assertEquals(
            WebDeleteAccountDialogText(
                title = "계정 삭제",
                body = "계정과 볼트가 서버에서 바로 지워지고 되돌릴 수 없습니다. 다른 기기도 모두 로그아웃되고, 아직 올리지 못한 변경 사항은 사라집니다. 필요하면 먼저 백업을 내보내 주세요.",
                passwordLabel = "마스터 비밀번호",
                phraseLabel = "확인을 위해 '계정 삭제'를 입력해 주세요.",
                submit = "삭제",
                cancel = "취소",
                progress = "계정을 삭제하는 중입니다…",
            ),
            state.deleteAccountDialog,
        )
    }

    @Test
    fun settingsStateSplitsEntryButtonFromOfflineJudgement() {
        val view = WebSettingsView()
        view.render(SettingsViewState(accountOffline = true))
        val offline = view.store.getSnapshot()
        assertFalse(offline.canChangePassword)
        assertFalse(offline.canDeleteAccount)
        assertTrue(offline.canStartChangePassword)
        assertTrue(offline.canStartDeleteAccount)

        view.render(SettingsViewState(readOnly = true))
        val readOnly = view.store.getSnapshot()
        assertFalse(readOnly.canStartChangePassword)
        assertTrue(readOnly.canStartDeleteAccount)

        view.render(SettingsViewState(inProgress = true))
        val busy = view.store.getSnapshot()
        assertFalse(busy.canStartChangePassword)
        assertFalse(busy.canStartDeleteAccount)
    }

    @Test
    fun settingsStateMapsNoticeKindToWireString() {
        val view = WebSettingsView()
        assertEquals("info", view.store.getSnapshot().noticeKind)

        view.render(SettingsViewState(notice = "인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다.", noticeKind = SettingsNoticeKind.ERROR))
        assertEquals("error", view.store.getSnapshot().noticeKind)

        view.render(SettingsViewState(notice = "마스터 비밀번호를 바꿨습니다.", noticeKind = SettingsNoticeKind.INFO))
        assertEquals("info", view.store.getSnapshot().noticeKind)
    }

    @Test
    fun settingsAutoLockOptionsComeFromState() {
        val state = WebSettingsView().store.getSnapshot()

        assertEquals(listOf(0, 60_000, 300_000, 900_000, 3_600_000), state.autoLockOptionsMs.toList())
    }
}
