// 항목 상세와 삭제 확인 (VLT-001)
import { ArrowLeft, Pencil, Trash2 } from 'lucide-react'
import { useState, type ReactNode } from 'react'
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

export function ItemDetail({ item, readOnly, onBack, onEdit, onDelete }: ItemDetailProps) {
  const [confirming, setConfirming] = useState(false)
  const save = useSave()

  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-2">
        <Button variant="ghost" size="sm" icon={<ArrowLeft aria-hidden className="size-4" />} onClick={onBack}>
          목록으로
        </Button>
        {!readOnly && (
          <div className="flex gap-2">
            <Button variant="outline" size="sm" icon={<Pencil aria-hidden className="size-4" />} onClick={onEdit}>
              수정
            </Button>
            <Button variant="destructive" size="sm" icon={<Trash2 aria-hidden className="size-4" />} onClick={() => setConfirming(true)}>
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
          onConfirm={() => void save.run(onDelete)}
          onCancel={() => setConfirming(false)}
        />
      )}
      {save.error && <Alert title={save.error.title}>{save.error.body}</Alert>}
      <Card className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold">{item.serviceName}</h2>
        <dl className="flex flex-col gap-3">
          {item.username && <Field label="아이디">{item.username}</Field>}
          {/* 비밀번호는 가려서 보이고, 보기를 누르면 10초 동안만 펼친다 */}
          <PasswordField label="비밀번호" value={item.password ?? ''} onTextChange={noop} readOnly placeholder="" />
          {/* 저장된 URL을 링크로 만들면 javascript: 같은 값이 실행될 수 있어 글자로만 보인다 */}
          {item.url && <Field label="URL">{item.url}</Field>}
          {item.notes && <Field label="메모">{<span className="whitespace-pre-wrap">{item.notes}</span>}</Field>}
        </dl>
      </Card>
    </section>
  )
}
