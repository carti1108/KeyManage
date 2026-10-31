// 항목 추가·수정 폼 (VLT-001)
import { Wand2 } from 'lucide-react'
import { useId, useRef, useState, type KeyboardEvent } from 'react'
import { hasSequentialDigits, isWeak, scorePassword } from '../../domain/generator/passwordStrength'
import { checkItemFields } from '../../domain/vault/itemList'
import type { ItemFields, VaultItem } from '../../domain/vault/vaultData'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { EntropyGauge } from '../components/EntropyGauge'
import { PasswordField } from '../components/PasswordField'
import { TextField } from '../components/TextField'
import { PasswordGenerator } from './PasswordGenerator'
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
  const [generating, setGenerating] = useState(false)
  const passwordInput = useRef<HTMLInputElement>(null)
  const save = useSave()
  const titleId = useId()
  const score = scorePassword(password)
  const notesId = useId()

  // 생성기가 사라지면 누른 버튼도 사라지므로 비밀번호 칸으로 포커스를 돌려준다
  function closeGenerator() {
    setGenerating(false)
    passwordInput.current?.focus()
  }

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
          ref={passwordInput}
          {...IGNORE_PASSWORD_MANAGERS}
        />
        {password !== '' && <EntropyGauge score={score} />}
        {/* 약한 비밀번호도 저장은 허용한다. 사이트마다 규칙이 달라 사용자가 고를 수 없는 경우가 있다 (GEN-001) */}
        {/* 알림 영역은 늘 두고 안의 내용만 바꾼다. 내용과 함께 새로 생긴 영역은 화면 낭독기가 읽지 않는 경우가 있다 */}
        <div role="status">
          {password !== '' && isWeak(score) && (
            <Alert variant="info" title="취약한 비밀번호 경고" live={false}>
              현재 비밀번호는 보안에 취약합니다. 80점 이상의 강력한 비밀번호로 재생성하세요.
            </Alert>
          )}
          {/* 점수와 별개로 안내만 한다. 점수가 높아도 보인다 (GEN-001) */}
          {hasSequentialDigits(password) && (
            <p className="mt-1 text-sm text-muted-foreground">123처럼 이어지는 숫자가 들어 있어 추측하기 쉽습니다.</p>
          )}
        </div>
        {generating ? (
          <PasswordGenerator
            onUse={(generated) => {
              setPassword(generated)
              closeGenerator()
            }}
            onClose={closeGenerator}
          />
        ) : (
          <Button variant="outline" size="sm" icon={<Wand2 aria-hidden className="size-4" />} onClick={() => setGenerating(true)} className="self-start">
            비밀번호 생성
          </Button>
        )}
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
