// 계정 삭제 진입점과 대화상자 (SEC-005)
// 삭제 전 안내와 마스터 비밀번호·확인 문구를 한 대화상자로 받는다. 확인 문구가 정확히 같아야 삭제 버튼이 켜진다
// 대화상자는 모달이다. 삭제를 기다리는 동안 배경에서 편집·저장하면 지워질 계정으로 요청이 나가므로 배경을 막는다
import { Trash2 } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent, type RefObject } from 'react'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { ModalDialog } from '../components/ModalDialog'
import { PasswordField } from '../components/PasswordField'
import { TextField } from '../components/TextField'
import { ACCOUNT_DELETION_OFFLINE, ACCOUNT_DELETION_PHRASE, DELETE_ACCOUNT_DIALOG } from '../messages/accountNotices'
import type { ErrorMessage } from '../messages/errorMessage'
import { useSave } from '../vault/useSave'

export interface DeleteAccountActionProps {
  /** 서버에 연결되지 않음(브라우저 오프라인, 세션 없이 연 볼트). 누르면 이유를 알린다 (SEC-005 규칙 1) */
  offline: boolean
  /** 성공하면 세션이 로그아웃되어 이 화면이 사라진다. 완료 안내는 로그인 화면이 보인다 */
  onDelete: (password: string) => Promise<void>
}

interface Inputs {
  readonly password: string
  readonly phrase: string
}

const EMPTY: Inputs = { password: '', phrase: '' }

/** 한글 자모가 나뉘어 입력돼도(NFD) 같은 글자로 보도록 NFC로 맞춘 뒤 정확히 비교한다. 앞뒤 공백은 그대로 다르다 (SEC-005 값) */
function matchesPhrase(phrase: string): boolean {
  return phrase.normalize('NFC') === ACCOUNT_DELETION_PHRASE
}

export function DeleteAccountAction({ offline, onDelete }: DeleteAccountActionProps) {
  const [open, setOpen] = useState(false)
  const [inputs, setInputs] = useState<Inputs>(EMPTY)
  const [offlineNotice, setOfflineNotice] = useState(false)
  // 한 번에 하나만 보낸다. 같은 틱의 두 번째 제출도 막는다
  const action = useSave()
  const openButton = useRef<HTMLButtonElement>(null)
  const passwordInput = useRef<HTMLInputElement>(null)
  // 대화상자가 닫히면 진입 버튼으로 포커스를 돌린다. 진행 중에는 버튼이 막혀 있으므로 끝난 뒤 옮긴다
  const returnFocus = useRef(false)
  const { pending, error } = action

  useEffect(() => {
    if (open || pending || !returnFocus.current) return
    openButton.current?.focus()
    returnFocus.current = false
  }, [open, pending])

  // 실패하면 비운 비밀번호 칸으로 돌아가 바로 다시 입력하게 한다
  useEffect(() => {
    if (open && error !== null && !pending) passwordInput.current?.focus()
  }, [open, error, pending])

  function start() {
    action.showError(null)
    setOfflineNotice(offline)
    if (!offline) setOpen(true)
  }

  function cancel() {
    action.showError(null)
    returnFocus.current = true
    setInputs(EMPTY)
    setOpen(false)
  }

  function closeOnEscape(event: KeyboardEvent) {
    if (event.key !== 'Escape' || pending) return
    event.preventDefault()
    cancel()
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const { password, phrase } = inputs
    if (password === '' || !matchesPhrase(phrase)) return
    // 비밀번호는 요청에만 담고 화면 상태에서는 바로 버린다. 실패하면 다시 입력받는다
    setInputs((value) => ({ ...value, password: '' }))
    void action.run(async () => {
      await onDelete(password)
      setInputs(EMPTY)
      setOpen(false)
    })
  }

  return (
    <section className="flex flex-col gap-2 w-full" aria-label="계정 삭제">
      <Button
        ref={openButton}
        variant="outline"
        size="sm"
        className="w-full justify-start gap-2 h-7 px-2 text-xs font-normal whitespace-nowrap text-destructive border-destructive/40 hover:bg-destructive/10"
        icon={<Trash2 aria-hidden className="size-3.5 shrink-0" />}
        disabled={pending}
        onClick={start}
      >
        {DELETE_ACCOUNT_DIALOG.title}
      </Button>
      {open && (
        <DeleteAccountDialog
          inputs={inputs}
          passwordRef={passwordInput}
          onInputsChange={setInputs}
          pending={pending}
          onSubmit={submit}
          onCancel={cancel}
          onKeyDown={closeOnEscape}
          error={error}
        />
      )}
      {offlineNotice && <Alert>{ACCOUNT_DELETION_OFFLINE}</Alert>}
    </section>
  )
}

interface DeleteAccountDialogProps {
  inputs: Inputs
  passwordRef: RefObject<HTMLInputElement | null>
  onInputsChange: (update: (value: Inputs) => Inputs) => void
  pending: boolean
  onSubmit: (event: FormEvent) => void
  onCancel: () => void
  onKeyDown: (event: KeyboardEvent) => void
  error: ErrorMessage | null
}

function DeleteAccountDialog({ inputs, passwordRef, onInputsChange, pending, onSubmit, onCancel, onKeyDown, error }: DeleteAccountDialogProps) {
  const titleId = useId()
  const bodyId = useId()
  const confirmed = inputs.password !== '' && matchesPhrase(inputs.phrase)

  return (
    <ModalDialog labelledBy={titleId} describedBy={bodyId} onKeyDown={onKeyDown}>
      <form className="flex flex-col gap-3" onSubmit={onSubmit} noValidate>
        <p id={titleId} className="text-sm font-semibold text-destructive">
          {DELETE_ACCOUNT_DIALOG.title}
        </p>
        <p id={bodyId} className="text-sm text-muted-foreground">
          {DELETE_ACCOUNT_DIALOG.body}
        </p>
        <PasswordField
          ref={passwordRef}
          label={DELETE_ACCOUNT_DIALOG.passwordLabel}
          autoComplete="current-password"
          showCopyAction={false}
          value={inputs.password}
          onTextChange={(password) => onInputsChange((value) => ({ ...value, password }))}
          disabled={pending}
          autoFocus
        />
        <TextField
          label={DELETE_ACCOUNT_DIALOG.phraseLabel}
          autoComplete="off"
          spellCheck={false}
          value={inputs.phrase}
          onChange={(event) => {
            const phrase = event.target.value
            onInputsChange((value) => ({ ...value, phrase }))
          }}
          disabled={pending}
        />
        {error && <Alert title={error.title}>{error.body}</Alert>}
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={onCancel} disabled={pending}>
            {DELETE_ACCOUNT_DIALOG.cancel}
          </Button>
          <Button type="submit" variant="destructive" size="sm" loading={pending} disabled={!confirmed}>
            {DELETE_ACCOUNT_DIALOG.submit}
          </Button>
        </div>
        {pending && (
          <p role="status" className="text-sm text-muted-foreground">
            {DELETE_ACCOUNT_DIALOG.progress}
          </p>
        )}
      </form>
    </ModalDialog>
  )
}
