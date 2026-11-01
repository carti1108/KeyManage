// 볼트 불러오기·저장과 충돌 시 자동 병합 (VLT-001, ADR-0006)
// 평문 볼트와 암호화 키는 이 모듈 안에서만 쓰고, 서버 포트에는 암호화된 페이로드만 넘긴다
import { openVault, sealVault, type EncryptedVaultPayload } from '../crypto/vaultCipher'
import { NetworkError, ServerError, UnexpectedResponseError } from '../server/serverErrors'
import { mergeVaults } from './mergeVaults'
import { emptyVault, isReadOnly, parseVaultData, type VaultData } from './vaultData'
import type { VaultApi } from './vaultApi'

/** 볼트 최대 허용 용량. 페이로드 JSON의 UTF-8 바이트 기준이며 같으면 허용한다 (VLT-001) */
export const MAX_PAYLOAD_BYTES = 5_242_880
/** 첫 저장이 충돌한 뒤 병합해 다시 보내는 횟수 (VLT-001) */
const MAX_MERGE_RETRIES = 3

export class VaultSyncConflictError extends Error {
  constructor() {
    super('자동 병합 재시도를 모두 실패함')
    this.name = 'VaultSyncConflictError'
  }
}

export class ReadOnlyVaultError extends Error {
  constructor() {
    super('새 버전 앱에서 저장한 볼트라 저장하지 않음')
    this.name = 'ReadOnlyVaultError'
  }
}

export class VaultTooLargeError extends Error {
  readonly sizeInBytes: number

  constructor(sizeInBytes: number) {
    super(`볼트가 허용 용량을 넘음: ${sizeInBytes}바이트`)
    this.name = 'VaultTooLargeError'
    this.sizeInBytes = sizeInBytes
  }
}

/** 저장 응답의 revision이 기대값(+1)과 다름. 서버가 규약을 어겼으므로 잠시 실패로 숨기지 않는다 */
export class RevisionMismatchError extends UnexpectedResponseError {
  constructor(expected: number, actual: number) {
    super(`저장 응답 revision이 ${expected}이 아님: ${actual}`)
    this.name = 'RevisionMismatchError'
  }
}

/** 서버 revision이 이 기기가 마지막으로 본 값보다 낮음. 사용자가 확인해야 받아들인다 (VLT-001) */
export class RevisionRollbackError extends Error {
  readonly lastSeenRevision: number
  readonly serverRevision: number

  constructor(lastSeenRevision: number, serverRevision: number) {
    super(`서버 볼트가 되돌아감: ${lastSeenRevision} → ${serverRevision}`)
    this.name = 'RevisionRollbackError'
    this.lastSeenRevision = lastSeenRevision
    this.serverRevision = serverRevision
  }
}

export interface SyncedVault {
  readonly revision: number
  readonly data: VaultData
  readonly readOnly: boolean
  /** 서버에 있는 암호화 볼트. 잠금 해제 확인에 쓴다 (SEC-003). 서버에 볼트가 없으면 null */
  readonly payload: EncryptedVaultPayload | null
}

/** 이 기기가 마지막으로 본 서버 revision. 잠금 해제·재로그인으로 동기화를 새로 만들어도 이어서 쓴다 */
export interface RevisionMemory {
  get(): number
  /** 더 큰 값일 때만 바꾼다. 응답 순서가 뒤바뀌어도 기준이 낮아지지 않는다 */
  raise(revision: number): void
  /** 사용자가 되돌림을 받아들였을 때만 쓴다 */
  reset(revision: number): void
}

/** 앱이 켜져 있는 동안만 기억한다. 재시작 뒤에도 기억하는 저장은 오프라인 저장소 단계에서 붙인다 */
export function createRevisionMemory(): RevisionMemory {
  let lastSeen = 0
  return {
    get: () => lastSeen,
    raise: (revision) => {
      lastSeen = Math.max(lastSeen, revision)
    },
    reset: (revision) => {
      lastSeen = revision
    },
  }
}

/**
 * 서버에 닿지 못했거나 서버가 잠시 처리하지 못한 실패. 변경 사항을 동기화 대기로 두고 나중에 다시 보낸다 (VLT-002).
 * 요청 시간 초과도 NetworkError로 온다. 응답 형식 오류는 공용 와이파이 로그인 페이지처럼 중간에서 가로챈 응답일 수 있다
 */
export function isTransientSyncError(error: unknown): boolean {
  if (error instanceof RevisionMismatchError) return false
  if (error instanceof NetworkError || error instanceof UnexpectedResponseError) return true
  return error instanceof ServerError && (error.status >= 500 || error.status === 429)
}

export interface VaultSyncDeps {
  readonly api: VaultApi
  readonly encryptionKey: CryptoKey
  readonly now: () => Date
  readonly revisionMemory: RevisionMemory
}

/** @throws VaultTooLargeError */
export function assertWithinLimit(payload: EncryptedVaultPayload): void {
  const size = new TextEncoder().encode(JSON.stringify(payload)).length
  if (size > MAX_PAYLOAD_BYTES) throw new VaultTooLargeError(size)
}

export function createVaultSync({ api, encryptionKey, now, revisionMemory }: VaultSyncDeps) {
  async function fetchRemote(acceptRollback: boolean): Promise<SyncedVault> {
    // 요청을 보내는 시점의 값과 비교한다. 응답을 기다리는 사이 저장이 끝나 기준이 올라가도 되돌림으로 오해하지 않는다
    const seenBeforeRequest = revisionMemory.get()
    const remote = await api.get()
    const revision = remote?.revision ?? 0
    if (revision < seenBeforeRequest && !acceptRollback) throw new RevisionRollbackError(seenBeforeRequest, revision)
    const data = remote === null ? emptyVault(now()) : parseVaultData(await openVault(encryptionKey, remote.payload))
    if (acceptRollback) revisionMemory.reset(revision)
    else revisionMemory.raise(revision)
    return { revision, data, readOnly: isReadOnly(data), payload: remote?.payload ?? null }
  }

  /** @throws ReadOnlyVaultError, InvalidVaultDataError, VaultTooLargeError */
  async function seal(data: VaultData): Promise<EncryptedVaultPayload> {
    if (isReadOnly(data)) throw new ReadOnlyVaultError()
    const json = JSON.stringify(data)
    // 규격에 맞지 않는 볼트를 올리면 모든 기기가 열지 못하게 되므로 보내기 전에 다시 확인한다
    parseVaultData(json)
    const payload = await sealVault(encryptionKey, json)
    assertWithinLimit(payload)
    return payload
  }

  return {
    /**
     * 서버에 보내지 않고 저장 전 검사와 암호화만 한다. 동기화 대기 내용을 기기에 남길 때 쓴다
     * @throws ReadOnlyVaultError, InvalidVaultDataError, VaultTooLargeError
     */
    seal,

    /**
     * @param options.acceptRollback 되돌림 경고를 사용자가 확인했을 때만 true
     * @throws RevisionRollbackError, InvalidVaultDataError, VaultDecryptError, ServerError, NetworkError
     */
    load(options: { acceptRollback?: boolean } = {}): Promise<SyncedVault> {
      return fetchRemote(options.acceptRollback ?? false)
    },

    /**
     * base는 data를 만들 때 바탕으로 삼은 불러오기·저장 결과다. 충돌하면 서버 볼트와 병합해 다시 저장한다
     * @returns 실제로 저장된(병합됐을 수 있는) 볼트
     * @throws ReadOnlyVaultError, InvalidVaultDataError, VaultTooLargeError, VaultSyncConflictError, RevisionRollbackError,
     *   ServerError, NetworkError, UnexpectedResponseError. 충돌 뒤 서버 볼트를 받을 때는 VaultDecryptError, UnsupportedPayloadError도 낸다
     */
    async save(base: SyncedVault, data: VaultData): Promise<SyncedVault> {
      let expectedRevision = base.revision
      let toSave = data
      for (let attempt = 0; ; attempt++) {
        const payload = await seal(toSave)
        try {
          const revision = await api.save(expectedRevision, payload)
          // 서버는 저장할 때마다 1씩 올린다. 다른 값이면 되돌림 감지 기준을 흔들 수 있으므로 믿지 않는다
          if (revision !== expectedRevision + 1) throw new RevisionMismatchError(expectedRevision + 1, revision)
          revisionMemory.raise(revision)
          return { revision, data: toSave, readOnly: false, payload }
        } catch (error) {
          if (!(error instanceof ServerError && error.code === 'VAULT_CONFLICT')) throw error
          if (attempt === MAX_MERGE_RETRIES) throw new VaultSyncConflictError()
        }
        const remote = await fetchRemote(false)
        toSave = mergeVaults(toSave, remote.data)
        expectedRevision = remote.revision
      }
    },
  }
}

export type VaultSync = ReturnType<typeof createVaultSync>
