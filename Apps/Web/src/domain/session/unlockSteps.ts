// 잠금 해제·재연결이 나눠 쓰는 단계: 비밀번호 확인, 세션 없이 연 계정의 로그인, 열린 직후 서버와 맞추기 (SEC-003, ADR-0019)
import { MasterPasswordChangedError } from '../auth/authService'
import { fromBase64 } from '../crypto/base64'
import { deriveKeys } from '../crypto/keys'
import { VaultDecryptError, openVault } from '../crypto/vaultCipher'
import { mergeVaults } from '../vault/mergeVaults'
import { parseVaultData } from '../vault/vaultData'
import { RevisionRollbackError, type SyncedVault, type VaultSync } from '../vault/vaultSync'
import { expire, isUnauthorized, signOutForChangedPassword, type SessionContext } from './sessionContext'
import { SessionStateError, WrongMasterPasswordError, type Account, type RollbackOption } from './sessionTypes'

/** 비밀번호로 이 기기의 볼트를 열어 확인한다. 틀리면 인증 해시도 지운다 */
export async function verifyPassword(password: string, target: Account) {
  const keys = await deriveKeys(password, fromBase64(target.kdf.salt), target.kdf.iterations)
  try {
    return { keys, data: parseVaultData(await openVault(keys.encryptionKey, target.payload)) }
  } catch (error) {
    keys.authHash.fill(0)
    if (error instanceof VaultDecryptError) throw new WrongMasterPasswordError()
    throw error
  }
}

/**
 * 비밀번호로 이 기기의 볼트를 연다. 열지 못하면 서버의 KDF 값만 확인해, 다른 곳에서 비밀번호가 바뀌었으면
 * 로그아웃 상태로 바꾸고 MasterPasswordChangedError를 던진다. 바뀐 비밀번호는 기기의 옛 KDF 값으로 열 수 없기 때문이다 (SEC-003 규칙 11).
 * KDF 값이 같거나 서버에 닿지 못하면 WrongMasterPasswordError 그대로다. 그사이 다른 로그인·로그아웃이 있었으면 상태를 건드리지 않는다
 */
export async function verifyOrDetectChange(ctx: SessionContext, password: string, target: Account) {
  const startedGeneration = ctx.generation
  try {
    return await verifyPassword(password, target)
  } catch (error) {
    if (!(error instanceof WrongMasterPasswordError)) throw error
    let changed = false
    try {
      changed = await ctx.deps.auth.kdfChanged(target.email, target.kdf)
    } catch {
      // 오프라인·서버 오류면 바뀌었는지 알 수 없다. 비밀번호 틀림으로 둔다
    }
    if (!changed || ctx.generation !== startedGeneration) throw error
    // 다른 탭 감지와 같이 정리한다. 알림 대신 오류로 부른 쪽(잠금 해제·재연결)에 알린다
    signOutForChangedPassword(ctx, target)
    throw new MasterPasswordChangedError()
  }
}

/**
 * 세션 토큰 없이 연 계정을 이미 만든 인증 해시로 로그인한다. 인증 해시는 이 요청 안에서 지운다 (ADR-0019)
 * @returns 세션 토큰. 로그인하지 못했으면 null과 그 사유(서버에 닿지 못함, 서버 거절 등)
 * @throws MasterPasswordChangedError(로그아웃 상태로 바꾼다)
 */
export async function resumeSession(ctx: SessionContext, target: Account, authHash: Uint8Array): Promise<{ accessToken: string | null; failure?: unknown }> {
  const startedGeneration = ctx.generation
  try {
    return { accessToken: (await ctx.deps.auth.resume(target.email, target.kdf, authHash)).accessToken }
  } catch (error) {
    if (error instanceof MasterPasswordChangedError) {
      // 잠금 해제 때 서버 감지와 같이 정리한다. 기기 기록은 새 비밀번호로 로그인에 성공할 때 버린다 (ADR-0020)
      if (ctx.generation === startedGeneration) signOutForChangedPassword(ctx, target)
      throw error
    }
    // 그 밖의 실패여도 비밀번호는 이 기기에서 확인했으니 볼트는 연다. 사유는 부른 쪽이 알린다
    return { accessToken: null, failure: error }
  } finally {
    authHash.fill(0)
  }
}

/**
 * 잠금 해제 직후 서버와 맞춘다. 대기 내용이 있으면 서버 볼트로 덮지 않고 병합해 올린다
 * @returns 서버와 맞춘 볼트. 서버에 닿지 못하거나 서버 오류·응답 형식 오류면 null
 * @throws RevisionRollbackError, SessionExpiredError, SessionStateError(그사이 로그아웃·다른 로그인)
 */
export async function syncAfterUnlock(ctx: SessionContext, sync: VaultSync, cached: SyncedVault, pendingSync: boolean, options: RollbackOption): Promise<SyncedVault | null> {
  const startedGeneration = ctx.generation
  try {
    // 대기 내용이 있어도 먼저 받아 되돌림을 검사한다. 기준과 같은 revision으로 되돌아간 서버도 잡는다
    const fresh = await sync.load(options)
    // 대기 내용이 있거나, 되돌림을 받아들였는데 서버 볼트가 없으면(유실) 이 기기의 볼트를 병합해 올린다
    if (pendingSync || fresh.payload === null) return await sync.save(fresh, mergeVaults(cached.data, fresh.data))
    return fresh
  } catch (error) {
    if (error instanceof RevisionRollbackError) throw error
    if (isUnauthorized(error)) {
      // 그사이 로그아웃·다른 로그인이 있었으면 지금 세션을 끝내지 않는다
      if (ctx.generation !== startedGeneration) throw new SessionStateError('기다리는 사이 로그아웃되어 작업을 취소함')
      throw expire(ctx)
    }
    return null
  }
}
