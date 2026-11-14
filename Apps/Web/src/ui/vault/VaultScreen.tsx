// 열린 볼트의 목록·상세·편집 화면을 오간다 (와이어프레임 03 볼트 / 04 편집 2분할 지원)
import { Shield } from 'lucide-react'
import { useState } from 'react'
import { cn } from '../cn'
import type { ClipboardPort } from '../clipboard/clipboardPort'
import { useCopy } from '../clipboard/useCopy'
import { Alert } from '../components/Alert'
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
  clipboard: Pick<ClipboardPort, 'copy'>
  now?: () => Date
  /** 와이어프레임 3분할 명세에 맞춘 데스크톱 2분할(목록+상세) 레이아웃 */
  layout?: 'single' | 'split'
}

const LIST: View = { kind: 'list' }

export function VaultScreen({ vault, onSave, clipboard, now = () => new Date(), layout = 'single' }: VaultScreenProps) {
  const copy = useCopy(clipboard)
  const onCopy = (text: string) => void copy.copy(text)
  return (
    <>
      {copy.error && <Alert>{copy.error}</Alert>}
      <VaultView vault={vault} onSave={onSave} onCopy={onCopy} now={now} layout={layout} />
    </>
  )
}

function VaultView({
  vault,
  onSave,
  onCopy,
  now,
  layout,
}: Omit<VaultScreenProps, 'clipboard'> & { onCopy: (text: string) => void; now: () => Date }) {
  const [view, setView] = useState<View>(LIST)
  // 목록으로 돌아올 때 포커스를 돌려줄 항목
  const [lastOpened, setLastOpened] = useState<string | null>(null)
  const { data, readOnly } = vault
  // 다른 기기에서 지워졌거나 동기화로 사라진 항목을 보고 있었다면 목록을 보인다
  const current = view.kind === 'detail' || view.kind === 'edit' ? data.items.find((item) => item.id === view.id && item.deletedAt === undefined) : undefined

  // 1. 단일 컬럼 모드 (모바일 또는 단위 테스트 호환)
  if (layout === 'single') {
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
          onCopy={onCopy}
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
        onCopy={onCopy}
        onDelete={async () => {
          await onSave(deleteItem(data, id, now()))
          setView((v) => (v.kind === 'detail' && v.id === id ? LIST : v))
        }}
      />
    )
  }

  // 2. 데스크톱 2분할 모드 (와이어프레임 03 볼트 / 04 편집: 좌 360px 목록, 우 flex 상세/편집)
  const isDetailActive = (view.kind === 'detail' || view.kind === 'edit') && current !== undefined
  const isAddActive = view.kind === 'add'
  const isPanelActive = isDetailActive || isAddActive

  return (
    <div className="flex flex-col lg:flex-row gap-6 w-full items-start">
      {/* 좌측 목록 컬럼 (와이어프레임 Web: 360px / lg:w-96) */}
      <div className={cn("w-full lg:w-96 lg:shrink-0", isPanelActive && "hidden lg:block")}>
        <ItemList
          key={view.kind === 'list' ? 'list' : 'item-active'}
          data={data}
          readOnly={readOnly}
          focusId={lastOpened}
          onOpen={(id) => {
            setLastOpened(id)
            setView({ kind: 'detail', id })
          }}
          onAdd={() => setView({ kind: 'add' })}
          onCopy={onCopy}
        />
      </div>

      {/* 우측 상세/편집 컬럼 (와이어프레임 Web: flex-1) */}
      <div className={cn("w-full flex-1 min-w-0", !isPanelActive && "hidden lg:block")}>
        {renderDetailPane()}
      </div>
    </div>
  )

  function renderDetailPane() {
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

    if (view.kind === 'detail' && current) {
      const { id } = current
      return (
        <ItemDetail
          item={current}
          readOnly={readOnly}
          onBack={() => setView(LIST)}
          onEdit={() => setView({ kind: 'edit', id })}
          onCopy={onCopy}
          onDelete={async () => {
            await onSave(deleteItem(data, id, now()))
            setView((v) => (v.kind === 'detail' && v.id === id ? LIST : v))
          }}
        />
      )
    }

    // 데스크톱에서 아직 항목을 선택하지 않았을 때의 빈 상태 안내
    return (
      <div className="flex h-64 flex-col items-center justify-center rounded-lg border border-dashed border-border p-6 text-center text-muted-foreground">
        <Shield aria-hidden className="mb-2 size-8 text-muted-foreground/40" />
        <p className="text-label font-medium">선택된 항목이 없습니다</p>
        <p className="text-caption text-muted-foreground">목록에서 항목을 선택하거나 새 항목을 추가해 주세요.</p>
      </div>
    )
  }
}

// 새 항목에는 비어 있는 선택 필드를 넣지 않는다
function withoutEmptyOptional({ url, notes, ...required }: ItemFields): ItemFields {
  return { ...required, ...(url ? { url } : {}), ...(notes ? { notes } : {}) }
}
