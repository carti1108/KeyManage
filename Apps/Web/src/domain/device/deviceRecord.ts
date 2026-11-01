// 웹 기기에 저장하는 한 계정의 기록 (ADR-0019)
// 비밀이 아니거나(이메일, KDF 값, revision) 암호화된 값(볼트)만 담는다. 토큰·키·인증 해시·평문은 담지 않는다
import type { UnlockKdf } from '../auth/authService'
import type { EncryptedVaultPayload } from '../crypto/vaultCipher'

/** 기록 형식 버전. 형식을 바꾸면 올리고 이전 기록을 옮기는 절차를 둔다 */
export const DEVICE_RECORD_VERSION = 1

export interface DeviceRecord {
  readonly version: typeof DEVICE_RECORD_VERSION
  readonly email: string
  readonly kdf: UnlockKdf
  /** 마지막 볼트. 동기화 대기 중이면 대기 내용을 담은 볼트다 */
  readonly payload: EncryptedVaultPayload
  /** payload가 바탕으로 삼은 서버 revision */
  readonly revision: number
  /** 되돌림 감지 기준 (VLT-001) */
  readonly lastSeenRevision: number
  readonly pendingSync: boolean
}

/** 기기 저장소 포트. 읽은 값은 믿지 않고 parseDeviceRecord로 확인한다 */
export interface DeviceStore {
  /** @throws DeviceStorageError */
  load(): Promise<unknown>
  /** @throws DeviceStorageError */
  save(record: DeviceRecord): Promise<void>
  /** @throws DeviceStorageError */
  clear(): Promise<void>
}

/** 기기 저장소에 읽거나 쓰지 못함 (저장 공간 부족, 사생활 보호 모드 등) */
export class DeviceStorageError extends Error {
  constructor(cause: unknown) {
    super('기기 저장소를 쓰지 못함', { cause })
    this.name = 'DeviceStorageError'
  }
}

type Fields = Record<string, unknown>

function isFields(value: unknown): value is Fields {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isRevision(value: unknown): value is number {
  return Number.isSafeInteger(value) && (value as number) >= 0
}

function isKdf(value: unknown): value is UnlockKdf {
  return isFields(value) && typeof value.salt === 'string' && Number.isSafeInteger(value.iterations) && (value.iterations as number) > 0
}

// 헤더 값의 규격은 복호화할 때 openVault가 확인한다. 여기서는 모양만 본다
function isPayload(value: unknown): value is EncryptedVaultPayload {
  if (!isFields(value)) return false
  return (
    typeof value.version === 'number' &&
    typeof value.cipher === 'string' &&
    typeof value.iv === 'string' &&
    typeof value.ciphertext === 'string' &&
    typeof value.tag === 'string'
  )
}

/** 기기에서 읽은 값을 확인한다. 없거나, 다른 형식 버전이거나, 규격에 맞지 않으면 null (쓰지 않는다) */
export function parseDeviceRecord(value: unknown): DeviceRecord | null {
  if (!isFields(value) || value.version !== DEVICE_RECORD_VERSION) return null
  const { email, kdf, payload, revision, lastSeenRevision, pendingSync } = value
  if (typeof email !== 'string' || !isKdf(kdf) || !isPayload(payload)) return null
  if (!isRevision(revision) || !isRevision(lastSeenRevision) || typeof pendingSync !== 'boolean') return null
  return {
    version: DEVICE_RECORD_VERSION,
    email,
    kdf: { salt: kdf.salt, iterations: kdf.iterations },
    payload: { version: payload.version, cipher: payload.cipher, iv: payload.iv, ciphertext: payload.ciphertext, tag: payload.tag },
    revision,
    lastSeenRevision,
    pendingSync,
  }
}
