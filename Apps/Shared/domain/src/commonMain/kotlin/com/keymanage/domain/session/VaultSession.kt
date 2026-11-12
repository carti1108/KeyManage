package com.keymanage.domain.session

import com.keymanage.domain.auth.MasterPasswordChangedException
import com.keymanage.domain.auth.normalizeEmail
import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.RestoreTarget
import com.keymanage.domain.backup.VaultBackup
import com.keymanage.domain.backup.createBackupFile
import com.keymanage.domain.backup.restoreBackupInto
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.device.rememberedKdf
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.tabs.TabLockName
import com.keymanage.domain.tabs.TabMessage
import com.keymanage.domain.vault.SyncedVault
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.isReadOnly
import com.keymanage.domain.vault.isTransientSyncError
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

// 로그인·가입·잠금·잠금 해제·로그아웃 상태 관리 (SEC-002, SEC-003). 원본: Apps/Web/src/domain/session/vaultSession.ts
// 잠기면 암호화 키와 평문 볼트를 버리고, 이메일·KDF 값·토큰·암호화 볼트만 남긴다
// 서버에 닿지 못한 저장은 동기화 대기로 두고 나중에 다시 보낸다 (VLT-002)
// 상태는 SessionContext, 기기 기록은 DeviceSync, 대기 재전송은 PendingPush, 로그인은 SignIn, 잠금 해제 단계는 UnlockSteps가 맡는다.
// 원본처럼 한 스레드(메인 디스패처)에서만 부른다

class VaultSession(private val deps: VaultSessionDeps) : RestoreTarget {
    private val ctx = SessionContext(deps)
    private val device = ctx.device

    init {
        deps.tabs.channel.subscribe { message -> onTabMessage(ctx, message) }
    }

    override fun state(): SessionState {
        val account = ctx.account ?: return SessionState.SignedOut
        val opened = ctx.unlocked ?: return SessionState.Locked(account.email, account.pendingSync)
        return SessionState.Unlocked(account.email, opened.vault, opened.pendingSync, signedIn = account.accessToken != null, storedOnDevice = !device.hasFailed())
    }

    /** @throws VaultSetupException 가입은 됐지만 빈 볼트를 저장하지 못했을 때 */
    suspend fun register(email: String, password: String) {
        val normalized = normalizeEmail(email)
        val startedAt = beginSignIn(ctx, normalized)
        val result = deps.auth.register(email, password)
        try {
            signIn(ctx, normalized, startedAt, result, acceptRollback = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SessionStateException) {
            throw error
        } catch (error: Exception) {
            throw VaultSetupException(error)
        }
    }

    /**
     * 세션 만료 전에 남긴 동기화 대기 내용이 같은 계정이면 병합해 올린다
     * @throws com.keymanage.domain.vault.RevisionRollbackException 서버 볼트가 되돌아감. 사용자가 확인하면 acceptRollback으로 다시 부른다
     */
    suspend fun login(email: String, password: String, acceptRollback: Boolean = false) {
        val normalized = normalizeEmail(email)
        val startedAt = beginSignIn(ctx, normalized)
        signIn(ctx, normalized, startedAt, deps.auth.login(email, password), acceptRollback)
    }

    /**
     * 키와 평문 볼트를 버린다. 진행 중인 잠금 해제·저장도 취소한다. 동기화 대기 내용은 암호화된 채 남는다.
     * ALL_TABS(잠금 버튼, 보이는 탭의 자동 잠금 시간 초과)면 같은 계정의 다른 탭도 잠근다. 새로고침·떠남·동결, "즉시" 설정의 숨김은 THIS_TAB (SEC-003)
     */
    fun lock(scope: LockScope = LockScope.ALL_TABS) {
        if (lockThisTab(ctx) && scope == LockScope.ALL_TABS) deps.tabs.channel.post(TabMessage.LOCKED)
    }

    /** 잠금 해제 결과. offline: 서버와 맞추지 못했는지 */
    data class UnlockResult(val offline: Boolean)

    /**
     * 저장해 둔 암호화 볼트를 복호화해 비밀번호를 확인하므로 오프라인에서도 동작한다.
     * 확인 뒤 바로 서버와 맞추며(대기 내용이 있으면 병합해 올림), 서버와 맞추지 못하면 저장해 둔 볼트로 연다
     * @throws WrongMasterPasswordException, MasterPasswordChangedException(로그아웃됨), SessionExpiredException(로그아웃됨),
     *   RevisionRollbackException(잠긴 상태 유지), SessionStateException
     */
    suspend fun unlock(password: String, acceptRollback: Boolean = false): UnlockResult {
        if (ctx.account == null) throw SessionStateException("로그인 전에는 잠금을 해제할 수 없음")
        if (ctx.unlocked != null) return UnlockResult(offline = false)
        val startedAt = ctx.epoch
        // 동결 등으로 다른 탭의 알림을 놓쳤을 수 있으니 기기 기록을 다시 읽는다. 기록의 볼트는 연 뒤 쓸 때 병합한다 (ADR-WEB-001)
        // 다른 탭이 비밀번호를 바꿔 로그아웃 상태로 바꿨으면 취소가 아니라 비밀번호 바뀜으로 알린다 (SEC-003 규칙 11)
        if (refreshLockedFromDevice(ctx, replaceMemory = false) == LockedRefresh.PASSWORD_CHANGED) throw MasterPasswordChangedException()
        ensureCurrent(ctx, startedAt)
        val locked = ctx.account ?: throw SessionStateException("다른 탭에서 로그아웃됨")
        if (ctx.unlocked != null) return UnlockResult(offline = false)

        val (keys, data) = verifyOrDetectChange(ctx, password, locked).let { it.keys to it.data }
        // 토큰이 있으면 인증 해시가 필요 없어 바로 지운다. 없으면(기기 기록으로 다시 열었으면) 바로 로그인하고 지운다 (SEC-003, ADR-0019)
        var accessToken = locked.accessToken
        if (accessToken == null) {
            val resumed = resumeSession(ctx, locked, keys.authHash)
            accessToken = resumed.accessToken
            // 오프라인은 조용히 세션 없이 연다. 서버가 거절했으면(출처 거부 등) 사유를 알린다
            val failure = resumed.failure
            if (failure != null && !isTransientSyncError(failure) && ctx.epoch == startedAt) notifySync(ctx, failure)
        }
        keys.authHash.fill(0)
        ensureCurrentOrRevoke(ctx, startedAt, if (locked.accessToken == null) accessToken else null)
        // 받은 토큰은 되돌림 확인으로 잠긴 채 끝나도 남겨, 다시 풀 때 또 로그인하지 않게 한다
        if (locked.accessToken == null && accessToken != null) ctx.account = locked.copy(accessToken = accessToken)

        val sync = syncFor(ctx, accessToken ?: NO_TOKEN, keys.encryptionKey)
        val cached = SyncedVault(locked.revision, data, isReadOnly(data), locked.payload)
        ctx.retryBlocked = false
        val fresh = if (accessToken == null) null else syncAfterUnlock(ctx, sync, cached, locked.pendingSync, acceptRollback)
        ensureCurrent(ctx, startedAt)
        open(ctx, locked.email, accessToken, locked.kdf, sync, fresh ?: cached, fresh == null && locked.pendingSync)
        return UnlockResult(offline = fresh == null)
    }

    /**
     * 세션 없이 연 볼트에서 비밀번호를 다시 입력받아 로그인하고 서버와 맞춘다 (SEC-003, ADR-0019)
     * @throws WrongMasterPasswordException, 로그인하지 못한 실제 사유(NetworkError, ServerError 등), MasterPasswordChangedException(로그아웃됨),
     *   RevisionRollbackException, SessionExpiredException, SessionStateException
     */
    suspend fun reconnect(password: String, acceptRollback: Boolean = false) {
        val current = ctx.account
        if (current == null || ctx.unlocked == null) throw SessionStateException("잠금 해제 상태가 아님")
        if (current.accessToken != null) return
        val startedAt = ctx.epoch
        val keys = verifyOrDetectChange(ctx, password, current).keys
        val resumed = resumeSession(ctx, current, keys.authHash)
        // 왜 동기화하지 못하는지 알 수 있게 실제 사유(연결 실패, 서버 오류, 거절)를 그대로 낸다
        val accessToken = resumed.accessToken ?: throw (resumed.failure ?: NetworkError())
        ensureCurrentOrRevoke(ctx, startedAt, accessToken)
        val opened = ctx.unlocked ?: throw SessionStateException("잠금 해제 상태가 아님")
        val sync = syncFor(ctx, accessToken, keys.encryptionKey)
        ctx.retryBlocked = false
        val fresh = try {
            // 서버와 맞추기 전에는 토큰을 세션에 넣지 않는다. 되돌림이면 세션 없이 두고 확인을 받는다
            syncAfterUnlock(ctx, sync, opened.vault, opened.pendingSync, acceptRollback)
        } catch (error: Throwable) {
            if (error !is CancellationException) revoke(ctx, accessToken)
            throw error
        }
        ensureCurrentOrRevoke(ctx, startedAt, accessToken)
        val latest = ctx.unlocked ?: throw SessionStateException("잠금 해제 상태가 아님")
        // 다시 닿지 못했거나 맞추는 사이 또 고쳤으면 지금 볼트를 대기로 두고 보낸다. 서버가 앞서 있으면 병합 절차를 탄다
        if (fresh == null || latest.vault !== opened.vault) {
            val pending = latest.pendingSync || latest.vault !== opened.vault
            open(ctx, current.email, accessToken, current.kdf, sync, latest.vault, pending)
            enqueuePush(ctx)
            return
        }
        open(ctx, current.email, accessToken, current.kdf, sync, fresh)
    }

    /**
     * 먼저 이 기기에 반영한 뒤 서버에 보낸다. 서버에 닿지 못하면 동기화 대기로 두고 성공으로 끝난다 (VLT-002)
     * @throws SessionStateException(잠김·취소), 보내기 전 검사 오류(ReadOnlyVaultException, VaultTooLargeException, InvalidVaultDataException),
     *   SessionExpiredException, 그 밖에 VaultSync.save의 분명한 거절. 분명한 거절이어도 변경 사항은 대기로 남는다
     */
    override suspend fun save(data: VaultData) {
        val opened = ctx.unlocked
        if (ctx.account == null || opened == null) throw SessionStateException("잠금 해제 상태가 아님")
        val vault = opened.vault
        val startedAt = ctx.epoch
        val payload = opened.sync.seal(data)
        ensureCurrent(ctx, startedAt)
        // 암호화하는 사이 다시 로그인했을 수 있으니 지금 계정의 토큰과 동기화 객체를 쓴다
        val latestAccount = ctx.account
        val latest = ctx.unlocked
        if (latestAccount == null || latest == null) throw SessionStateException("잠금 해제 상태가 아님")
        // 기준 revision은 마지막으로 서버와 맞춘 값 그대로다. 서버가 앞서 있으면 보낼 때 병합한다
        open(ctx, latestAccount.email, latestAccount.accessToken, latestAccount.kdf, latest.sync, SyncedVault(vault.revision, data, readOnly = false, payload = payload), pendingSync = true)
        ctx.retryBlocked = false
        enqueuePush(ctx)
    }

    /**
     * 화면의 볼트(대기 중인 변경 포함)를 지금 키로 새로 암호화해 계정의 KDF 값과 함께 백업 파일로 만든다 (VLT-004).
     * 키는 세션 밖으로 나가지 않는다. 읽기 전용 볼트도 내보낸다
     * @throws SessionStateException 잠금 해제 상태가 아니거나 암호화하는 사이 잠김
     */
    suspend fun exportBackup(now: Instant, calendar: LocalCalendar): BackupFile {
        val account = ctx.account
        val opened = ctx.unlocked
        if (account == null || opened == null) throw SessionStateException("잠금 해제 상태가 아님")
        val startedAt = ctx.epoch
        val payload = opened.sync.sealBackup(opened.vault.data)
        ensureCurrent(ctx, startedAt)
        return createBackupFile(payload, account.kdf, now, calendar)
    }

    /**
     * 백업을 파일의 KDF 값과 비밀번호로 열어 백업에 있던 항목을 백업 시점 내용으로 되돌리고 save와 같이 저장한다 (VLT-004)
     * @param now 복원 시각. 되돌린 항목의 수정 시각이 된다
     * @return 복원한 항목 수
     * @throws ReadOnlyVaultException, BackupDecryptException, NewerBackupVersionException, SessionStateException, save의 오류
     */
    suspend fun restoreBackup(backup: VaultBackup, password: String, now: Instant): Int = restoreBackupInto(this, backup, password, now)

    /**
     * 마스터 비밀번호를 바꾼다. 화면의 볼트(대기 포함)를 새 키로 다시 암호화해 서버에서 인증 정보와 함께 교체하고,
     * 새 토큰·키·KDF 값으로 이 기기의 세션과 기록을 바꾼다. 다른 기기·탭은 로그인 화면으로 간다 (SEC-004)
     */
    suspend fun changeMasterPassword(currentPassword: String, newPassword: String) = com.keymanage.domain.session.changeMasterPassword(ctx, currentPassword, newPassword)

    /**
     * 계정을 지운다. 비밀번호로 만든 인증 해시를 서버에 보내 계정·볼트·세션을 지우고, 이 기기의 기록과 탭 기억을 지워
     * 이 탭과 같은 브라우저의 다른 탭을 로그아웃한다. 자동 잠금 같은 기기 설정은 남긴다 (SEC-005)
     */
    suspend fun deleteAccount(password: String) = com.keymanage.domain.session.deleteAccount(ctx, password)

    /**
     * 서버에 올리지 못한 변경 사항이 기기에도 아직 저장되지 않았는지(쓰는 중이거나 실패). 떠남 확인용 (VLT-002).
     * 잠겼거나 세션 만료로 로그아웃된 뒤 남긴 것도 센다
     */
    fun hasUnsavedChanges(): Boolean = (ctx.account?.pendingSync == true || ctx.orphan != null) && (device.isBehind() || ctx.persisting > 0)

    /**
     * 기기 기록이 있으면 그 계정의 잠금 화면으로 시작한다 (SEC-003, ADR-0019). 앱을 열 때 한 번 부른다.
     * 기록이 없거나 규격에 맞지 않으면 로그인 전 상태로 둔다.
     * 이 탭이 기억한 같은 계정의 KDF 값과 기록이 다르면 다른 탭이 비밀번호를 바꾼 것이다. 기록은 남긴 채 로그인 전 상태로 둔다 (SEC-003 규칙 11)
     * @throws 기기 저장소 읽기 오류, MasterPasswordChangedException 이 탭이 연 뒤 다른 곳에서 비밀번호가 바뀜
     */
    suspend fun restore() {
        val record = readDevice(ctx)
        ctx.deviceRead = true
        if (record == null || ctx.account != null) return
        ctx.lastSeen = record
        val remembered = deps.tabMemory?.get()
        if (remembered != null && remembered.email == record.email && rememberedKdf(record.email, record.kdf) != remembered) {
            ctx.lastEmail = record.email
            // 안내는 한 번만 한다. 다음 새로고침부터는 기록의 KDF 값으로 잠금 화면에서 시작한다(오프라인에서도 새 비밀번호로 열림)
            deps.tabMemory?.set(rememberedKdf(record.email, record.kdf))
            throw MasterPasswordChangedException()
        }
        ctx.account = Account(record.email, null, record.kdf, record.payload, record.revision, record.pendingSync)
        ctx.lastEmail = record.email
        deps.revisionMemory.reset(record.lastSeenRevision)
    }

    /**
     * 동기화 대기 내용을 다시 보낸다. 재연결·주기 재전송이 부르며 결과는 onSyncChange로 알린다.
     * 서버에 닿지 못하면 조용히 대기를 유지하고, 되돌림이 감지되면 잠금 해제·저장 전까지 다시 보내지 않는다
     */
    suspend fun retryPendingSync() {
        val opened = ctx.unlocked
        if (ctx.retryBlocked || opened == null || !opened.pendingSync) return
        try {
            if (enqueuePush(ctx) == PushResult.SYNCED) notifySync(ctx, null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // 세션 만료는 expire가 이미 알렸다
            if (error !is SessionExpiredException) notifySync(ctx, error)
        }
    }

    /**
     * 이 기기의 세션과 기기 기록을 먼저 지우고(볼트가 열려 있었으면 잠금 알림), 서버에 토큰 폐기를 요청한다.
     * 동기화 대기 내용도 버린다. 서버 요청이 실패하면 오류를 그대로 알린다. 이 기기는 이미 로그아웃된 상태다
     * @throws DeviceStorageException 기기 기록을 지우지 못함(서버 폐기 오류보다 먼저 알린다), 그 밖에 서버 폐기 요청 오류
     */
    suspend fun logout() {
        val account = ctx.account
        val token = account?.accessToken
        resetSignIn(ctx)
        // 이 기기의 기록도 지운다 (ADR-0019). 지우지 못했으면 서버 폐기 결과보다 먼저 알린다
        val cleared = deps.tabs.lock.run(TabLockName.DEVICE) { device.clear() } == true
        // 같은 계정의 다른 탭도 로그아웃한다. 기기 기록은 이 탭이 지웠다
        if (account != null) deps.tabs.channel.post(TabMessage.LOGGED_OUT)
        var serverError: Exception? = null
        if (token != null) {
            try {
                deps.auth.logout(token)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                serverError = error
            }
        }
        if (!cleared) throw DeviceStorageException(IllegalStateException("로그아웃 때 기기 기록을 지우지 못함"))
        serverError?.let { throw it }
    }

    /**
     * 기기 기록에 서버에 올리지 못한 변경이 있는지. 기록은 모든 탭의 대기 내용을 합친 것이므로 로그아웃 확인에 쓴다 (ADR-WEB-001).
     * 다른 탭이 쓰는 중이면 끝난 뒤 읽는다. 읽지 못하면 다른 탭의 대기 내용을 알 수 없으니 있다고 보고 확인을 받는다
     */
    suspend fun pendingOnDevice(): Boolean {
        val record = try {
            deps.tabs.lock.run(TabLockName.DEVICE) { readDevice(ctx) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return true
        }
        val account = ctx.account
        return account != null && record != null && record.email == account.email && record.pendingSync
    }

    /** 다른 탭 때문에 상태가 바뀌었을 때(병합, 잠금, 로그아웃) 부를 함수를 등록한다. 화면이 상태를 다시 읽는다. @return 해제 함수 */
    fun onRemoteChange(listener: () -> Unit): () -> Unit = subscribe(ctx.remoteListeners, listener)

    /** 볼트가 닫힐 때(잠금, 잠금 해제 상태에서 로그아웃) 부를 함수를 등록한다. 클립보드 비우기 등 (COM-001) */
    fun onLock(listener: () -> Unit): () -> Unit = subscribe(ctx.lockListeners, listener)

    /** 볼트가 열릴 때(로그인, 가입, 잠금 해제) 부를 함수를 등록한다. 자동 잠금 타이머 재시작 등 */
    fun onUnlock(listener: () -> Unit): () -> Unit = subscribe(ctx.unlockListeners, listener)

    /** 대기 내용 재전송 결과나 세션 만료를 알릴 함수를 등록한다. 화면이 상태를 다시 읽고 오류를 보여 준다 */
    fun onSyncChange(listener: SyncChangeListener): () -> Unit = subscribe(ctx.syncListeners, listener)

    // 같은 함수를 두 번 등록해도 한 번만 부른다(원본 Set과 같다)
    private fun <L> subscribe(listeners: MutableSet<L>, listener: L): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }
}
