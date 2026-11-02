// 암호화 백업 내보내기와 복원 진입점 (VLT-004)
// 내보내기는 안내를 보인 뒤 저장한다. 복원은 파일 형식을 먼저 확인하고 그 파일의 비밀번호를 받는다
import { ArchiveRestore, Download } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent, type RefObject } from 'react'
import { readBackupFile, type BackupFile, type BackupFileSource, type BackupFiles, type VaultBackup } from '../../domain/backup/backupFile'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { ConfirmPrompt } from '../components/ConfirmPrompt'
import { PasswordField } from '../components/PasswordField'
import { describeError } from '../messages/errorMessage'
import { EXPORT_NOTICE, RESTORE_DIALOG, restoredMessage } from '../messages/backupNotices'
import { useSave } from '../vault/useSave'

export interface BackupActionsProps {
  /** 읽기 전용 볼트에는 복원하지 않는다. 내보내기는 한다 */
  readOnly: boolean
  files: BackupFiles
  onExport: (now: Date) => Promise<BackupFile>
  /** @param now 복원 시각. 되돌린 항목의 수정 시각이 된다 @returns 복원한 항목 수 */
  onRestore: (backup: VaultBackup, password: string, now: Date) => Promise<number>
  now?: () => Date
}

type Step = { kind: 'idle' } | { kind: 'confirmExport' } | { kind: 'password'; backup: VaultBackup }

const IDLE: Step = { kind: 'idle' }

export function BackupActions({ readOnly, files, onExport, onRestore, now = () => new Date() }: BackupActionsProps) {
  const [step, setStep] = useState<Step>(IDLE)
  const [password, setPassword] = useState('')
  const [done, setDone] = useState<string | null>(null)
  // 내보내기·파일 읽기·복원을 한 번에 하나만 한다. 같은 틱의 두 번째 제출도 막는다
  const action = useSave()
  const exportButton = useRef<HTMLButtonElement>(null)
  const restoreButton = useRef<HTMLButtonElement>(null)
  const passwordInput = useRef<HTMLInputElement>(null)
  // 파일 선택 차례. 새 선택이나 다른 동작을 시작하면 올려, 늦게 끝난 이전 선택 결과를 버린다
  const pickTurn = useRef(0)
  // 대화상자가 닫히면 포커스를 돌려줄 버튼. 진행 중에는 버튼이 막혀 있으므로 끝난 뒤 옮긴다
  const returnFocus = useRef<RefObject<HTMLButtonElement | null> | null>(null)
  const { pending, error } = action

  useEffect(() => {
    if (step.kind !== 'idle' || pending || returnFocus.current === null) return
    returnFocus.current.current?.focus()
    returnFocus.current = null
  }, [step, pending])

  // 복원에 실패하면 비운 비밀번호 칸으로 돌아가 바로 다시 입력하게 한다
  useEffect(() => {
    if (step.kind === 'password' && error !== null && !pending) passwordInput.current?.focus()
  }, [step, error, pending])

  function start(next: Step) {
    pickTurn.current += 1
    setDone(null)
    action.showError(null)
    setStep(next)
  }

  function close(opener: RefObject<HTMLButtonElement | null>) {
    returnFocus.current = opener
    setPassword('')
    setStep(IDLE)
  }

  function cancel(opener: RefObject<HTMLButtonElement | null>) {
    action.showError(null)
    close(opener)
  }

  /** 진행 중이 아니면 Esc로 닫는다 */
  function closeOnEscape(event: KeyboardEvent, opener: RefObject<HTMLButtonElement | null>) {
    if (event.key !== 'Escape' || pending) return
    event.preventDefault()
    cancel(opener)
  }

  function exportBackup() {
    void action.run(async () => {
      files.save(await onExport(now()))
      close(exportButton)
    })
  }

  /**
   * 선택 창이 열려 있는 동안에는 버튼을 막지 않는다. cancel 이벤트가 없는 브라우저에서 취소하면 끝나지 않으므로,
   * 다시 눌러 새로 고를 수 있게 하고 가장 최근 선택의 결과만 쓴다. 파일을 받은 뒤부터(형식 검사) 진행 중으로 막는다
   */
  async function pickFile() {
    start(IDLE)
    const turn = pickTurn.current
    let picked: BackupFileSource | null
    try {
      picked = await files.pick()
    } catch (caught) {
      if (turn === pickTurn.current) action.showError(describeError(caught))
      return
    }
    if (picked === null || turn !== pickTurn.current) return
    const source = picked
    void action.run(async () => {
      const backup = await readBackupFile(source)
      if (turn === pickTurn.current) setStep({ kind: 'password', backup })
    })
  }

  function restore(event: FormEvent, backup: VaultBackup) {
    event.preventDefault()
    // 입력값은 요청에만 담고 화면 상태에서는 바로 버린다
    const entered = password
    setPassword('')
    if (entered === '') return
    void action.run(async () => {
      const count = await onRestore(backup, entered, now())
      close(restoreButton)
      setDone(restoredMessage(count))
    })
  }

  return (
    <section className="flex flex-col gap-1.5 w-full" aria-label="백업">
      <div className="flex flex-col gap-1.5 w-full">
        <Button
          ref={exportButton}
          variant="outline"
          size="sm"
          className="w-full justify-start gap-2 h-7 px-2 text-xs font-normal whitespace-nowrap"
          icon={<Download aria-hidden className="size-3.5 shrink-0" />}
          disabled={pending}
          onClick={() => start({ kind: 'confirmExport' })}
        >
          백업 내보내기
        </Button>
        <Button
          ref={restoreButton}
          variant="outline"
          size="sm"
          className="w-full justify-start gap-2 h-7 px-2 text-xs font-normal whitespace-nowrap"
          icon={<ArchiveRestore aria-hidden className="size-3.5 shrink-0" />}
          disabled={readOnly || pending}
          onClick={() => void pickFile()}
        >
          백업에서 복원
        </Button>
      </div>
      {step.kind === 'confirmExport' && (
        <div onKeyDown={(e) => closeOnEscape(e, exportButton)}>
          <ConfirmPrompt
            tone="notice"
            title={EXPORT_NOTICE.title}
            body={EXPORT_NOTICE.body}
            confirmLabel={EXPORT_NOTICE.confirm}
            cancelLabel={EXPORT_NOTICE.cancel}
            pending={pending}
            onConfirm={exportBackup}
            onCancel={() => cancel(exportButton)}
          />
        </div>
      )}
      {step.kind === 'password' && (
        <RestoreDialog
          password={password}
          passwordRef={passwordInput}
          onPasswordChange={setPassword}
          pending={pending}
          onSubmit={(e) => restore(e, step.backup)}
          onCancel={() => cancel(restoreButton)}
          onKeyDown={(e) => closeOnEscape(e, restoreButton)}
        />
      )}
      {error && <Alert title={error.title}>{error.body}</Alert>}
      {done && <Alert variant="info">{done}</Alert>}
    </section>
  )
}

interface RestoreDialogProps {
  password: string
  passwordRef: RefObject<HTMLInputElement | null>
  onPasswordChange: (value: string) => void
  pending: boolean
  onSubmit: (event: FormEvent) => void
  onCancel: () => void
  onKeyDown: (event: KeyboardEvent) => void
}

function RestoreDialog({ password, passwordRef, onPasswordChange, pending, onSubmit, onCancel, onKeyDown }: RestoreDialogProps) {
  const titleId = useId()
  const bodyId = useId()
  return (
    <div role="dialog" aria-labelledby={titleId} aria-describedby={bodyId} className="rounded-md border border-border p-3" onKeyDown={onKeyDown}>
      <form className="flex flex-col gap-3" onSubmit={onSubmit} noValidate>
        <p id={titleId} className="text-sm font-semibold">
          {RESTORE_DIALOG.title}
        </p>
        <p id={bodyId} className="text-sm text-muted-foreground">
          {RESTORE_DIALOG.body}
        </p>
        <PasswordField
          ref={passwordRef}
          label={RESTORE_DIALOG.passwordLabel}
          autoComplete="off"
          showCopyAction={false}
          value={password}
          onTextChange={onPasswordChange}
          disabled={pending}
          autoFocus
        />
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={onCancel} disabled={pending}>
            {RESTORE_DIALOG.cancel}
          </Button>
          <Button type="submit" size="sm" loading={pending} disabled={password === ''}>
            {RESTORE_DIALOG.submit}
          </Button>
        </div>
        {pending && (
          <p role="status" className="text-sm text-muted-foreground">
            {RESTORE_DIALOG.progress}
          </p>
        )}
      </form>
    </div>
  )
}
