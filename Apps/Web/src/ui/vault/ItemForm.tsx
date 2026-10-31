// 항목 추가·수정 폼 (VLT-001)
import { useId, useState, type FormEvent } from 'react'
import { checkItemFields } from '../../domain/vault/itemList'
import type { ItemFields, VaultItem } from '../../domain/vault/vaultData'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PasswordField } from '../components/PasswordField'
import { TextField } from '../components/TextField'
import { useSave } from './useSave'

export interface ItemFormProps {
  /** 없으면 새 항목 */
  item?: VaultItem
  onSubmit: (fields: ItemFields) => Promise<void>
  onCancel: () => void
}

export function ItemForm({ item, onSubmit, onCancel }: ItemFormProps) {
  const [serviceName, setServiceName] = useState(item?.serviceName ?? '')
  const [username, setUsername] = useState(item?.username ?? '')
  const [password, setPassword] = useState(item?.password ?? '')
  const [url, setUrl] = useState(item?.url ?? '')
  const [notes, setNotes] = useState(item?.notes ?? '')
  const save = useSave()
  const notesId = useId()

  function submit(event: FormEvent) {
    event.preventDefault()
    const fields: ItemFields = { serviceName: serviceName.trim(), username, password, url, notes }
    if (checkItemFields(fields)) return save.showError({ body: '서비스 이름을 입력해 주세요.' })
    void save.run(() => onSubmit(fields))
  }

  return (
    <Card padding="lg">
      <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
        <h2 className="text-lg font-semibold">{item ? '항목 수정' : '항목 추가'}</h2>
        <TextField label="서비스 이름" value={serviceName} onChange={(e) => setServiceName(e.target.value)} required autoFocus />
        {/* 이 폼의 값은 다른 사이트의 자격 증명이다. 브라우저가 이 앱의 로그인 정보로 기억하거나 채우지 않게 한다 */}
        <TextField label="아이디" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="off" />
        <PasswordField label="비밀번호" value={password} onTextChange={setPassword} autoComplete="off" />
        <TextField label="URL" type="url" value={url} onChange={(e) => setUrl(e.target.value)} autoComplete="off" />
        <div className="flex flex-col gap-1.5">
          <label htmlFor={notesId} className="text-sm font-medium">
            메모
          </label>
          <textarea
            id={notesId}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={3}
            className="rounded-md border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </div>
        {save.error && <Alert title={save.error.title}>{save.error.body}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onCancel} disabled={save.pending}>
            취소
          </Button>
          <Button type="submit" loading={save.pending}>
            저장
          </Button>
        </div>
      </form>
    </Card>
  )
}
