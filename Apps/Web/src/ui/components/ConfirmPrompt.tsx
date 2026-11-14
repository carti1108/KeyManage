// 되돌리기 어려운 동작 전에 묻는 확인. 결정을 요구하므로 alertdialog로 알리고 안전한 쪽(취소)에 먼저 포커스를 둔다
import { useId } from 'react'
import { cn } from '../cn'
import { Button } from './Button'

export interface ConfirmPromptProps {
  title: string
  body: string
  confirmLabel: string
  cancelLabel?: string
  onConfirm: () => void
  onCancel: () => void
  /** 확인 요청을 보내는 중 */
  pending?: boolean
  /** destructive: 되돌리기 어려운 동작(기본). notice: 안내를 읽고 진행하는 동작 */
  tone?: keyof typeof TONE_CLASSES
}

const TONE_CLASSES = {
  destructive: { box: 'border-destructive/40 bg-destructive/10 text-destructive', confirm: 'destructive' },
  notice: { box: 'border-border bg-muted text-foreground', confirm: 'primary' },
} as const

export function ConfirmPrompt({ title, body, confirmLabel, cancelLabel = '취소', onConfirm, onCancel, pending = false, tone = 'destructive' }: ConfirmPromptProps) {
  const titleId = useId()
  const bodyId = useId()
  return (
    <div
      role="alertdialog"
      aria-labelledby={titleId}
      aria-describedby={bodyId}
      className={cn('flex flex-col gap-1 rounded-md border px-3 py-2 text-label', TONE_CLASSES[tone].box)}
    >
      <p id={titleId} className="font-semibold">
        {title}
      </p>
      <p id={bodyId}>{body}</p>
      <div className="mt-2 flex gap-2">
        <Button variant="outline" size="sm" onClick={onCancel} disabled={pending} autoFocus>
          {cancelLabel}
        </Button>
        <Button variant={TONE_CLASSES[tone].confirm} size="sm" onClick={onConfirm} loading={pending}>
          {confirmLabel}
        </Button>
      </div>
    </div>
  )
}
