// 항목 목록과 검색
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { listItems } from '../../domain/vault/itemList'
import type { VaultData } from '../../domain/vault/vaultData'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { TextField } from '../components/TextField'

export interface ItemListProps {
  data: VaultData
  readOnly: boolean
  onOpen: (id: string) => void
  onAdd: () => void
}

export function ItemList({ data, readOnly, onOpen, onAdd }: ItemListProps) {
  const [query, setQuery] = useState('')
  const items = listItems(data, query)
  const empty = data.items.every((item) => item.deletedAt !== undefined)

  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-end gap-2">
        <TextField
          label="항목 검색"
          type="search"
          autoComplete="off"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="flex-1"
        />
        {!readOnly && (
          <Button icon={<Plus aria-hidden className="size-4" />} onClick={onAdd}>
            항목 추가
          </Button>
        )}
      </div>
      {items.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-foreground">{empty ? '저장된 항목이 없습니다.' : '검색 결과가 없습니다.'}</p>
      ) : (
        <ul aria-label="항목 목록" className="flex flex-col gap-2">
          {items.map((item) => (
            <li key={item.id}>
              <Card interactive padding="sm" onClick={() => onOpen(item.id)}>
                <span className="block font-medium">{item.serviceName}</span>
                {item.username && <span className="block truncate text-sm text-muted-foreground">{item.username}</span>}
              </Card>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
