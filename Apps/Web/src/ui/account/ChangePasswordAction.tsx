// 마스터 비밀번호 변경 진입점과 대화상자 (SEC-004)
// 변경 전 안내와 현재·새·확인 입력을 한 대화상자로 받는다. 새 비밀번호는 SEC-002 규칙과 현재와 다름을 먼저 확인한다
// 대화상자는 모달이다. 변경을 기다리는 동안 배경에서 편집·저장하면 옛 키로 요청이 나가므로 배경을 막고 포커스를 가둔다
import { KeyRound } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent, type RefObject } from 'react'
import { checkNewMasterPassword } from '../../domain/auth/masterPassword'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { ModalDialog } from '../components/ModalDialog'
import { PasswordField } from '../components/PasswordField'
import { CHANGE_PASSWORD_DIALOG, PASSWORD_CHANGE_DONE, PASSWORD_CHANGE_OFFLINE, SAME_MASTER_PASSWORD } from '../messages/accountNotices'
import { NEW_PASSWORD_PROBLEMS } from '../messages/authNotices'
import { useSave } from '../vault/useSave'

export interface ChangePasswordActionProps {
  /** 읽기 전용 볼트에서는 바꾸지 않는다 (SEC-004 규칙 1) */
  readOnly: boolean
  /** 서버에 연결되지 않음(브라우저 오프라인, 세션 없이 연 볼트). 누르면 이유를 알린다 */
  offline: boolean
  onChange: (currentPassword: string, newPassword: string) => Promise<void>
}

interface Inputs {
  readonly current: string
  readonly next: string
  readonly confirmation: string
}

const EMPTY: Inputs = { current: '', next: '', confirmation: '' }

export function ChangePasswordAction({ readOnly, offline, onChange }: ChangePasswordActionProps) {
  const [open, setOpen] = useState(false)
  const [inputs, setInputs] = useState<Inputs>(EMPTY)
  const [notice, setNotice] = useState<{ readonly kind: 'done' | 'offline' } | null>(null)
  // 한 번에 하나만 보낸다. 같은 틱의 두 번째 제출도 막는다
  const action = useSave()
  const openButton = useRef<HTMLButtonElement>(null)
  const currentInput = useRef<HTMLInputElement>(null)
  // 대화상자가 닫히면 진입 버튼으로 포커스를 돌린다. 진행 중에는 버튼이 막혀 있으므로 끝난 뒤 옮긴다
  const returnFocus = useRef(false)
  const { pending, error } = action

  useEffect(() => {
    if (open || pending || !returnFocus.current) return
    openButton.current?.focus()
    returnFocus.current = false
  }, [open, pending])

  // 실패하면 비운 현재 비밀번호 칸으로 돌아가 바로 다시 입력하게 한다
  useEffect(() => {
    if (open && error !== null && !pending) currentInput.current?.focus()
  }, [open, error, pending])

  function start() {
    action.showError(null)
    if (offline) {
      setNotice({ kind: 'offline' })
      return
    }
    setNotice(null)
    setOpen(true)
  }

  function close() {
    returnFocus.current = true
    setInputs(EMPTY)
    setOpen(false)
  }

  function cancel() {
    action.showError(null)
    close()
  }

  function closeOnEscape(event: KeyboardEvent) {
    if (event.key !== 'Escape' || pending) return
    event.preventDefault()
    cancel()
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const { current, next, confirmation } = inputs
    if (current === '') return
    const problem = checkNewMasterPassword(next, confirmation)
    if (problem) return action.showError({ body: NEW_PASSWORD_PROBLEMS[problem] })
    if (next === current) return action.showError({ body: SAME_MASTER_PASSWORD })
    // 현재 비밀번호는 요청에만 담고 화면 상태에서는 바로 버린다. 실패하면 다시 입력받는다
    setInputs((value) => ({ ...value, current: '' }))
    void action.run(async () => {
      await onChange(current, next)
      close()
      setNotice({ kind: 'done' })
    })
  }

  return (
    <section className="flex flex-col gap-1.5 w-full" aria-label="계정">
      <div className="flex flex-col gap-1.5 w-full">
        <Button
          ref={openButton}
          variant="outline"
          size="sm"
          className="w-full justify-start gap-2 h-7 px-2 text-xs font-normal whitespace-nowrap"
          icon={<KeyRound aria-hidden className="size-3.5 shrink-0" />}
          disabled={readOnly || pending}
          onClick={start}
        >
          {CHANGE_PASSWORD_DIALOG.title}
        </Button>
      </div>
      {open && (
        <ChangePasswordDialog
          inputs={inputs}
          currentRef={currentInput}
          onInputsChange={setInputs}
          pending={pending}
          onSubmit={submit}
          onCancel={cancel}
          onKeyDown={closeOnEscape}
          error={error}
        />
      )}
      {!open && error && <Alert title={error.title}>{error.body}</Alert>}
      {notice?.kind === 'offline' && <Alert>{PASSWORD_CHANGE_OFFLINE}</Alert>}
      {notice?.kind === 'done' && <Alert variant="info">{PASSWORD_CHANGE_DONE}</Alert>}
    </section>
  )
}

interface ChangePasswordDialogProps {
  inputs: Inputs
  currentRef: RefObject<HTMLInputElement | null>
  onInputsChange: (update: (value: Inputs) => Inputs) => void
  pending: boolean
  onSubmit: (event: FormEvent) => void
  onCancel: () => void
  onKeyDown: (event: KeyboardEvent) => void
  error: { readonly title?: string; readonly body: string } | null
}

function ChangePasswordDialog({ inputs, currentRef, onInputsChange, pending, onSubmit, onCancel, onKeyDown, error }: ChangePasswordDialogProps) {
  const titleId = useId()
  const bodyId = useId()
  const ruleId = useId()
  const set = (key: keyof Inputs) => (value: string) => onInputsChange((current) => ({ ...current, [key]: value }))

  return (
    <ModalDialog labelledBy={titleId} describedBy={bodyId} onKeyDown={onKeyDown}>
      <form className="flex flex-col gap-3" onSubmit={onSubmit} noValidate>
        <p id={titleId} className="text-sm font-semibold">
          {CHANGE_PASSWORD_DIALOG.title}
        </p>
        <p id={bodyId} className="text-sm text-muted-foreground">
          {CHANGE_PASSWORD_DIALOG.body}
        </p>
        <PasswordField
          ref={currentRef}
          label={CHANGE_PASSWORD_DIALOG.currentLabel}
          autoComplete="current-password"
          showCopyAction={false}
          value={inputs.current}
          onTextChange={set('current')}
          disabled={pending}
          autoFocus
        />
        <PasswordField
          label={CHANGE_PASSWORD_DIALOG.newLabel}
          autoComplete="new-password"
          showCopyAction={false}
          value={inputs.next}
          onTextChange={set('next')}
          disabled={pending}
          aria-describedby={ruleId}
        />
        <p id={ruleId} className="text-xs text-muted-foreground">
          {NEW_PASSWORD_PROBLEMS.tooShort}
        </p>
        <PasswordField
          label={CHANGE_PASSWORD_DIALOG.confirmLabel}
          autoComplete="new-password"
          showCopyAction={false}
          value={inputs.confirmation}
          onTextChange={set('confirmation')}
          disabled={pending}
        />
        {error && <Alert title={error.title}>{error.body}</Alert>}
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={onCancel} disabled={pending}>
            {CHANGE_PASSWORD_DIALOG.cancel}
          </Button>
          <Button type="submit" size="sm" loading={pending} disabled={inputs.current === ''}>
            {CHANGE_PASSWORD_DIALOG.submit}
          </Button>
        </div>
        {pending && (
          <p role="status" className="text-sm text-muted-foreground">
            {CHANGE_PASSWORD_DIALOG.progress}
          </p>
        )}
      </form>
    </ModalDialog>
  )
}

