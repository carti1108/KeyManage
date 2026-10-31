// 열린 볼트의 목록·상세·편집 화면을 오간다
import { useState } from 'react'
import { itemChanges } from '../../domain/vault/itemList'
import { addItem, deleteItem, updateItem, type ItemFields } from '../../domain/vault/vaultData'
import type { VaultData } from '../../domain/vault/vaultData'
import type { SyncedVault } from '../../domain/vault/vaultSync'
import { ItemDetail } from './ItemDetail'
import { ItemForm } from './ItemForm'
import { ItemList } from './ItemList'

type View = { kind: 'list' } | { kind: 'detail'; id: string } | { kind: 'edit'; id: string } | { kind: 'add' }

export interface VaultScreenProps {
  vault: SyncedVault
  /** 저장이 끝나면 부모가 새 볼트를 내려 준다 */
  onSave: (data: VaultData) => Promise<void>
  now?: () => Date
}

const LIST: View = { kind: 'list' }

export function VaultScreen({ vault, onSave, now = () => new Date() }: VaultScreenProps) {
  const [view, setView] = useState<View>(LIST)
  // 목록으로 돌아올 때 포커스를 돌려줄 항목
  const [lastOpened, setLastOpened] = useState<string | null>(null)
  const { data, readOnly } = vault
  // 다른 기기에서 지워졌거나 동기화로 사라진 항목을 보고 있었다면 목록을 보인다
  const current = view.kind === 'detail' || view.kind === 'edit' ? data.items.find((item) => item.id === view.id && item.deletedAt === undefined) : undefined
  const showList = view.kind === 'list' || ((view.kind === 'detail' || view.kind === 'edit') && current === undefined)

  if (showList) {
    return (
      <ItemList
        data={data}
        readOnly={readOnly}
        focusId={lastOpened}
        onOpen={(id) => {
          setLastOpened(id)
          setView({ kind: 'detail', id })
        }}
        onAdd={() => setView({ kind: 'add' })}
      />
    )
  }

  if (view.kind === 'add') {
    return (
      <ItemForm
        onCancel={() => setView(LIST)}
        onSubmit={async (fields: ItemFields) => {
          const id = globalThis.crypto.randomUUID()
          await onSave(addItem(data, withoutEmptyOptional(fields), now(), id))
          setLastOpened(id)
          setView({ kind: 'detail', id })
        }}
      />
    )
  }

  if (view.kind === 'edit' && current) {
    return (
      <ItemForm
        item={current}
        onCancel={() => setView({ kind: 'detail', id: current.id })}
        onSubmit={async (fields) => {
          const changes = itemChanges(current, fields)
          if (changes) await onSave(updateItem(data, current.id, changes, now()))
          setView({ kind: 'detail', id: current.id })
        }}
      />
    )
  }

  if (current === undefined) return null
  const { id } = current
  return (
    <ItemDetail
      item={current}
      readOnly={readOnly}
      onBack={() => setView(LIST)}
      onEdit={() => setView({ kind: 'edit', id })}
      onDelete={async () => {
        await onSave(deleteItem(data, id, now()))
        // 삭제를 기다리는 동안 다른 화면으로 갔다면 그 화면을 빼앗지 않는다
        setView((v) => (v.kind === 'detail' && v.id === id ? LIST : v))
      }}
    />
  )
}

// 새 항목에는 비어 있는 선택 필드를 넣지 않는다
function withoutEmptyOptional({ url, notes, ...required }: ItemFields): ItemFields {
  return { ...required, ...(url ? { url } : {}), ...(notes ? { notes } : {}) }
}
