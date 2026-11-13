// 볼트 불러오기·저장과 충돌 시 자동 병합(VLT-001, ADR-0006)은 공유 Kotlin(Apps/Shared/domain의 VaultSync)이 한다.
// 웹에는 세션 상태 타입과 화면이 오류 종류를 구분하는 데 쓰는 오류 정의만 남긴다
import type { EncryptedVaultPayload } from '../crypto/vaultCipher'
import type { VaultData } from './vaultData'

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
  /** 서버에 있는 암호화 볼트. 잠금 해제 확인에 쓴다 (SEC-003). 서버에 볼트가 없으면 null */
  readonly payload: EncryptedVaultPayload | null
}
