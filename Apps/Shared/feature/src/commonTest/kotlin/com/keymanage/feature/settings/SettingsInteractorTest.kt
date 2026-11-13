package com.keymanage.feature.settings

import com.carti.ribs.RibCoroutinesConfig
import com.carti.ribs.RibDispatchersProvider
import com.carti.ribs.RibMount
import com.carti.ribs.mount
import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.BackupFileSource
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.LocalDay
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.backup.createBackupFile
import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.encoding.toBase64
import com.keymanage.domain.lock.AUTO_LOCK_OPTIONS
import com.keymanage.domain.lock.AutoLockSettingStore
import com.keymanage.domain.lock.DEFAULT_AUTO_LOCK
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.session.AccountDeletedDeviceNotClearedException
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionExpiredException
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SyncChangeListener
import com.keymanage.domain.session.WrongMasterPasswordException
import com.keymanage.domain.backup.BackupDecryptException
import com.keymanage.domain.vault.SyncedVault
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.emptyVault
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.SessionPort
import com.keymanage.feature.messages.ACCOUNT_DELETED
import com.keymanage.feature.messages.ACCOUNT_DELETED_DEVICE_NOT_CLEARED
import com.keymanage.feature.messages.ACCOUNT_DELETION_OFFLINE
import com.keymanage.feature.messages.BACKUP_OPEN_FAILED
import com.keymanage.feature.messages.NOT_A_BACKUP
import com.keymanage.feature.messages.PASSWORD_CHANGE_DONE
import com.keymanage.feature.messages.PASSWORD_CHANGE_OFFLINE
import com.keymanage.feature.messages.SAME_MASTER_PASSWORD
import com.keymanage.feature.messages.describeError
import com.keymanage.feature.messages.restoredMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val EMAIL = "user@example.com"
private const val LONG_PASSWORD = "correct horse battery"
private const val NEW_PASSWORD = "another long password 1"
private val EXPORTED = BackupFile("keymanage-backup-20260102.json", "{}")

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsInteractorTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var session: FakeSession
    private lateinit var files: FakeBackupFiles
    private lateinit var environment: FakeEnvironment
    private lateinit var view: FakeSettingsView
    private lateinit var listener: FakeSettingsListener
    private lateinit var router: SettingsRouter
    private var mount: RibMount? = null

    @BeforeTest
    fun setUp() {
        RibCoroutinesConfig.dispatchers = SingleDispatcher(dispatcher)
        session = FakeSession()
        files = FakeBackupFiles()
        environment = FakeEnvironment()
        view = FakeSettingsView()
        listener = FakeSettingsListener()
    }

    @AfterTest
    fun tearDown() {
        mount?.unmount()
        RibCoroutinesConfig.reset()
    }

    private fun attach() {
        val dependency = object : SettingsDependency {
            override val settingsViewFactory = SettingsViewFactory { view }
            override val session = this@SettingsInteractorTest.session
            override val backupFiles = files
            override val environment = this@SettingsInteractorTest.environment
        }
        router = SettingsBuilder(dependency).build(listener)
        mount = router.mount()
    }

    private val state get() = view.states.last()

    private fun send(action: SettingsViewAction) {
        assertNotNull(view.listener, "활성 구간이 아니면 뷰 입력을 받지 않는다").handle(action)
        dispatcher.scheduler.runCurrent()
    }

    private fun openChangePassword(current: String = LONG_PASSWORD, next: String = NEW_PASSWORD, confirmation: String = next) {
        send(SettingsViewAction.StartChangePassword)
        send(SettingsViewAction.EditCurrentPassword(current))
        send(SettingsViewAction.EditNewPassword(next))
        send(SettingsViewAction.EditPasswordConfirmation(confirmation))
    }

    private fun openDeleteAccount(password: String = LONG_PASSWORD, phrase: String = "계정 삭제") {
        send(SettingsViewAction.StartDeleteAccount)
        send(SettingsViewAction.EditDeletionPassword(password))
        send(SettingsViewAction.EditDeletionPhrase(phrase))
    }

    private fun pickValidBackup() {
        files.picked = backupSource()
        send(SettingsViewAction.StartRestore)
    }

    // --- 정보 표시 ---

    @Test
    fun 활성화하면_이메일_서버_주소_자동_잠금_시간을_그린다() {
        environment.serverAddress = "https://keymanage.example.com"
        environment.autoLock.value = 15.minutes

        attach()

        assertEquals(EMAIL, state.email)
        assertEquals("https://keymanage.example.com", state.serverAddress)
        assertEquals(15.minutes, state.autoLockTimeout)
        assertEquals(AUTO_LOCK_OPTIONS, state.autoLockOptions)
        assertFalse(state.syncOffline)
        assertEquals(SettingsModal.NONE, state.modal)
    }

    @Test
    fun 동기화_상태는_네트워크_끊김_대기_변경_세션_없음이면_오프라인이다() {
        attach()
        assertFalse(state.syncOffline)

        environment.changeOffline(true)
        assertTrue(state.syncOffline)
        environment.changeOffline(false)
        assertFalse(state.syncOffline)

        session.current = unlocked(pendingSync = true)
        session.fireSyncChange()
        assertTrue(state.syncOffline)

        session.current = unlocked(signedIn = false)
        session.fireSyncChange()
        assertTrue(state.syncOffline)
    }

    @Test
    fun 세션이_바뀌었다는_알림을_받으면_다시_읽는다() {
        attach()

        session.current = unlocked(email = "other@example.com", readOnly = true)
        session.fireRemoteChange()

        assertEquals("other@example.com", state.email)
        assertTrue(state.readOnly)
    }

    // --- 자동 잠금 ---

    @Test
    fun 자동_잠금_시간을_고르면_저장하고_그린다() {
        attach()

        send(SettingsViewAction.SelectAutoLock(Duration.ZERO))

        assertEquals(listOf(Duration.ZERO), environment.autoLock.written)
        assertEquals(Duration.ZERO, state.autoLockTimeout)
    }

    @Test
    fun 선택지가_아닌_자동_잠금_시간은_저장하지_않는다() {
        attach()

        send(SettingsViewAction.SelectAutoLock(7.minutes))

        assertEquals(emptyList(), environment.autoLock.written)
        assertEquals(DEFAULT_AUTO_LOCK, state.autoLockTimeout)
    }

    @Test
    fun 다른_탭이_바꾼_자동_잠금_시간을_받는다() {
        attach()

        environment.autoLock.emit(60.minutes)

        assertEquals(60.minutes, state.autoLockTimeout)
    }

    // --- 세션 제어 ---

    @Test
    fun 잠그기와_로그아웃은_부모에게_올린다() {
        attach()

        send(SettingsViewAction.RequestLock)
        send(SettingsViewAction.RequestLogout)

        assertEquals(listOf("lock", "logout"), listener.events)
    }

    // --- 백업 내보내기 ---

    @Test
    fun 내보내기는_안내를_보인_뒤_확인하면_저장한다() {
        attach()

        send(SettingsViewAction.StartExport)
        assertEquals(SettingsModal.EXPORT_CONFIRM, state.modal)
        assertTrue(files.saved.isEmpty())

        send(SettingsViewAction.ConfirmExport)

        assertEquals(1, files.saved.size)
        assertEquals(EXPORTED, files.saved.single())
        assertEquals(listOf(environment.clock.now() to environment.calendar), session.exportCalls)
        assertEquals(SettingsModal.NONE, state.modal)
        assertFalse(state.inProgress)
    }

    @Test
    fun 내보내기_안내를_취소하면_저장하지_않는다() {
        attach()
        send(SettingsViewAction.StartExport)

        send(SettingsViewAction.CancelModal)

        assertEquals(SettingsModal.NONE, state.modal)
        assertTrue(session.exportCalls.isEmpty())
    }

    @Test
    fun 내보내기가_실패하면_오류를_보이고_안내는_남는다() {
        session.exportError = NetworkError()
        attach()
        send(SettingsViewAction.StartExport)

        send(SettingsViewAction.ConfirmExport)

        assertEquals(describeError(NetworkError()), state.error)
        assertEquals(SettingsModal.EXPORT_CONFIRM, state.modal)
        assertTrue(files.saved.isEmpty())
        assertFalse(state.inProgress)
    }

    @Test
    fun 내보내는_동안에는_진행_중이고_취소와_중복_확인을_받지_않는다() {
        val gate = CompletableDeferred<Unit>()
        session.exportGate = gate
        attach()
        send(SettingsViewAction.StartExport)

        send(SettingsViewAction.ConfirmExport)
        assertTrue(state.inProgress)
        assertFalse(state.canExportBackup)
        send(SettingsViewAction.ConfirmExport)
        send(SettingsViewAction.CancelModal)
        assertEquals(SettingsModal.EXPORT_CONFIRM, state.modal)

        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, session.exportCalls.size)
        assertEquals(SettingsModal.NONE, state.modal)
    }

    @Test
    fun 읽기_전용_볼트도_내보낼_수_있다() {
        session.current = unlocked(readOnly = true)
        attach()

        assertTrue(state.canExportBackup)
        send(SettingsViewAction.StartExport)
        send(SettingsViewAction.ConfirmExport)

        assertEquals(1, files.saved.size)
    }

    // --- 백업 복원 ---

    @Test
    fun 파일을_고르면_형식을_확인하고_비밀번호_모달을_연다() {
        attach()

        pickValidBackup()

        assertEquals(SettingsModal.RESTORE_PASSWORD, state.modal)
        assertFalse(state.inProgress)
        assertNull(state.error)
    }

    @Test
    fun 파일_선택을_취소하면_아무것도_하지_않는다() {
        attach()

        files.picked = null
        send(SettingsViewAction.StartRestore)

        assertEquals(SettingsModal.NONE, state.modal)
        assertNull(state.error)
    }

    @Test
    fun 파일_선택_창이_실패하면_오류를_보인다() {
        files.pickError = NetworkError()
        attach()

        send(SettingsViewAction.StartRestore)

        assertEquals(describeError(NetworkError()), state.error)
        assertEquals(SettingsModal.NONE, state.modal)
    }

    @Test
    fun 백업_파일이_아니면_비밀번호를_묻지_않고_오류를_보인다() {
        files.picked = BackupFileSource(8) { "not json" }
        attach()

        send(SettingsViewAction.StartRestore)

        assertEquals(NOT_A_BACKUP, state.error)
        assertEquals(SettingsModal.NONE, state.modal)
    }

    @Test
    fun 한도를_넘는_파일은_내용을_읽지_않는다() {
        var read = false
        files.picked = BackupFileSource(Int.MAX_VALUE) { read = true; "" }
        attach()

        send(SettingsViewAction.StartRestore)

        assertFalse(read)
        assertEquals(NOT_A_BACKUP, state.error)
    }

    @Test
    fun 비밀번호를_넣고_복원하면_안내를_보이고_부모에게_알린다() {
        session.restoreCount = 3
        attach()
        pickValidBackup()

        send(SettingsViewAction.EditRestorePassword("backup-pass"))
        assertTrue(state.canSubmitRestore)
        send(SettingsViewAction.SubmitRestore)

        assertEquals(listOf("backup-pass"), session.restorePasswords)
        assertEquals(environment.clock.now(), session.restoreTimes.single())
        assertEquals(restoredMessage(3), state.notice)
        assertEquals(SettingsModal.NONE, state.modal)
        assertEquals("", state.restorePassword)
        assertEquals(listOf("restored", "changed"), listener.events)
    }

    @Test
    fun 비밀번호가_비어_있으면_복원하지_않는다() {
        attach()
        pickValidBackup()

        assertFalse(state.canSubmitRestore)
        send(SettingsViewAction.SubmitRestore)

        assertTrue(session.restorePasswords.isEmpty())
        assertEquals(SettingsModal.RESTORE_PASSWORD, state.modal)
    }

    @Test
    fun 복원에_실패하면_입력을_비우고_모달에서_다시_입력받는다() {
        session.restoreError = BackupDecryptException()
        attach()
        pickValidBackup()
        send(SettingsViewAction.EditRestorePassword("wrong"))

        send(SettingsViewAction.SubmitRestore)

        assertEquals(BACKUP_OPEN_FAILED, state.error)
        assertEquals("", state.restorePassword)
        assertEquals(SettingsModal.RESTORE_PASSWORD, state.modal)
        assertFalse(state.inProgress)

        session.restoreError = null
        send(SettingsViewAction.EditRestorePassword("right"))
        send(SettingsViewAction.SubmitRestore)

        assertEquals(listOf("wrong", "right"), session.restorePasswords)
        assertEquals(SettingsModal.NONE, state.modal)
        assertNull(state.error)
    }

    @Test
    fun 복원_모달을_취소하면_입력과_오류를_지운다() {
        session.restoreError = BackupDecryptException()
        attach()
        pickValidBackup()
        send(SettingsViewAction.EditRestorePassword("wrong"))
        send(SettingsViewAction.SubmitRestore)
        send(SettingsViewAction.EditRestorePassword("typed"))

        send(SettingsViewAction.CancelModal)

        assertEquals(SettingsModal.NONE, state.modal)
        assertEquals("", state.restorePassword)
        assertNull(state.error)
        // 취소한 뒤에는 이전 파일로 복원하지 않는다
        send(SettingsViewAction.SubmitRestore)
        assertEquals(1, session.restorePasswords.size)
    }

    @Test
    fun 복원하는_동안에는_취소와_입력을_받지_않는다() {
        val gate = CompletableDeferred<Unit>()
        session.restoreGate = gate
        attach()
        pickValidBackup()
        send(SettingsViewAction.EditRestorePassword("pw"))

        send(SettingsViewAction.SubmitRestore)
        assertTrue(state.inProgress)
        assertFalse(state.canRestoreBackup)
        send(SettingsViewAction.CancelModal)
        send(SettingsViewAction.EditRestorePassword("changed"))
        send(SettingsViewAction.SubmitRestore)
        assertEquals(SettingsModal.RESTORE_PASSWORD, state.modal)
        assertEquals("", state.restorePassword)

        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf("pw"), session.restorePasswords)
        assertEquals(SettingsModal.NONE, state.modal)
    }

    @Test
    fun 읽기_전용_볼트에는_복원하지_않는다() {
        session.current = unlocked(readOnly = true)
        attach()

        assertFalse(state.canRestoreBackup)
        pickValidBackup()

        assertEquals(0, files.pickCalls)
        assertEquals(SettingsModal.NONE, state.modal)
    }

    @Test
    fun 파일을_다시_고르면_늦게_끝난_이전_선택은_버린다() {
        val first = CompletableDeferred<BackupFileSource?>()
        val second = CompletableDeferred<BackupFileSource?>()
        files.pickGates = ArrayDeque(listOf(first, second))
        attach()

        send(SettingsViewAction.StartRestore)
        send(SettingsViewAction.StartRestore)
        second.complete(backupSource())
        dispatcher.scheduler.runCurrent()
        assertEquals(SettingsModal.RESTORE_PASSWORD, state.modal)

        first.complete(BackupFileSource(8) { "not json" })
        dispatcher.scheduler.runCurrent()

        assertEquals(SettingsModal.RESTORE_PASSWORD, state.modal)
        assertNull(state.error)
    }

    @Test
    fun 내보내기_안내가_열려_있어도_복원_파일을_고르면_안내를_닫는다() {
        attach()
        send(SettingsViewAction.StartExport)

        pickValidBackup()

        assertEquals(SettingsModal.RESTORE_PASSWORD, state.modal)
    }

    // --- 비밀번호 변경 ---

    @Test
    fun 비밀번호_변경_모달을_연다() {
        attach()

        assertTrue(state.canChangePassword)
        send(SettingsViewAction.StartChangePassword)

        assertEquals(SettingsModal.CHANGE_PASSWORD, state.modal)
        assertNull(state.notice)
    }

    @Test
    fun 네트워크가_끊기면_비밀번호_변경이_꺼지고_눌러도_이유만_알린다() {
        environment.offline = true
        attach()

        assertFalse(state.canChangePassword)
        send(SettingsViewAction.StartChangePassword)

        assertEquals(PASSWORD_CHANGE_OFFLINE, state.notice)
        assertEquals(SettingsModal.NONE, state.modal)
    }

    @Test
    fun 세션_없이_연_볼트에서는_비밀번호_변경이_꺼진다() {
        session.current = unlocked(signedIn = false)
        attach()

        assertFalse(state.canChangePassword)
        send(SettingsViewAction.StartChangePassword)

        assertEquals(PASSWORD_CHANGE_OFFLINE, state.notice)
        assertEquals(SettingsModal.NONE, state.modal)
    }

    @Test
    fun 읽기_전용_볼트에서는_비밀번호를_바꾸지_않는다() {
        session.current = unlocked(readOnly = true)
        attach()

        assertFalse(state.canChangePassword)
        send(SettingsViewAction.StartChangePassword)

        assertEquals(SettingsModal.NONE, state.modal)
        assertNull(state.notice)
    }

    @Test
    fun 현재_비밀번호가_비어_있으면_변경_버튼이_꺼져_있다() {
        attach()
        send(SettingsViewAction.StartChangePassword)

        assertFalse(state.canSubmitChangePassword)
        send(SettingsViewAction.SubmitChangePassword)

        assertTrue(session.changeCalls.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun 새_비밀번호가_짧으면_서버에_보내지_않고_알린다() {
        attach()
        openChangePassword(next = "short")

        send(SettingsViewAction.SubmitChangePassword)

        assertEquals("마스터 비밀번호는 12자 이상이어야 합니다.", state.error)
        assertTrue(session.changeCalls.isEmpty())
        assertEquals(LONG_PASSWORD, state.currentPassword)
    }

    @Test
    fun 확인_입력이_다르면_서버에_보내지_않고_알린다() {
        attach()
        openChangePassword(confirmation = "different long password")

        send(SettingsViewAction.SubmitChangePassword)

        assertEquals("비밀번호 확인이 일치하지 않습니다.", state.error)
        assertTrue(session.changeCalls.isEmpty())
    }

    @Test
    fun 새_비밀번호가_지금과_같으면_서버에_보내지_않고_알린다() {
        attach()
        openChangePassword(next = LONG_PASSWORD)

        send(SettingsViewAction.SubmitChangePassword)

        assertEquals(SAME_MASTER_PASSWORD, state.error)
        assertTrue(session.changeCalls.isEmpty())
    }

    @Test
    fun 비밀번호를_바꾸면_완료_안내를_보이고_입력을_비우고_부모에게_알린다() {
        attach()
        openChangePassword()

        send(SettingsViewAction.SubmitChangePassword)

        assertEquals(listOf(LONG_PASSWORD to NEW_PASSWORD), session.changeCalls)
        assertEquals(PASSWORD_CHANGE_DONE, state.notice)
        assertEquals(SettingsModal.NONE, state.modal)
        assertEquals("", state.currentPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.passwordConfirmation)
        assertNull(state.error)
        assertEquals(listOf("passwordChanged", "changed"), listener.events)
    }

    @Test
    fun 비밀번호를_바꾸는_동안에는_현재_비밀번호를_State에서_버리고_진행_중이다() {
        val gate = CompletableDeferred<Unit>()
        session.changeGate = gate
        attach()
        openChangePassword()

        send(SettingsViewAction.SubmitChangePassword)

        assertTrue(state.inProgress)
        assertEquals("", state.currentPassword)
        assertFalse(state.canChangePassword)
        send(SettingsViewAction.CancelModal)
        assertEquals(SettingsModal.CHANGE_PASSWORD, state.modal)

        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()
        assertFalse(state.inProgress)
    }

    @Test
    fun 변경에_실패하면_모달에서_오류를_보이고_현재_비밀번호를_다시_받는다() {
        session.changeError = WrongMasterPasswordException()
        attach()
        openChangePassword()

        send(SettingsViewAction.SubmitChangePassword)

        assertEquals("마스터 비밀번호가 올바르지 않습니다.", state.error)
        assertEquals(SettingsModal.CHANGE_PASSWORD, state.modal)
        assertEquals("", state.currentPassword)
        assertEquals(NEW_PASSWORD, state.newPassword)
        assertEquals(listOf("changed"), listener.events)
    }

    @Test
    fun 비밀번호_변경_중_세션이_만료되면_로그인_화면으로_보내며_이유를_알린다() {
        session.changeError = SessionExpiredException()
        attach()
        openChangePassword()

        send(SettingsViewAction.SubmitChangePassword)

        assertEquals(listOf("end:${describeError(SessionExpiredException())}"), listener.events)
        assertNull(state.error)
        assertEquals(SettingsModal.NONE, state.modal)
    }

    @Test
    fun 다른_곳에서_비밀번호가_바뀌었다는_응답도_로그인_화면으로_보낸다() {
        session.changeError = MasterPasswordChangedException()
        attach()
        openChangePassword()

        send(SettingsViewAction.SubmitChangePassword)

        assertEquals(listOf("end:${describeError(MasterPasswordChangedException())}"), listener.events)
    }

    @Test
    fun 비밀번호_변경_모달을_취소하면_입력과_오류를_지운다() {
        attach()
        openChangePassword(next = "short")
        send(SettingsViewAction.SubmitChangePassword)

        send(SettingsViewAction.CancelModal)

        assertEquals(SettingsModal.NONE, state.modal)
        assertEquals("", state.currentPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.passwordConfirmation)
        assertNull(state.error)
    }

    @Test
    fun 복원에_실패하면_성공_이벤트는_올리지_않고_시도만_알린다() {
        session.restoreError = BackupDecryptException()
        attach()
        pickValidBackup()
        send(SettingsViewAction.EditRestorePassword("wrong"))

        send(SettingsViewAction.SubmitRestore)

        assertEquals(listOf("changed"), listener.events)
    }

    // --- 내려갈 때 평문 정리 ---

    @Test
    fun 내려가기_전에_입력해_둔_비밀번호를_State에서_비우고_그려_둔다() {
        attach()
        openChangePassword()
        send(SettingsViewAction.EditNewPassword("typed-new-password"))
        send(SettingsViewAction.EditPasswordConfirmation("typed-new-password"))
        assertEquals("typed-new-password", state.newPassword)

        mount?.unmount()

        assertEquals("", state.currentPassword)
        assertEquals("", state.newPassword)
        assertEquals("", state.passwordConfirmation)
        assertEquals("", state.restorePassword)
        assertEquals("", state.deletionPassword)
        assertEquals("", state.deletionPhrase)
    }

    // --- 계정 삭제 ---

    @Test
    fun 계정_삭제_모달을_연다() {
        attach()

        assertTrue(state.canDeleteAccount)
        send(SettingsViewAction.StartDeleteAccount)

        assertEquals(SettingsModal.DELETE_ACCOUNT, state.modal)
    }

    @Test
    fun 네트워크가_끊기거나_세션이_없으면_계정_삭제가_꺼지고_이유만_알린다() {
        environment.offline = true
        attach()

        assertFalse(state.canDeleteAccount)
        send(SettingsViewAction.StartDeleteAccount)

        assertEquals(ACCOUNT_DELETION_OFFLINE, state.notice)
        assertEquals(SettingsModal.NONE, state.modal)

        environment.changeOffline(false)
        session.current = unlocked(signedIn = false)
        session.fireSyncChange()
        assertFalse(state.canDeleteAccount)
    }

    @Test
    fun 읽기_전용_볼트에서도_계정을_지울_수_있다() {
        session.current = unlocked(readOnly = true)
        attach()

        assertTrue(state.canDeleteAccount)
        openDeleteAccount()
        send(SettingsViewAction.SubmitDeleteAccount)

        assertEquals(listOf(LONG_PASSWORD), session.deleteCalls)
    }

    @Test
    fun 확인_문구가_정확히_같고_비밀번호가_있을_때만_삭제할_수_있다() {
        attach()
        send(SettingsViewAction.StartDeleteAccount)

        send(SettingsViewAction.EditDeletionPassword(LONG_PASSWORD))
        send(SettingsViewAction.EditDeletionPhrase("계정삭제"))
        assertFalse(state.canSubmitDeletion)
        send(SettingsViewAction.EditDeletionPhrase("계정 삭제 "))
        assertFalse(state.canSubmitDeletion)
        send(SettingsViewAction.SubmitDeleteAccount)
        assertTrue(session.deleteCalls.isEmpty())

        send(SettingsViewAction.EditDeletionPhrase("계정 삭제"))
        assertTrue(state.canSubmitDeletion)
        send(SettingsViewAction.EditDeletionPassword(""))
        assertFalse(state.canSubmitDeletion)
    }

    @Test
    fun 확인_문구는_앞뒤_공백이_하나라도_붙으면_다르고_정확히_같을_때만_삭제_버튼이_켜진다() {
        attach()
        send(SettingsViewAction.StartDeleteAccount)
        send(SettingsViewAction.EditDeletionPassword(LONG_PASSWORD))

        send(SettingsViewAction.EditDeletionPhrase("계정 삭제 "))
        assertFalse(state.canSubmitDeletion)
        send(SettingsViewAction.EditDeletionPhrase(" 계정 삭제"))
        assertFalse(state.canSubmitDeletion)
        send(SettingsViewAction.EditDeletionPhrase(" 계정 삭제 "))
        assertFalse(state.canSubmitDeletion)
        send(SettingsViewAction.SubmitDeleteAccount)
        assertTrue(session.deleteCalls.isEmpty())

        send(SettingsViewAction.EditDeletionPhrase("계정 삭제"))
        assertTrue(state.canSubmitDeletion)
        send(SettingsViewAction.SubmitDeleteAccount)
        assertEquals(listOf(LONG_PASSWORD), session.deleteCalls)
    }

    @Test
    fun 지운_뒤_로그인_화면에_보이는_완료_안내는_정책_문구_그대로다() {
        attach()
        openDeleteAccount()

        send(SettingsViewAction.SubmitDeleteAccount)

        // 정책(SEC-005) 표준 문구. 상수를 거치지 않고 문구 자체를 고정한다
        assertEquals(listOf("end:계정을 삭제했습니다."), listener.events)
    }

    @Test
    fun 지우면_로그인_화면으로_보내며_완료_안내를_알린다() {
        attach()
        openDeleteAccount()

        send(SettingsViewAction.SubmitDeleteAccount)

        assertEquals(listOf(LONG_PASSWORD), session.deleteCalls)
        assertEquals(listOf("end:$ACCOUNT_DELETED"), listener.events)
        assertEquals(SettingsModal.NONE, state.modal)
        assertEquals("", state.deletionPassword)
        assertEquals("", state.deletionPhrase)
    }

    @Test
    fun 기기_기록을_지우지_못하면_로그인_화면에_직접_지우라는_안내를_알린다() {
        session.deleteError = AccountDeletedDeviceNotClearedException()
        attach()
        openDeleteAccount()

        send(SettingsViewAction.SubmitDeleteAccount)

        assertEquals(listOf("end:$ACCOUNT_DELETED_DEVICE_NOT_CLEARED"), listener.events)
        assertNull(state.error)
    }

    @Test
    fun 계정_삭제_중_세션이_만료되면_로그인_화면으로_보내며_이유를_알린다() {
        session.deleteError = SessionExpiredException()
        attach()
        openDeleteAccount()

        send(SettingsViewAction.SubmitDeleteAccount)

        assertEquals(listOf("end:${describeError(SessionExpiredException())}"), listener.events)
    }

    @Test
    fun 삭제에_실패하면_모달에서_오류를_보이고_비밀번호를_다시_받는다() {
        session.deleteError = WrongMasterPasswordException()
        attach()
        openDeleteAccount()

        send(SettingsViewAction.SubmitDeleteAccount)

        assertEquals("마스터 비밀번호가 올바르지 않습니다.", state.error)
        assertEquals(SettingsModal.DELETE_ACCOUNT, state.modal)
        assertEquals("", state.deletionPassword)
        assertEquals("계정 삭제", state.deletionPhrase)
        assertFalse(state.canSubmitDeletion)
        assertEquals(listOf("changed"), listener.events)
    }

    @Test
    fun 삭제하는_동안에는_진행_중이고_취소와_중복_제출을_받지_않는다() {
        val gate = CompletableDeferred<Unit>()
        session.deleteGate = gate
        attach()
        openDeleteAccount()

        send(SettingsViewAction.SubmitDeleteAccount)
        assertTrue(state.inProgress)
        send(SettingsViewAction.EditDeletionPassword("again"))
        send(SettingsViewAction.SubmitDeleteAccount)
        send(SettingsViewAction.CancelModal)
        assertEquals(SettingsModal.DELETE_ACCOUNT, state.modal)

        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, session.deleteCalls.size)
        assertEquals(listOf("end:$ACCOUNT_DELETED"), listener.events)
    }

    @Test
    fun 계정_삭제_모달을_취소하면_입력과_오류를_지운다() {
        attach()
        openDeleteAccount()

        send(SettingsViewAction.CancelModal)

        assertEquals(SettingsModal.NONE, state.modal)
        assertEquals("", state.deletionPassword)
        assertEquals("", state.deletionPhrase)
    }

    // --- 공통 ---

    @Test
    fun 안내와_오류를_지울_수_있다() {
        environment.offline = true
        attach()
        send(SettingsViewAction.StartChangePassword)
        assertNotNull(state.notice)

        send(SettingsViewAction.DismissMessage)

        assertNull(state.notice)
    }

    @Test
    fun 한_번에_모달_하나만_연다() {
        attach()
        send(SettingsViewAction.StartExport)

        send(SettingsViewAction.StartChangePassword)
        assertEquals(SettingsModal.CHANGE_PASSWORD, state.modal)

        send(SettingsViewAction.StartDeleteAccount)
        assertEquals(SettingsModal.DELETE_ACCOUNT, state.modal)
        assertEquals("", state.currentPassword)
    }

    @Test
    fun 비활성이_되면_구독을_풀고_뷰_입력을_받지_않는다() {
        attach()

        mount?.unmount()
        // 내려가며 평문을 비운 마지막 한 번만 그린다. 그 뒤로는 그리지 않는다
        val rendered = view.states.size
        environment.changeOffline(true)
        session.fireSyncChange()
        environment.autoLock.emit(60.minutes)

        assertEquals(rendered, view.states.size)
        assertNull(view.listener)
        assertEquals(0, environment.networkListeners.size)
        assertEquals(0, session.syncListeners.size)
        assertEquals(0, session.remoteListeners.size)
        assertEquals(0, environment.autoLock.listeners.size)
    }

    @Test
    fun 진행_중_비활성이_되면_요청_결과를_부모에게_알리지_않는다() {
        val gate = CompletableDeferred<Unit>()
        session.deleteGate = gate
        attach()
        openDeleteAccount()
        send(SettingsViewAction.SubmitDeleteAccount)

        mount?.unmount()
        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()

        assertTrue(listener.events.isEmpty())
    }

    // --- 진입 버튼과 안내 종류 (오프라인 판단 분리) ---

    @Test
    fun 오프라인이어도_진입_버튼은_눌러_안내를_받을_수_있다() {
        environment.offline = true
        attach()

        // 실제로 바꿀 수는 없지만(canX), 진입 버튼은 켜 둔다(canStartX). 오프라인 안내는 눌렀을 때 notice로 온다
        assertFalse(state.canChangePassword)
        assertTrue(state.canStartChangePassword)
        assertFalse(state.canDeleteAccount)
        assertTrue(state.canStartDeleteAccount)
    }

    @Test
    fun 읽기_전용_볼트에서는_비밀번호_변경_진입_버튼만_막고_계정_삭제는_연다() {
        session.current = unlocked(readOnly = true)
        attach()

        assertFalse(state.canStartChangePassword)
        assertTrue(state.canStartDeleteAccount)
    }

    @Test
    fun 진행_중에는_두_진입_버튼을_막는다() {
        val gate = CompletableDeferred<Unit>()
        session.changeGate = gate
        attach()
        openChangePassword()

        send(SettingsViewAction.SubmitChangePassword)

        assertTrue(state.inProgress)
        assertFalse(state.canStartChangePassword)
        assertFalse(state.canStartDeleteAccount)
        gate.complete(Unit)
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun 오프라인_안내는_오류_종류다() {
        environment.offline = true
        attach()

        send(SettingsViewAction.StartChangePassword)
        assertEquals(PASSWORD_CHANGE_OFFLINE, state.notice)
        assertEquals(SettingsNoticeKind.ERROR, state.noticeKind)

        send(SettingsViewAction.StartDeleteAccount)
        assertEquals(ACCOUNT_DELETION_OFFLINE, state.notice)
        assertEquals(SettingsNoticeKind.ERROR, state.noticeKind)
    }

    @Test
    fun 완료_안내는_일반_종류로_바뀐다() {
        environment.offline = true
        attach()
        send(SettingsViewAction.StartChangePassword)
        assertEquals(SettingsNoticeKind.ERROR, state.noticeKind)

        environment.changeOffline(false)
        session.fireSyncChange()
        openChangePassword()
        send(SettingsViewAction.SubmitChangePassword)

        assertEquals(PASSWORD_CHANGE_DONE, state.notice)
        assertEquals(SettingsNoticeKind.INFO, state.noticeKind)
    }

    @Test
    fun 백업_복원_완료_안내는_일반_종류다() {
        session.restoreCount = 2
        attach()
        pickValidBackup()
        send(SettingsViewAction.EditRestorePassword("backup-pass"))
        send(SettingsViewAction.SubmitRestore)

        assertEquals(restoredMessage(2), state.notice)
        assertEquals(SettingsNoticeKind.INFO, state.noticeKind)
    }

    // --- 도우미 ---

    private fun unlocked(
        email: String = EMAIL,
        pendingSync: Boolean = false,
        signedIn: Boolean = true,
        readOnly: Boolean = false,
    ): SessionState = SessionState.Unlocked(
        email = email,
        vault = SyncedVault(revision = 1, data = emptyVault(Instant.fromEpochSeconds(0)), readOnly = readOnly, payload = null),
        pendingSync = pendingSync,
        signedIn = signedIn,
        storedOnDevice = true,
    )

    private fun backupSource(): BackupFileSource {
        val kdf = UnlockKdf(toBase64(ByteArray(16)), 600_000)
        val vault = EncryptedVaultPayload(1, "AES-256-GCM", toBase64(ByteArray(12)), toBase64(ByteArray(8)), toBase64(ByteArray(16)))
        val file = createBackupFile(vault, kdf, Instant.fromEpochSeconds(0)) { LocalDay(2026, 1, 2) }
        return BackupFileSource(file.text.length) { file.text }
    }

    private inner class FakeSession : SessionPort {
        var current: SessionState = unlocked()
        val exportCalls = mutableListOf<Pair<Instant, LocalCalendar>>()
        var exportError: Throwable? = null
        var exportGate: CompletableDeferred<Unit>? = null
        val restorePasswords = mutableListOf<String>()
        val restoreTimes = mutableListOf<Instant>()
        var restoreCount = 1
        var restoreError: Throwable? = null
        var restoreGate: CompletableDeferred<Unit>? = null
        val changeCalls = mutableListOf<Pair<String, String>>()
        var changeError: Throwable? = null
        var changeGate: CompletableDeferred<Unit>? = null
        val deleteCalls = mutableListOf<String>()
        var deleteError: Throwable? = null
        var deleteGate: CompletableDeferred<Unit>? = null
        val syncListeners = mutableListOf<SyncChangeListener>()
        val remoteListeners = mutableListOf<() -> Unit>()

        fun fireSyncChange() = syncListeners.toList().forEach { it(null) }
        fun fireRemoteChange() = remoteListeners.toList().forEach { it() }

        override fun state() = current

        override suspend fun exportBackup(now: Instant, calendar: LocalCalendar): BackupFile {
            exportCalls += now to calendar
            exportGate?.await()
            exportError?.let { throw it }
            return EXPORTED
        }

        override suspend fun restoreBackup(backup: VaultBackup, password: String, now: Instant): Int {
            restorePasswords += password
            restoreTimes += now
            restoreGate?.await()
            restoreError?.let { throw it }
            return restoreCount
        }

        override suspend fun changeMasterPassword(currentPassword: String, newPassword: String) {
            changeCalls += currentPassword to newPassword
            changeGate?.await()
            changeError?.let { throw it }
        }

        override suspend fun deleteAccount(password: String) {
            deleteCalls += password
            deleteGate?.await()
            deleteError?.let { throw it }
        }

        override fun onSyncChange(listener: SyncChangeListener): () -> Unit {
            syncListeners += listener
            return { syncListeners.remove(listener) }
        }

        override fun onRemoteChange(listener: () -> Unit): () -> Unit {
            remoteListeners += listener
            return { remoteListeners.remove(listener) }
        }

        override suspend fun register(email: String, password: String) = unused()
        override suspend fun login(email: String, password: String, acceptRollback: Boolean) = unused()
        override fun lock(scope: LockScope) = unused()
        override suspend fun unlock(password: String, acceptRollback: Boolean): Boolean = unused()
        override suspend fun reconnect(password: String, acceptRollback: Boolean) = unused()
        override suspend fun save(data: VaultData) = unused()
        override suspend fun logout() = unused()
        override suspend fun pendingOnDevice(): Boolean = unused()
        override fun onLock(listener: () -> Unit): () -> Unit = unused()
        override fun onUnlock(listener: () -> Unit): () -> Unit = unused()

        private fun unused(): Nothing = error("설정 화면이 쓰지 않는 세션 기능")
    }

    private class FakeBackupFiles : BackupFilesPort {
        val saved = mutableListOf<BackupFile>()
        var picked: BackupFileSource? = null
        var pickError: Throwable? = null
        var pickCalls = 0
        var pickGates: ArrayDeque<CompletableDeferred<BackupFileSource?>>? = null

        override suspend fun save(file: BackupFile) {
            saved += file
        }

        override suspend fun pick(): BackupFileSource? {
            pickCalls += 1
            pickError?.let { throw it }
            return pickGates?.removeFirst()?.await() ?: picked
        }
    }

    private class FakeAutoLockStore : AutoLockSettingStore {
        var value: Duration = DEFAULT_AUTO_LOCK
        val written = mutableListOf<Duration>()
        val listeners = mutableListOf<(Duration) -> Unit>()

        override fun read() = value

        override fun write(timeout: Duration) {
            written += timeout
            value = timeout
        }

        override fun subscribe(listener: (Duration) -> Unit): () -> Unit {
            listeners += listener
            return { listeners.remove(listener) }
        }

        fun emit(timeout: Duration) {
            value = timeout
            listeners.toList().forEach { it(timeout) }
        }
    }

    private class FakeEnvironment : AppEnvironment {
        override val clock: Clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochSeconds(1_700_000_000)
        }
        override val calendar = LocalCalendar { LocalDay(2026, 1, 2) }
        val autoLock = FakeAutoLockStore()
        override val autoLockSetting: AutoLockSettingStore get() = autoLock
        override var serverAddress: String = ""
        var offline = false
        val networkListeners = mutableListOf<() -> Unit>()

        override fun isNetworkOffline() = offline

        override fun subscribeNetwork(listener: () -> Unit): () -> Unit {
            networkListeners += listener
            return { networkListeners.remove(listener) }
        }

        fun changeOffline(value: Boolean) {
            offline = value
            networkListeners.toList().forEach { it() }
        }
    }

    private class FakeSettingsView : SettingsViewContract {
        override var listener: SettingsPresentableListener? = null
        val states = mutableListOf<SettingsViewState>()

        override fun render(state: SettingsViewState) {
            states += state
        }
    }

    private class FakeSettingsListener : SettingsListener {
        val events = mutableListOf<String>()

        override fun settingsDidRequestLock() {
            events += "lock"
        }

        override fun settingsDidRequestLogout() {
            events += "logout"
        }

        override fun settingsDidRestoreBackup() {
            events += "restored"
        }

        override fun settingsDidChangeMasterPassword() {
            events += "passwordChanged"
        }

        override fun settingsDidChangeSession() {
            events += "changed"
        }

        override fun settingsDidEndSession(notice: String) {
            events += "end:$notice"
        }
    }
}

private class SingleDispatcher(dispatcher: CoroutineDispatcher) : RibDispatchersProvider {
    override val Main: CoroutineDispatcher = dispatcher
    override val MainImmediate: CoroutineDispatcher = dispatcher
    override val Default: CoroutineDispatcher = dispatcher
    override val Unconfined: CoroutineDispatcher = dispatcher
}
