// 웹 TypeScript 도메인이 만든 볼트 JSON을 Kotlin 테스트 상수로 저장한다 (domain-migration-plan.md 1단계)
// Kotlin(VaultCompatTest)은 같은 입력·같은 편집 순서로 만든 결과가 바이트 단위로 같은지 비교한다.
// 실행: node Apps/Shared/domain/compat/generate-vault-fixtures.ts (Node 23.6 이상, TypeScript 직접 실행)
import { writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { addItem, deleteItem, emptyVault, parseVaultData, updateItem } from '../../../Web/src/domain/vault/vaultData.ts'
import { mergeVaults } from '../../../Web/src/domain/vault/mergeVaults.ts'

const TS = '2026-10-29T00:00:00Z'
const tricky = 'q"uote \\back\nline\ttab \u0001 \u2028 </script> 이모지😀 $dollar'

// 웹이 저장해 둔 볼트라고 가정한 입력. 모르는 필드, 다른 플랫폼의 null, 오프셋 시각, 특수 문자를 섞었다
export const fixture = JSON.stringify({
  version: 1,
  updatedAt: '2026-10-29T09:00:00+09:00',
  futureTop: { nested: [1, 2.5, true, null], text: tricky },
  items: [
    { id: 'a', serviceName: tricky, username: 'u', password: 'p', url: null, notes: null, deletedAt: null, createdAt: TS, updatedAt: TS, totp: 'SECRET', extra: { n: 3 } },
    { id: 'b', serviceName: 'B', username: 'ub', password: 'pb', createdAt: TS, updatedAt: '2026-10-29T00:00:00.123Z' },
    { id: 'gone', createdAt: TS, updatedAt: TS, deletedAt: TS },
  ],
})

const T1 = new Date('2026-10-29T00:01:00Z')
const T2 = new Date('2026-10-29T00:02:00Z')

const parsed = parseVaultData(fixture)
const added = addItem(parsed, { serviceName: '새 항목', username: 'new', password: tricky, notes: '메모' }, T1, 'n-1')
const updated = updateItem(added, 'a', { password: 'p2', url: null, notes: '새 메모' }, T1)
const deleted = deleteItem(updated, 'b', T2)
const remote = updateItem(parsed, 'b', { username: 'remote-ub' }, T2)

const outputs: Record<string, string> = {
  fixture,
  parsed: JSON.stringify(parsed),
  added: JSON.stringify(added),
  updated: JSON.stringify(updated),
  deleted: JSON.stringify(deleted),
  mergedLocalFirst: JSON.stringify(mergeVaults(deleted, remote)),
  mergedRemoteFirst: JSON.stringify(mergeVaults(remote, deleted)),
  empty: JSON.stringify(emptyVault(T1)),
}

const escaped = JSON.stringify(outputs).replaceAll('\\', '\\\\').replaceAll('"', '\\"').replaceAll('$', '\\$')
const target = fileURLToPath(new URL('../src/commonTest/kotlin/com/keymanage/domain/vault/VaultCompatFixtures.kt', import.meta.url))
writeFileSync(
  target,
  `package com.keymanage.domain.vault\n\n// 생성 파일. 직접 고치지 않는다. 다시 만들기: node Apps/Shared/domain/compat/generate-vault-fixtures.ts\n` +
    `internal const val VAULT_COMPAT_JSON = "${escaped}"\n`,
)
console.log(`wrote ${target}`)
