// 볼트 불러오기·저장과 충돌 시 자동 병합 (VLT-001, ADR-0006)
// 평문 볼트와 암호화 키는 이 모듈 안에서만 쓰고, 서버 포트에는 암호화된 페이로드만 넘긴다
import { openVault, sealVault, type EncryptedVaultPayload } from '../crypto/vaultCipher'
import { ServerError } from '../server/serverErrors'
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
}

export interface VaultSyncDeps {
  readonly api: VaultApi
  readonly encryptionKey: CryptoKey
  readonly now: () => Date
}

/** @throws VaultTooLargeError */
export function assertWithinLimit(payload: EncryptedVaultPayload): void {
  const size = new TextEncoder().encode(JSON.stringify(payload)).length
  if (size > MAX_PAYLOAD_BYTES) throw new VaultTooLargeError(size)
}

export function createVaultSync({ api, encryptionKey, now }: VaultSyncDeps) {
  // 이 세션에서 마지막으로 본 서버 revision. 되돌림 감지에 쓴다 (기기 저장은 오프라인 저장소 단계에서 붙인다)
  let lastSeenRevision = 0

  async function fetchRemote(acceptRollback: boolean): Promise<SyncedVault> {
    const remote = await api.get()
    const revision = remote?.revision ?? 0
    if (revision < lastSeenRevision && !acceptRollback) throw new RevisionRollbackError(lastSeenRevision, revision)
    const data = remote === null ? emptyVault(now()) : parseVaultData(await openVault(encryptionKey, remote.payload))
    lastSeenRevision = revision
    return { revision, data, readOnly: isReadOnly(data) }
  }

  return {
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
     * @throws ReadOnlyVaultError, VaultTooLargeError, VaultSyncConflictError, RevisionRollbackError, ServerError, NetworkError
     */
    async save(base: SyncedVault, data: VaultData): Promise<SyncedVault> {
      let expectedRevision = base.revision
      let toSave = data
      for (let attempt = 0; ; attempt++) {
        if (isReadOnly(toSave)) throw new ReadOnlyVaultError()
        const payload = await sealVault(encryptionKey, JSON.stringify(toSave))
        assertWithinLimit(payload)
        try {
          const revision = await api.save(expectedRevision, payload)
          lastSeenRevision = revision
          return { revision, data: toSave, readOnly: false }
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
