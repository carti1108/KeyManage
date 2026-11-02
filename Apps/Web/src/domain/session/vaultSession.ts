// 로그인·가입·잠금·잠금 해제·로그아웃 상태 관리 (SEC-002, SEC-003)
// 잠기면 암호화 키와 평문 볼트를 버리고, 이메일·KDF 값·토큰·암호화 볼트만 남긴다
// 서버에 닿지 못한 저장은 동기화 대기로 두고 나중에 다시 보낸다 (VLT-002)
// 상태는 sessionContext, 기기 기록은 deviceSync, 대기 재전송은 pendingPush, 로그인은 signIn, 잠금 해제 단계는 unlockSteps가 맡는다
import { MasterPasswordChangedError, normalizeEmail } from '../auth/authService'
import { sameKdf } from '../device/reconcileDeviceRecord'
import { DeviceStorageError } from '../device/deviceRecord'
import { NetworkError } from '../server/serverErrors'
import { isReadOnly, type VaultData } from '../vault/vaultData'
import { isTransientSyncError, type SyncedVault } from '../vault/vaultSync'
import { createBackupFile, type BackupFile, type VaultBackup } from '../backup/backupFile'
import { restoreBackupInto } from '../backup/restoreBackup'
import { NewMasterPasswordTooShortError, PasswordChangeOfflineError, SameMasterPasswordError, changeMasterPassword } from './changePassword'
import { onTabMessage, open, refreshLockedFromDevice } from './deviceSync'
import { enqueuePush } from './pendingPush'
import { NO_TOKEN, createSessionContext, ensureCurrent, ensureCurrentOrRevoke, lockThisTab, notifySync, readDevice, resetSignIn, revoke, syncFor } from './sessionContext'
import { beginSignIn, signIn } from './signIn'
import { resumeSession, syncAfterUnlock, verifyOrDetectChange } from './unlockSteps'

import {
  SessionExpiredError,
  SessionStateError,
  VaultSetupError,
  WrongMasterPasswordError,
  type LockScope,
  type RollbackOption,
  type SessionState,
  type SyncChangeListener,
  type VaultSessionDeps,
} from './sessionTypes'

export { NewMasterPasswordTooShortError, PasswordChangeOfflineError, SameMasterPasswordError, SessionExpiredError, SessionStateError, VaultSetupError, WrongMasterPasswordError }
export type { LockScope, SessionState, SyncChangeListener, VaultSessionDeps }

export function createVaultSession(deps: VaultSessionDeps) {
  const { auth, revisionMemory, tabs } = deps
  const ctx = createSessionContext(deps)
  const { device } = ctx

  tabs.channel.subscribe((kind) => onTabMessage(ctx, kind))

  const session = {
    state(): SessionState {
      if (ctx.account === null) return { status: 'signedOut' }
      if (ctx.unlocked === null) return { status: 'locked', email: ctx.account.email, pendingSync: ctx.account.pendingSync }
      return { status: 'unlocked', email: ctx.account.email, vault: ctx.unlocked.vault, pendingSync: ctx.unlocked.pendingSync, signedIn: ctx.account.accessToken !== null, storedOnDevice: !device.hasFailed() }
    },

    /** @throws VaultSetupError 가입은 됐지만 빈 볼트를 저장하지 못했을 때 */
    async register(email: string, password: string): Promise<void> {
      const normalized = normalizeEmail(email)
      const startedAt = beginSignIn(ctx, normalized)
      const result = await auth.register(email, password)
      try {
        await signIn(ctx, normalized, startedAt, result, {})
      } catch (error) {
        if (error instanceof SessionStateError) throw error
        throw new VaultSetupError(error)
      }
    },

    /**
     * 세션 만료 전에 남긴 동기화 대기 내용이 같은 계정이면 병합해 올린다
     * @throws RevisionRollbackError 서버 볼트가 되돌아감. 사용자가 확인하면 acceptRollback으로 다시 부른다
     */
    async login(email: string, password: string, options: RollbackOption = {}): Promise<void> {
      const normalized = normalizeEmail(email)
      const startedAt = beginSignIn(ctx, normalized)
      await signIn(ctx, normalized, startedAt, await auth.login(email, password), options)
    },

    /**
     * 키와 평문 볼트를 버린다. 진행 중인 잠금 해제·저장도 취소한다. 동기화 대기 내용은 암호화된 채 남는다.
     * allTabs(잠금 버튼, 보이는 탭의 자동 잠금 시간 초과)면 같은 계정의 다른 탭도 잠근다. 새로고침·떠남·동결, "즉시" 설정의 숨김은 thisTab (SEC-003)
     */
    lock(scope: LockScope = 'allTabs'): void {
      if (lockThisTab(ctx) && scope === 'allTabs') tabs.channel.post('locked')
    },

    /**
     * 저장해 둔 암호화 볼트를 복호화해 비밀번호를 확인하므로 오프라인에서도 동작한다.
     * 확인 뒤 바로 서버와 맞추며(대기 내용이 있으면 병합해 올림), 서버와 맞추지 못하면 저장해 둔 볼트로 연다
     * @returns offline: 서버와 맞추지 못했는지
     * @throws WrongMasterPasswordError, MasterPasswordChangedError(로그아웃됨), SessionExpiredError(로그아웃됨), RevisionRollbackError(잠긴 상태 유지), SessionStateError
     */
    async unlock(password: string, options: RollbackOption = {}): Promise<{ offline: boolean }> {
      if (ctx.account === null) throw new SessionStateError('로그인 전에는 잠금을 해제할 수 없음')
      if (ctx.unlocked !== null) return { offline: false }
      const startedAt = ctx.epoch
      // 동결 등으로 다른 탭의 알림을 놓쳤을 수 있으니 기기 기록을 다시 읽는다. 기록의 볼트는 연 뒤 쓸 때 병합한다 (ADR-WEB-001)
      // 다른 탭이 비밀번호를 바꿔 로그아웃 상태로 바꿨으면 취소가 아니라 비밀번호 바뀜으로 알린다 (SEC-003 규칙 11)
      if ((await refreshLockedFromDevice(ctx, false)) === 'passwordChanged') throw new MasterPasswordChangedError()
      ensureCurrent(ctx, startedAt)
      if (ctx.account === null) throw new SessionStateError('다른 탭에서 로그아웃됨')
      if (ctx.unlocked !== null) return { offline: false }
      const locked = ctx.account

      const { keys, data } = await verifyOrDetectChange(ctx, password, locked)
      // 토큰이 있으면 인증 해시가 필요 없어 바로 지운다. 없으면(기기 기록으로 다시 열었으면) 바로 로그인하고 지운다 (SEC-003, ADR-0019)
      let accessToken = locked.accessToken
      if (accessToken === null) {
        const resumed = await resumeSession(ctx, locked, keys.authHash)
        accessToken = resumed.accessToken
        // 오프라인은 조용히 세션 없이 연다. 서버가 거절했으면(출처 거부 등) 사유를 알린다
        if (resumed.failure !== undefined && !isTransientSyncError(resumed.failure) && ctx.epoch === startedAt) notifySync(ctx, resumed.failure)
      }
      keys.authHash.fill(0)
      ensureCurrentOrRevoke(ctx, startedAt, locked.accessToken === null ? accessToken : null)
      // 받은 토큰은 되돌림 확인으로 잠긴 채 끝나도 남겨, 다시 풀 때 또 로그인하지 않게 한다
      if (locked.accessToken === null && accessToken !== null) ctx.account = { ...locked, accessToken }

      const sync = syncFor(ctx, accessToken ?? NO_TOKEN, keys.encryptionKey)
      const cached: SyncedVault = { revision: locked.revision, data, readOnly: isReadOnly(data), payload: locked.payload }
      ctx.retryBlocked = false
      const fresh = accessToken === null ? null : await syncAfterUnlock(ctx, sync, cached, locked.pendingSync, options)
      ensureCurrent(ctx, startedAt)
      await open(ctx, locked.email, accessToken, locked.kdf, sync, fresh ?? cached, fresh === null && locked.pendingSync)
      return { offline: fresh === null }
    },

    /**
     * 세션 없이 연 볼트에서 비밀번호를 다시 입력받아 로그인하고 서버와 맞춘다 (SEC-003, ADR-0019)
     * @throws WrongMasterPasswordError, 로그인하지 못한 실제 사유(NetworkError, ServerError 등), MasterPasswordChangedError(로그아웃됨),
     *   RevisionRollbackError, SessionExpiredError, SessionStateError
     */
    async reconnect(password: string, options: RollbackOption = {}): Promise<void> {
      if (ctx.account === null || ctx.unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
      if (ctx.account.accessToken !== null) return
      const current = ctx.account
      const startedAt = ctx.epoch
      const { keys } = await verifyOrDetectChange(ctx, password, current)
      const { accessToken, failure } = await resumeSession(ctx, current, keys.authHash)
      // 왜 동기화하지 못하는지 알 수 있게 실제 사유(연결 실패, 서버 오류, 거절)를 그대로 낸다
      if (accessToken === null) throw failure ?? new NetworkError()
      ensureCurrentOrRevoke(ctx, startedAt, accessToken)
      const opened = ctx.unlocked
      if (opened === null) throw new SessionStateError('잠금 해제 상태가 아님')
      const sync = syncFor(ctx, accessToken, keys.encryptionKey)
      ctx.retryBlocked = false
      let fresh: SyncedVault | null
      try {
        // 서버와 맞추기 전에는 토큰을 세션에 넣지 않는다. 되돌림이면 세션 없이 두고 확인을 받는다
        fresh = await syncAfterUnlock(ctx, sync, opened.vault, opened.pendingSync, options)
      } catch (error) {
        revoke(ctx, accessToken)
        throw error
      }
      ensureCurrentOrRevoke(ctx, startedAt, accessToken)
      const latest = ctx.unlocked
      if (latest === null) throw new SessionStateError('잠금 해제 상태가 아님')
      // 다시 닿지 못했거나 맞추는 사이 또 고쳤으면 지금 볼트를 대기로 두고 보낸다. 서버가 앞서 있으면 병합 절차를 탄다
      if (fresh === null || latest.vault !== opened.vault) {
        const pending = latest.pendingSync || latest.vault !== opened.vault
        await open(ctx, current.email, accessToken, current.kdf, sync, latest.vault, pending)
        await enqueuePush(ctx)
        return
      }
      await open(ctx, current.email, accessToken, current.kdf, sync, fresh)
    },

    /**
     * 먼저 이 기기에 반영한 뒤 서버에 보낸다. 서버에 닿지 못하면 동기화 대기로 두고 성공으로 끝난다 (VLT-002)
     * @throws SessionStateError(잠김·취소), 보내기 전 검사 오류(ReadOnlyVaultError, VaultTooLargeError, InvalidVaultDataError),
     *   SessionExpiredError, 그 밖에 VaultSync.save의 분명한 거절. 분명한 거절이어도 변경 사항은 대기로 남는다
     */
    async save(data: VaultData): Promise<void> {
      if (ctx.account === null || ctx.unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
      const { vault } = ctx.unlocked
      const startedAt = ctx.epoch
      const payload = await ctx.unlocked.sync.seal(data)
      ensureCurrent(ctx, startedAt)
      // 암호화하는 사이 다시 로그인했을 수 있으니 지금 계정의 토큰과 동기화 객체를 쓴다
      const latestAccount = ctx.account
      const latest = ctx.unlocked
      if (latestAccount === null || latest === null) throw new SessionStateError('잠금 해제 상태가 아님')
      const { email, accessToken, kdf } = latestAccount
      // 기준 revision은 마지막으로 서버와 맞춘 값 그대로다. 서버가 앞서 있으면 보낼 때 병합한다
      await open(ctx, email, accessToken, kdf, latest.sync, { revision: vault.revision, data, readOnly: false, payload }, true)
      ctx.retryBlocked = false
      await enqueuePush(ctx)
    },

    /**
     * 화면의 볼트(대기 중인 변경 포함)를 지금 키로 새로 암호화해 계정의 KDF 값과 함께 백업 파일로 만든다 (VLT-004).
     * 키는 세션 밖으로 나가지 않는다. 읽기 전용 볼트도 내보낸다
     * @throws SessionStateError 잠금 해제 상태가 아니거나 암호화하는 사이 잠김
     */
    async exportBackup(now: Date): Promise<BackupFile> {
      if (ctx.account === null || ctx.unlocked === null) throw new SessionStateError('잠금 해제 상태가 아님')
      const { kdf } = ctx.account
      const startedAt = ctx.epoch
      const payload = await ctx.unlocked.sync.sealBackup(ctx.unlocked.vault.data)
      ensureCurrent(ctx, startedAt)
      return createBackupFile(payload, kdf, now)
    },

    /**
     * 백업을 파일의 KDF 값과 비밀번호로 열어 백업에 있던 항목을 백업 시점 내용으로 되돌리고 save와 같이 저장한다 (VLT-004)
     * @param now 복원 시각. 되돌린 항목의 수정 시각이 된다
     * @returns 복원한 항목 수
     * @throws ReadOnlyVaultError, BackupDecryptError, NewerBackupVersionError, SessionStateError, save의 오류
     */
    restoreBackup(backup: VaultBackup, password: string, now: Date): Promise<number> {
      return restoreBackupInto(session, backup, password, now)
    },

    /**
     * 마스터 비밀번호를 바꾼다. 화면의 볼트(대기 포함)를 새 키로 다시 암호화해 서버에서 인증 정보와 함께 교체하고,
     * 새 토큰·키·KDF 값으로 이 기기의 세션과 기록을 바꾼다. 다른 기기·탭은 로그인 화면으로 간다 (SEC-004)
     * @throws SessionStateError, ReadOnlyVaultError, PasswordChangeOfflineError, NewMasterPasswordTooShortError, SameMasterPasswordError,
     *   WrongMasterPasswordError, VaultSyncConflictError, SessionExpiredError, 그 밖의 ServerError·NetworkError
     */
    changeMasterPassword(currentPassword: string, newPassword: string): Promise<void> {
      return changeMasterPassword(ctx, currentPassword, newPassword)
    },

    /**
     * 서버에 올리지 못한 변경 사항이 기기에도 아직 저장되지 않았는지(쓰는 중이거나 실패). 떠남 확인용 (VLT-002).
     * 잠겼거나 세션 만료로 로그아웃된 뒤 남긴 것도 센다
     */
    hasUnsavedChanges(): boolean {
      return (ctx.account?.pendingSync === true || ctx.orphan !== null) && (device.isBehind() || ctx.persisting > 0)
    },

    /**
     * 기기 기록이 있으면 그 계정의 잠금 화면으로 시작한다 (SEC-003, ADR-0019). 앱을 열 때 한 번 부른다.
     * 기록이 없거나 규격에 맞지 않으면 로그인 전 상태로 둔다
     * 이 탭이 기억한 같은 계정의 KDF 값과 기록이 다르면 다른 탭이 비밀번호를 바꾼 것이다. 기록은 남긴 채 로그인 전 상태로 둔다 (SEC-003 규칙 11)
     * @throws DeviceStorageError 기기 저장소를 읽지 못함, MasterPasswordChangedError 이 탭이 연 뒤 다른 곳에서 비밀번호가 바뀜
     */
    async restore(): Promise<void> {
      const record = await readDevice(ctx)
      ctx.deviceRead = true
      if (record === null || ctx.account !== null) return
      ctx.lastSeen = record
      const remembered = deps.tabMemory?.get() ?? null
      if (remembered !== null && remembered.email === record.email && !sameKdf(remembered, record.kdf)) {
        ctx.lastEmail = record.email
        throw new MasterPasswordChangedError()
      }
      const { email, kdf, payload, revision, pendingSync } = record
      ctx.account = { email, accessToken: null, kdf, payload, revision, pendingSync }
      ctx.lastEmail = email
      revisionMemory.reset(record.lastSeenRevision)
    },

    /**
     * 동기화 대기 내용을 다시 보낸다. 재연결·주기 재전송이 부르며 결과는 onSyncChange로 알린다.
     * 서버에 닿지 못하면 조용히 대기를 유지하고, 되돌림이 감지되면 잠금 해제·저장 전까지 다시 보내지 않는다
     */
    async retryPendingSync(): Promise<void> {
      if (ctx.retryBlocked || ctx.unlocked === null || !ctx.unlocked.pendingSync) return
      try {
        if ((await enqueuePush(ctx)) === 'synced') notifySync(ctx, null)
      } catch (error) {
        // 세션 만료는 expire가 이미 알렸다
        if (!(error instanceof SessionExpiredError)) notifySync(ctx, error)
      }
    },

    /**
     * 이 기기의 세션과 기기 기록을 먼저 지우고(볼트가 열려 있었으면 잠금 알림), 서버에 토큰 폐기를 요청한다.
     * 동기화 대기 내용도 버린다. 서버 요청이 실패하면 오류를 그대로 알린다. 이 기기는 이미 로그아웃된 상태다
     * @throws DeviceStorageError 기기 기록을 지우지 못함(서버 폐기 오류보다 먼저 알린다), 그 밖에 서버 폐기 요청 오류
     */
    async logout(): Promise<void> {
      const token = ctx.account?.accessToken
      resetSignIn(ctx)
      // 이 기기의 기록도 지운다 (ADR-0019). 지우지 못했으면 서버 폐기 결과보다 먼저 알린다
      const cleared = (await tabs.lock.run('device', () => device.clear())) === true
      // 같은 계정의 다른 탭도 로그아웃한다. 기기 기록은 이 탭이 지웠다
      if (token !== undefined) tabs.channel.post('logged-out')
      let serverError: unknown = null
      if (token !== undefined && token !== null) {
        try {
          await auth.logout(token)
        } catch (error) {
          serverError = error
        }
      }
      if (!cleared) throw new DeviceStorageError(new Error('로그아웃 때 기기 기록을 지우지 못함'))
      if (serverError !== null) throw serverError
    },

    /**
     * 기기 기록에 서버에 올리지 못한 변경이 있는지. 기록은 모든 탭의 대기 내용을 합친 것이므로 로그아웃 확인에 쓴다 (ADR-WEB-001).
     * 다른 탭이 쓰는 중이면 끝난 뒤 읽는다. 읽지 못하면 다른 탭의 대기 내용을 알 수 없으니 있다고 보고 확인을 받는다
     */
    async pendingOnDevice(): Promise<boolean> {
      const record = await tabs.lock.run('device', () => readDevice(ctx)).catch(() => undefined)
      if (record === undefined) return true
      return ctx.account !== null && record !== null && record.email === ctx.account.email && record.pendingSync
    },

    /** 다른 탭 때문에 상태가 바뀌었을 때(병합, 잠금, 로그아웃) 부를 함수를 등록한다. 화면이 상태를 다시 읽는다 */
    onRemoteChange(listener: () => void): () => void {
      ctx.remoteListeners.add(listener)
      return () => ctx.remoteListeners.delete(listener)
    },

    /** 볼트가 닫힐 때(잠금, 잠금 해제 상태에서 로그아웃) 부를 함수를 등록한다. 클립보드 비우기 등 (COM-001) */
    onLock(listener: () => void): () => void {
      ctx.lockListeners.add(listener)
      return () => ctx.lockListeners.delete(listener)
    },

    /** 볼트가 열릴 때(로그인, 가입, 잠금 해제) 부를 함수를 등록한다. 자동 잠금 타이머 재시작 등 */
    onUnlock(listener: () => void): () => void {
      ctx.unlockListeners.add(listener)
      return () => ctx.unlockListeners.delete(listener)
    },

    /** 대기 내용 재전송 결과나 세션 만료를 알릴 함수를 등록한다. 화면이 상태를 다시 읽고 오류를 보여 준다 */
    onSyncChange(listener: SyncChangeListener): () => void {
      ctx.syncListeners.add(listener)
      return () => ctx.syncListeners.delete(listener)
    },
  }
  return session
}

export type VaultSession = ReturnType<typeof createVaultSession>
