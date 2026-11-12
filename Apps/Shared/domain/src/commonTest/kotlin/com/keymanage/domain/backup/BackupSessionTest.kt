package com.keymanage.domain.backup

import com.keymanage.domain.auth.KdfParams
import com.keymanage.domain.auth.PBKDF2_SHA256
import com.keymanage.domain.auth.UnlockKdf
import com.keymanage.domain.crypto.Vectors
import com.keymanage.domain.crypto.sealVault
import com.keymanage.domain.crypto.text
import com.keymanage.domain.session.FakeAuthApi
import com.keymanage.domain.session.NOW
import com.keymanage.domain.session.PASSWORD
import com.keymanage.domain.session.SessionSetup
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.SessionStateException
import com.keymanage.domain.session.VaultSession
import com.keymanage.domain.session.fields
import com.keymanage.domain.session.serverItemIds
import com.keymanage.domain.session.sessionTest
import com.keymanage.domain.session.setup
import com.keymanage.domain.session.unlockedWithItem
import com.keymanage.domain.vault.InvalidVaultDataException
import com.keymanage.domain.vault.ItemChanges
import com.keymanage.domain.vault.ReadOnlyVaultException
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.VaultItem
import com.keymanage.domain.vault.VaultTooLargeException
import com.keymanage.domain.vault.activeItems
import com.keymanage.domain.vault.addItem
import com.keymanage.domain.vault.deleteItem
import com.keymanage.domain.vault.formatTime
import com.keymanage.domain.vault.updateItem
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

// 열린 볼트에서 백업 내보내기와 복원 (VLT-004)
// 원본: Apps/Web/src/domain/backup/backupSession.test.ts. '복원 규칙 (VLT-004 규칙 6·7)'은 RestoreItemsTest.kt에서 이미 옮겼다
private val EXPORTED_AT = Instant.parse("2026-11-01T09:30:00Z")
private val RESTORED_AT = Instant.parse("2026-11-01T10:00:00Z")
private val other = Vectors.keyCases[1]
private val vector = Vectors.keyCases[0]

// 원본은 기기의 현지 날짜를 썼다. 테스트에서는 UTC 날짜로 고정한다
private val utcCalendar = LocalCalendar { time ->
    val days = time.epochSeconds.floorDiv(86_400)
    // 1970-01-01부터 날짜 계산(테스트 전용, UTC)
    val z = days + 719_468
    val era = z.floorDiv(146_097)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
    LocalDay(year, month, day)
}

private fun unlockedData(session: VaultSession): VaultData =
    (session.state() as? SessionState.Unlocked ?: error("잠금 해제 상태가 아님")).vault.data

private fun pendingSyncOf(session: VaultSession): Boolean =
    (session.state() as? SessionState.Unlocked ?: error("잠금 해제 상태가 아님")).pendingSync

private fun sortedActiveIds(data: VaultData): List<String> = activeItems(data).map { it.id }.sorted()

private fun itemOf(data: VaultData, id: String): VaultItem = data.items.firstOrNull { it.id == id } ?: error("항목 없음: $id")

private suspend fun saveItem(session: VaultSession, id: String) {
    session.save(addItem(unlockedData(session), fields.copy(serviceName = id), NOW, id))
}

private suspend fun exported(session: VaultSession): VaultBackup = parseBackup(session.exportBackup(EXPORTED_AT, utcCalendar).text)

/** 다른 salt·비밀번호를 쓰는 다른 계정으로 로그인한다 */
private suspend fun TestScope.otherAccount(): SessionSetup {
    val authApi = FakeAuthApi()
    authApi.kdf = KdfParams(PBKDF2_SHA256, Vectors.iterations, other.text("salt"))
    val ctx = setup(authApi = authApi)
    ctx.session.login("other@example.com", other.text("password"))
    return ctx
}

/** 서버 볼트가 높은 스키마 버전이라 읽기 전용으로 연 계정 */
private suspend fun TestScope.readOnlyAccount(): SessionSetup {
    val ctx = setup()
    val key = Vectors.keysOf(vector.text("name")).encryptionKey
    ctx.vaultApi.payload = sealVault(key, """{"version":2,"updatedAt":"2026-10-30T00:00:00Z","items":[]}""")
    ctx.vaultApi.revision = 1
    ctx.session.login("user@example.com", PASSWORD)
    return ctx
}

/**
 * 키를 만드는 사이 잠금·로그아웃·계정 전환이 끼어든 상태를 결정적으로 만든다. 첫 상태 조회(복원 시작 전 검사)를 돌려준 직후 [afterFirstState]를 실행한다.
 * JVM의 키 유도는 중단하지 않아 세션의 복원 중간에 끼어들 틈이 없으므로, 원본의 "기다리는 사이"를 이렇게 대신한다
 */
private class InterruptedRestoreTarget(
    private val session: VaultSession,
    private val afterFirstState: () -> Unit,
) : RestoreTarget {
    private var calls = 0

    override fun state(): SessionState {
        val state = session.state()
        if (calls++ == 0) afterFirstState()
        return state
    }

    override suspend fun save(data: VaultData) = session.save(data)
}

class BackupExportTest {
    @Test
    fun exportsOnlyWhenUnlocked() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.session.lock()
        assertFailsWith<SessionStateException> { ctx.session.exportBackup(EXPORTED_AT, utcCalendar) }
        assertFailsWith<SessionStateException> { setup().session.exportBackup(EXPORTED_AT, utcCalendar) }
    }

    @Test
    fun fileHasNoPlaintextEmailOrRevisionAndHoldsVaultEncryptedWithAccountKdfAndNewIv() = sessionTest {
        val ctx = unlockedWithItem()

        val file = ctx.session.exportBackup(EXPORTED_AT, utcCalendar)

        assertEquals(backupFileName(EXPORTED_AT, utcCalendar), file.name)
        for (secret in listOf("GitHub", "octocat", "pw-1", "user@example.com", "example.com", "revision", "token-1")) {
            assertFalse(file.text.contains(secret), "백업 파일에 평문이 있음: $secret")
        }
        val backup = parseBackup(file.text)
        assertEquals(UnlockKdf(ctx.authApi.kdf.salt, ctx.authApi.kdf.iterations), backup.kdf)
        assertEquals("2026-11-01T09:30:00Z", backup.createdAt)
        assertNotEquals(ctx.deviceStore.record?.payload?.iv, backup.vault.iv)
        assertEquals(unlockedData(ctx.session), openBackup(backup, PASSWORD))
    }

    @Test
    fun exportingTwiceEncryptsWithNewIvEachTime() = sessionTest {
        val ctx = unlockedWithItem()

        val first = exported(ctx.session)
        val second = exported(ctx.session)

        assertNotEquals(first.vault.iv, second.vault.iv)
        assertNotEquals(first.vault.ciphertext, second.vault.ciphertext)
    }

    @Test
    fun exportsPendingChangesNotYetUploaded() = sessionTest {
        val ctx = unlockedWithItem()
        ctx.vaultApi.offline = true
        saveItem(ctx.session, "b")
        assertTrue(pendingSyncOf(ctx.session))

        val restored = openBackup(exported(ctx.session), PASSWORD)

        assertEquals(listOf("a", "b"), sortedActiveIds(restored))
    }

    @Test
    fun exportsReadOnlyVaultToo_butThisVersionCannotRestoreIt() = sessionTest {
        val ctx = readOnlyAccount()

        val backup = exported(ctx.session)

        assertFailsWith<NewerBackupVersionException> { openBackup(backup, PASSWORD) }
    }
}

class BackupRestoreTest {
    @Test
    fun restoresItemsOfBackupOpenedWithSamePasswordIntoCurrentVaultSavesToServerAndReturnsCount() = sessionTest {
        val source = unlockedWithItem()
        saveItem(source.session, "b")
        val backup = exported(source.session)
        val target = setup()
        target.session.login("device-2@example.com", PASSWORD)
        saveItem(target.session, "z")

        val count = target.session.restoreBackup(backup, PASSWORD, RESTORED_AT)

        assertEquals(2, count)
        assertEquals(listOf("a", "b", "z"), sortedActiveIds(unlockedData(target.session)))
        assertFalse(pendingSyncOf(target.session))
        assertEquals(listOf("a", "b", "z"), serverItemIds(target.vaultApi))
    }

    @Test
    fun itemsEditedAfterBackupGoBackDeletedAreRevivedNewOnesStay() = sessionTest {
        val ctx = unlockedWithItem()
        saveItem(ctx.session, "b")
        val backup = exported(ctx.session)
        ctx.session.save(updateItem(unlockedData(ctx.session), "a", ItemChanges(username = "edited"), NOW))
        ctx.session.save(deleteItem(unlockedData(ctx.session), "b", NOW))
        saveItem(ctx.session, "c")

        val count = ctx.session.restoreBackup(backup, PASSWORD, RESTORED_AT)

        assertEquals(2, count)
        val data = unlockedData(ctx.session)
        assertEquals(listOf("a", "b", "c"), sortedActiveIds(data))
        assertEquals("octocat", itemOf(data, "a").username)
        assertEquals(formatTime(RESTORED_AT), itemOf(data, "a").updatedAt)
        assertEquals("b", itemOf(data, "b").serviceName)
        assertEquals("pw-1", itemOf(data, "b").password)
        assertEquals(formatTime(RESTORED_AT), itemOf(data, "b").updatedAt)
        assertNull(itemOf(data, "b").deletedAt)
        assertEquals(listOf("a", "b", "c"), serverItemIds(ctx.vaultApi))
    }

    @Test
    fun sameContentAsBackupTouchesNothingAndReturnsZeroWithoutSaving() = sessionTest {
        val ctx = unlockedWithItem()
        val backup = exported(ctx.session)
        saveItem(ctx.session, "c")
        val before = unlockedData(ctx.session)
        val saves = ctx.vaultApi.saves

        val count = ctx.session.restoreBackup(backup, PASSWORD, RESTORED_AT)

        assertEquals(0, count)
        assertSame(before, unlockedData(ctx.session))
        assertEquals(saves, ctx.vaultApi.saves)
    }

    @Test
    fun backupOfOtherAccountRestoresWithItsOwnPassword() = sessionTest {
        val source = otherAccount()
        saveItem(source.session, "x")
        val backup = exported(source.session)
        assertEquals(other.text("salt"), backup.kdf.salt)
        val ctx = unlockedWithItem()

        assertFailsWith<BackupDecryptException> { ctx.session.restoreBackup(backup, PASSWORD, RESTORED_AT) }
        val count = ctx.session.restoreBackup(backup, other.text("password"), RESTORED_AT)

        assertEquals(1, count)
        assertEquals(listOf("a", "x"), sortedActiveIds(unlockedData(ctx.session)))
    }

    @Test
    fun wrongPasswordOrTamperedFileIsRejectedAndVaultStays() = sessionTest {
        val ctx = unlockedWithItem()
        val backup = exported(ctx.session)
        val before = unlockedData(ctx.session)
        val saves = ctx.vaultApi.saves
        val tag = backup.vault.tag
        val flipped = (if (tag[0] == 'A') "B" else "A") + tag.substring(1)
        val tampered = backup.copy(vault = backup.vault.copy(tag = flipped))

        assertFailsWith<BackupDecryptException> { ctx.session.restoreBackup(backup, "wrong password", RESTORED_AT) }
        assertFailsWith<BackupDecryptException> { ctx.session.restoreBackup(tampered, PASSWORD, RESTORED_AT) }

        assertSame(before, unlockedData(ctx.session))
        assertEquals(saves, ctx.vaultApi.saves)
    }

    @Test
    fun doesNotRestoreIntoReadOnlyVault() = sessionTest {
        val source = unlockedWithItem()
        val backup = exported(source.session)
        val ctx = readOnlyAccount()

        assertFailsWith<ReadOnlyVaultException> { ctx.session.restoreBackup(backup, PASSWORD, RESTORED_AT) }
        assertEquals(0, ctx.vaultApi.saves)
    }

    @Test
    fun cancelsWhenNotUnlockedOrLockedWhileRestoring() = sessionTest {
        val ctx = unlockedWithItem()
        val backup = exported(ctx.session)
        val saves = ctx.vaultApi.saves

        // 원본은 복원을 시작한 뒤 바로 잠가 키를 만드는 사이 잠기게 했다. JVM에서는 키 유도가 중단하지 않아 그 틈이 없으므로,
        // 복원 시작 전 검사를 마친 직후 잠그는 대상으로 같은 순서를 만든다 (세션의 restoreBackup은 restoreBackupInto를 그대로 부른다)
        val target = InterruptedRestoreTarget(ctx.session) { ctx.session.lock() }
        assertFailsWith<SessionStateException> { restoreBackupInto(target, backup, PASSWORD, RESTORED_AT) }
        assertFailsWith<SessionStateException> { ctx.session.restoreBackup(backup, PASSWORD, RESTORED_AT) }
        assertEquals(saves, ctx.vaultApi.saves)
    }

    @Test
    fun withoutReachingServerKeepsMergeResultAsPendingSyncAndStoresOnDevice() = sessionTest {
        val source = otherAccount()
        saveItem(source.session, "x")
        val backup = exported(source.session)
        val ctx = unlockedWithItem()
        ctx.vaultApi.offline = true

        val count = ctx.session.restoreBackup(backup, other.text("password"), RESTORED_AT)

        assertEquals(1, count)
        assertTrue(pendingSyncOf(ctx.session))
        assertEquals(listOf("a", "x"), sortedActiveIds(unlockedData(ctx.session)))
        assertEquals(true, ctx.deviceStore.record?.pendingSync)
        ctx.vaultApi.offline = false
        assertEquals(listOf("a"), serverItemIds(ctx.vaultApi))
    }
}

class BackupRestoreExceptionTest {
    @Test
    fun mergeResultOverVaultLimitIsRejectedAsSaveFailureAndVaultStays() = sessionTest {
        val source = otherAccount()
        source.session.save(addItem(unlockedData(source.session), fields.copy(notes = "y".repeat(1_500_000)), NOW, "y"))
        val backup = exported(source.session)
        val ctx = unlockedWithItem()
        ctx.session.save(addItem(unlockedData(ctx.session), fields.copy(notes = "x".repeat(2_500_000)), NOW, "x"))
        val before = unlockedData(ctx.session)
        val saves = ctx.vaultApi.saves

        assertFailsWith<VaultTooLargeException> { ctx.session.restoreBackup(backup, other.text("password"), RESTORED_AT) }

        assertSame(before, unlockedData(ctx.session))
        assertFalse(pendingSyncOf(ctx.session))
        assertEquals(saves, ctx.vaultApi.saves)
    }

    @Test
    fun duplicateItemIdInBackupVaultIsRejectedAsFormatErrorAndVaultStays() = sessionTest {
        val ctx = unlockedWithItem()
        fun itemJson(username: String) =
            """{"id":"dup","serviceName":"GitHub","username":"$username","password":"pw-1","createdAt":"2026-10-01T00:00:00Z","updatedAt":"2026-10-01T00:00:00Z"}"""
        val duplicated = """{"version":1,"updatedAt":"2026-10-01T00:00:00Z","items":[${itemJson("octocat")},${itemJson("second")}]}"""
        val key = Vectors.keysOf(vector.text("name")).encryptionKey
        val backup = parseBackup(createBackupFile(sealVault(key, duplicated), UnlockKdf(vector.text("salt"), Vectors.iterations), NOW, utcCalendar).text)
        val before = unlockedData(ctx.session)
        val saves = ctx.vaultApi.saves

        assertFailsWith<InvalidVaultDataException> { ctx.session.restoreBackup(backup, PASSWORD, RESTORED_AT) }

        assertSame(before, unlockedData(ctx.session))
        assertEquals(saves, ctx.vaultApi.saves)
    }

    @Test
    fun doesNotMergeIfAccountChangesWhileDerivingKey() = sessionTest {
        val source = unlockedWithItem()
        val backup = exported(source.session)
        val account = otherAccount()
        val opened = account.session.state() as SessionState.Unlocked
        var calls = 0
        val saved = mutableListOf<VaultData>()
        val target = object : RestoreTarget {
            // 처음에는 원래 계정, 키를 만든 뒤에는 다른 계정으로 바뀐 상태를 돌려준다
            override fun state(): SessionState = if (calls++ == 0) opened else opened.copy(email = "someone-else@example.com")

            override suspend fun save(data: VaultData) {
                saved += data
            }
        }

        assertFailsWith<SessionStateException> { restoreBackupInto(target, backup, PASSWORD, RESTORED_AT) }
        assertTrue(saved.isEmpty())
    }
}
