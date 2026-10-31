// 항목 추가·수정 폼 (VLT-001)
import { useId, useState, type KeyboardEvent } from 'react'
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

// 이 폼의 값은 다른 사이트의 자격 증명과 비밀 메모다. 맞춤법 검사·자동 고침은 입력값을 외부 서비스로 보낼 수 있다
const PRIVATE_TEXT = { spellCheck: false, autoCorrect: 'off', autoCapitalize: 'off', autoComplete: 'off' } as const
// 비밀번호 관리자 확장이 이 칸을 채우거나 저장하지 않게 한다
const IGNORE_PASSWORD_MANAGERS = { 'data-1p-ignore': true, 'data-lpignore': 'true', 'data-bwignore': true } as const

export function ItemForm({ item, onSubmit, onCancel }: ItemFormProps) {
  const [serviceName, setServiceName] = useState(item?.serviceName ?? '')
  const [username, setUsername] = useState(item?.username ?? '')
  const [password, setPassword] = useState(item?.password ?? '')
  const [url, setUrl] = useState(item?.url ?? '')
  const [notes, setNotes] = useState(item?.notes ?? '')
  const save = useSave()
  const titleId = useId()
  const notesId = useId()

  function submit() {
    const fields: ItemFields = { serviceName: serviceName.trim(), username, password, url, notes }
    if (checkItemFields(fields)) return save.showError({ body: '서비스 이름을 입력해 주세요.' })
    void save.run(() => onSubmit(fields))
  }

  // <form> 제출로 다루면 브라우저가 아이디·비밀번호 쌍을 이 앱의 로그인 정보로 저장하려 한다. Enter는 직접 처리한다
  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Enter' || event.nativeEvent.isComposing) return
    if (event.target instanceof HTMLTextAreaElement || event.target instanceof HTMLButtonElement) return
    event.preventDefault()
    submit()
  }

  return (
    <Card padding="lg">
      <div role="group" aria-labelledby={titleId} className="flex flex-col gap-4" onKeyDown={onKeyDown}>
        <h2 id={titleId} className="text-lg font-semibold">
          {item ? '항목 수정' : '항목 추가'}
        </h2>
        <TextField label="서비스 이름" value={serviceName} onChange={(e) => setServiceName(e.target.value)} required autoFocus />
        <TextField label="아이디" value={username} onChange={(e) => setUsername(e.target.value)} {...PRIVATE_TEXT} />
        <PasswordField
          label="비밀번호"
          value={password}
          onTextChange={setPassword}
          autoComplete="off"
          concealment="css"
          {...IGNORE_PASSWORD_MANAGERS}
        />
        <TextField label="URL" type="url" value={url} onChange={(e) => setUrl(e.target.value)} {...PRIVATE_TEXT} />
        <div className="flex flex-col gap-1.5">
          <label htmlFor={notesId} className="text-sm font-medium">
            메모
          </label>
          <textarea
            id={notesId}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={3}
            {...PRIVATE_TEXT}
            className="rounded-md border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </div>
        {save.error && <Alert title={save.error.title}>{save.error.body}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onCancel} disabled={save.pending}>
            취소
          </Button>
          <Button onClick={submit} loading={save.pending}>
            저장
          </Button>
        </div>
      </div>
    </Card>
  )
}
