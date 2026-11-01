// 다른 탭이 쓴 기기 기록을 이 탭의 메모리 볼트와 합친다 (ADR-WEB-001)
// 항목 단위로 최종 변경 시각이 늦은 쪽을 고르므로(mergeVaults) 삭제를 되살리지 않는다
import type { EncryptedVaultPayload } from '../crypto/vaultCipher'
import type { DeviceRecord } from '../device/deviceRecord'
import { mergeSyncFields, samePayload } from '../device/reconcileDeviceRecord'
import { mergeVaults } from '../vault/mergeVaults'
import type { VaultData } from '../vault/vaultData'
import type { VaultSync } from '../vault/vaultSync'

export interface MemoryVault {
  readonly data: VaultData
  readonly payload: EncryptedVaultPayload
  readonly revision: number
  readonly lastSeenRevision: number
  readonly pendingSync: boolean
}

export interface MergedVault extends MemoryVault {
  /** 합친 내용이 메모리와 다름. 화면을 다시 그려야 한다 */
  readonly changed: boolean
}

// 같은 내용을 다시 암호화하면 IV가 바뀌어 다른 탭이 또 병합하게 된다. 내용이 같으면 기존 암호문을 그대로 쓴다
function sameData(a: VaultData, b: VaultData): boolean {
  return JSON.stringify(a) === JSON.stringify(b)
}

/**
 * 한쪽 내용이 다른 쪽을 모두 담으면 그쪽의 암호문과 대기 표시를 따른다. 그쪽이 서버와 맞춰졌으면 다른 쪽의 변경도 올라간 것이다.
 * 둘 다 새 내용을 가졌으면 새로 암호화하고, 둘 중 하나라도 대기면 대기로 둔다
 * @throws VaultDecryptError 등 기록을 열지 못함(다른 탭에서 바뀐 비밀번호로 로그인함)
 */
export async function mergeRecordIntoMemory(mine: MemoryVault, theirs: DeviceRecord, sync: Pick<VaultSync, 'open' | 'seal'>): Promise<MergedVault> {
  const theirData = samePayload(mine.payload, theirs.payload) ? mine.data : await sync.open(theirs.payload)
  const merged = mergeVaults(mine.data, theirData)
  const revision = Math.max(mine.revision, theirs.revision)
  const lastSeenRevision = Math.max(mine.lastSeenRevision, theirs.lastSeenRevision)
  if (sameData(merged, theirData)) {
    return { data: theirData, payload: theirs.payload, revision, lastSeenRevision, pendingSync: theirs.pendingSync, changed: !sameData(theirData, mine.data) }
  }
  if (sameData(merged, mine.data)) return { ...mine, revision, lastSeenRevision, changed: false }
  const fields = mergeSyncFields(mine, theirs)
  return { data: merged, payload: await sync.seal(merged), ...fields, changed: true }
}
