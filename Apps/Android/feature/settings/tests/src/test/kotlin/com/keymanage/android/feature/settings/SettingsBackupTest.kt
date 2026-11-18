package com.keymanage.android.feature.settings

import com.keymanage.android.domain.backup.BackupError
import com.keymanage.android.domain.sync.SyncError
import com.keymanage.android.feature.settings.impl.SettingsInteractor
import com.keymanage.android.feature.settings.impl.SettingsMessages
import com.keymanage.android.feature.settings.impl.SettingsPresenter
import com.keymanage.android.feature.settings.impl.SettingsScreen
import com.keymanage.android.feature.settings.impl.SettingsViewAction
import com.keymanage.android.feature.settings.impl.SettingsViewState
import com.keymanage.android.feature.settings.testing.MemoryAutoLockStore
import com.keymanage.android.feature.settings.testing.RecordingSettingsListener
import com.keymanage.android.rib.ViewActionStream
import com.keymanage.android.rib.ViewStateStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsBackupTest {
    private val now = Instant.parse("2026-11-18T09:00:00Z")
    private val written = mutableMapOf<String, ByteArray>()
    private var readFailure: Exception? = null
    private var restoreFailure: Exception? = null
    private val restoreCalls = mutableListOf<String>()

    private val documents = object : DocumentIo {
        override fun read(uri: String, maxBytes: Int): ByteArray {
            readFailure?.let { throw it }
            return "file:$uri".toByteArray()
        }

        override fun write(uri: String, bytes: ByteArray) {
            written[uri] = bytes
        }
    }

    private val backup = object : BackupPort {
        override fun export(now: Instant) = "backup@$now".toByteArray()
        override fun restore(bytes: ByteArray, password: String, now: Instant): RestoreOutcome {
            restoreFailure?.let { throw it }
            restoreCalls += password
            return RestoreOutcome(3, null)
        }
    }

    private val store = ViewStateStore(SettingsViewState(email = "dev@example.com"))
    private val interactor = SettingsInteractor(
        SettingsPresenter(store, ViewActionStream()), store, ViewActionStream(), MemoryAutoLockStore(), { false },
        RecordingSettingsListener(), backup, documents, UnconfinedTestDispatcher(), clock = { now },
        today = { LocalDate.of(2026, 11, 18) },
    )

    private fun pickFile() {
        interactor.handle(SettingsViewAction.OpenBackup)
        interactor.handle(SettingsViewAction.RestoreFilePicked("content://b"))
        interactor.handle(SettingsViewAction.RestorePasswordChanged("pw"))
    }

    @Test
    fun `백업 화면은 현지 날짜 파일 이름을 제안한다`() {
        interactor.handle(SettingsViewAction.OpenBackup)
        assertEquals(SettingsScreen.BACKUP, store.current.screen)
        assertEquals("keymanage-backup-20261118.json", store.current.backup.fileName)
    }

    @Test
    fun `내보내면 고른 위치에 백업 바이트를 쓴다`() = runTest {
        interactor.handle(SettingsViewAction.OpenBackup)
        interactor.export("content://out")
        assertContentEquals("backup@$now".toByteArray(), written["content://out"])
        assertEquals(SettingsMessages.EXPORTED, store.current.backup.message)
    }

    @Test
    fun `복원은 확인을 받은 뒤 실행하고 개수를 알리며 비밀번호를 지운다`() = runTest {
        // Given
        pickFile()
        interactor.handle(SettingsViewAction.RequestRestore)
        assertTrue(store.current.backup.isConfirmingRestore)

        // When
        interactor.restore()

        // Then
        assertEquals(listOf("pw"), restoreCalls)
        assertEquals(SettingsMessages.restored(3), store.current.backup.message)
        assertEquals("", store.current.backup.restorePassword)
    }

    @Test
    fun `복원 실패는 VLT-004 문구로 알린다`() = runTest {
        val cases = mapOf<Exception, String>(
            BackupError.CannotOpen() to SettingsMessages.CANNOT_OPEN,
            BackupError.NotBackupFile("x") to SettingsMessages.NOT_BACKUP,
            BackupError.NewerVersion("x") to SettingsMessages.NEWER_VERSION,
            SyncError.ReadOnlyVault(2) to SettingsMessages.READ_ONLY,
            SyncError.VaultTooLarge(6_000_000) to SettingsMessages.TOO_LARGE,
        )
        for ((error, message) in cases) {
            pickFile()
            restoreFailure = error
            interactor.restore()
            assertEquals(message, store.current.backup.message)
            assertTrue(store.current.backup.isError)
        }
    }

    @Test
    fun `파일을 읽지 못하면 알 수 없는 오류로 알린다`() = runTest {
        pickFile()
        readFailure = IOException("x")
        interactor.restore()
        assertEquals(SettingsMessages.UNKNOWN, store.current.backup.message)
    }

    @Test
    fun `파일이나 비밀번호가 없으면 복원 확인을 띄우지 않는다`() {
        interactor.handle(SettingsViewAction.OpenBackup)
        interactor.handle(SettingsViewAction.RequestRestore)
        assertEquals(false, store.current.backup.isConfirmingRestore)
    }
}
