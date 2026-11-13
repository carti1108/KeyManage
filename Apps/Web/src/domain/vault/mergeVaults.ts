// 409 충돌 뒤 로컬 볼트와 서버 볼트를 항목 단위로 합친다 (vault-spec 4장, VLT-001)
// 앱은 공유 Kotlin(Apps/Shared/domain의 MergeVaults)을 쓴다. 이 구현은 Kotlin 호환 픽스처 생성기(Apps/Shared/domain/compat)의 기준값을 만드는 데만 남겨 둔다
import type { VaultData, VaultItem } from './vaultData'

/** 같은 id면 최종 변경 시각이 늦은 쪽을 통째로 고르고, 같으면 서버(remote) 쪽을 고른다 */
export function mergeVaults(local: VaultData, remote: VaultData): VaultData {
  const localById = new Map(local.items.map((item) => [item.id, item]))
  const remoteIds = new Set(remote.items.map((item) => item.id))

  const merged = remote.items.map((remoteItem) => {
    const localItem = localById.get(remoteItem.id)
    return localItem !== undefined && lastChange(localItem) > lastChange(remoteItem) ? localItem : remoteItem
  })
  const localOnly = local.items.filter((item) => !remoteIds.has(item.id))

  // 모르는 최상위 필드는 서버 쪽을 따른다
  return { ...remote, updatedAt: later(local.updatedAt, remote.updatedAt), items: [...merged, ...localOnly] }
}

function lastChange(item: VaultItem): number {
  const updated = toMillis(item.updatedAt)
  return item.deletedAt === undefined ? updated : Math.max(updated, toMillis(item.deletedAt))
}

function later(a: string, b: string): string {
  return toMillis(a) > toMillis(b) ? a : b
}

// 읽을 수 없는 시각은 가장 오래된 것으로 본다. 그래야 형식이 맞는 쪽이 이긴다
function toMillis(time: string): number {
  const millis = Date.parse(time)
  return Number.isNaN(millis) ? -Infinity : millis
}
