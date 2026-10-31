// 항목 상세와 삭제 확인 (VLT-001)
import { ArrowLeft, Copy, Pencil, Trash2 } from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import type { VaultItem } from '../../domain/vault/vaultData'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ConfirmPrompt } from '../components/ConfirmPrompt'
import { PasswordField } from '../components/PasswordField'
import { useSave } from './useSave'

export interface ItemDetailProps {
  item: VaultItem
  readOnly: boolean
  onBack: () => void
  onEdit: () => void
  onDelete: () => Promise<void>
  onCopy: (text: string) => void
}

const noop = () => {}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="break-words text-sm">{children}</dd>
    </div>
  )
}

export function ItemDetail({ item, readOnly, onBack, onEdit, onDelete, onCopy }: ItemDetailProps) {
  const [confirming, setConfirming] = useState(false)
  const save = useSave()
  const heading = useRef<HTMLHeadingElement>(null)

  // 화면이 바뀌면 누른 버튼이 사라지므로, 보조 기술이 새 화면을 알도록 제목으로 포커스를 옮긴다
  useEffect(() => heading.current?.focus(), [])

  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-2">
        {/* 삭제 요청 중에 화면을 떠나면 결과(실패 안내)를 볼 곳이 없어진다 */}
        <Button variant="ghost" size="sm" icon={<ArrowLeft aria-hidden className="size-4" />} onClick={onBack} disabled={save.pending}>
          목록으로
        </Button>
        {!readOnly && (
          <div className="flex gap-2">
            <Button variant="outline" size="sm" icon={<Pencil aria-hidden className="size-4" />} onClick={onEdit} disabled={save.pending}>
              수정
            </Button>
            <Button
              variant="destructive"
              size="sm"
              icon={<Trash2 aria-hidden className="size-4" />}
              onClick={() => setConfirming(true)}
              disabled={save.pending}
            >
              삭제
            </Button>
          </div>
        )}
      </div>
      {confirming && (
        <ConfirmPrompt
          title="항목 삭제"
          body="이 항목을 삭제할까요? 다른 기기에서도 삭제됩니다."
          confirmLabel="삭제"
          pending={save.pending}
          onConfirm={() => void save.run(onDelete).then((ok) => !ok && setConfirming(false))}
          onCancel={() => setConfirming(false)}
        />
      )}
      {save.error && <Alert title={save.error.title}>{save.error.body}</Alert>}
      <Card className="flex flex-col gap-4">
        <h2 ref={heading} tabIndex={-1} className="text-lg font-semibold outline-none">
          {item.serviceName}
        </h2>
        {item.username && (
          <div className="flex items-end justify-between gap-2">
            <dl className="min-w-0">
              <Field label="아이디">{item.username}</Field>
            </dl>
            <Button
              variant="ghost"
              size="sm"
              aria-label="아이디 복사"
              icon={<Copy aria-hidden className="size-4" />}
              onClick={() => onCopy(item.username!)}
            />
          </div>
        )}
        {/* 비밀번호는 가려서 보이고, 보기를 누르면 10초 동안만 펼친다 */}
        <PasswordField
          label="비밀번호"
          value={item.password ?? ''}
          onTextChange={noop}
          readOnly
          placeholder=""
          onCopy={item.password ? () => onCopy(item.password!) : undefined}
        />
        {(item.url || item.notes) && (
          <dl className="flex flex-col gap-3">
            {/* 저장된 URL을 링크로 만들면 javascript: 같은 값이 실행될 수 있어 글자로만 보인다 */}
            {item.url && <Field label="URL">{item.url}</Field>}
            {item.notes && (
              <Field label="메모">
                <span className="whitespace-pre-wrap">{item.notes}</span>
              </Field>
            )}
          </dl>
        )}
      </Card>
    </section>
  )
}
