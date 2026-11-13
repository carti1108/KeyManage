// 설정 화면의 모달 세 개(복원·비밀번호 변경·계정 삭제). 입력값·검사·진행 상태는 모두 Kotlin 상태에서 받는다
import { useEffect, useId, useRef, type FormEvent, type KeyboardEvent } from 'react'
import type { WebSettingsState, WebSettingsView } from 'keymanage-web-session'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { ModalDialog } from '../components/ModalDialog'
import { PasswordField } from '../components/PasswordField'
import { TextField } from '../components/TextField'

interface DialogProps {
  view: WebSettingsView
  state: WebSettingsState
}

/** 실패해 진행이 끝나면 첫 입력 칸으로 돌아가 바로 다시 입력하게 한다 */
function useRefocusOnError(state: WebSettingsState) {
  const firstInput = useRef<HTMLInputElement>(null)
  useEffect(() => {
    if (state.error !== null && !state.inProgress) firstInput.current?.focus()
  }, [state.error, state.inProgress])
  return firstInput
}

function closeOnEscape(view: WebSettingsView) {
  return (event: KeyboardEvent) => {
    if (event.key !== 'Escape') return
    event.preventDefault()
    view.cancelModal()
  }
}

function submitWith(action: () => void) {
  return (event: FormEvent) => {
    event.preventDefault()
    action()
  }
}

function ProgressStatus({ text }: { text: string }) {
  return (
    <p role="status" className="text-sm text-muted-foreground">
      {text}
    </p>
  )
}

export function RestoreDialog({ view, state }: DialogProps) {
  const titleId = useId()
  const bodyId = useId()
  const passwordInput = useRefocusOnError(state)
  return (
    <div role="dialog" aria-labelledby={titleId} aria-describedby={bodyId} className="rounded-md border border-border p-3" onKeyDown={closeOnEscape(view)}>
      <form className="flex flex-col gap-3" onSubmit={submitWith(() => view.submitRestore())} noValidate>
        <p id={titleId} className="text-sm font-semibold">
          {state.restoreDialog.title}
        </p>
        <p id={bodyId} className="text-sm text-muted-foreground">
          {state.restoreDialog.body}
        </p>
        <PasswordField
          ref={passwordInput}
          label={state.restoreDialog.passwordLabel}
          autoComplete="off"
          showCopyAction={false}
          value={state.restorePassword}
          onTextChange={(value) => view.editRestorePassword(value)}
          disabled={state.inProgress}
          autoFocus
        />
        {state.error && <Alert>{state.error}</Alert>}
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => view.cancelModal()} disabled={state.inProgress}>
            {state.restoreDialog.cancel}
          </Button>
          <Button type="submit" size="sm" loading={state.inProgress} disabled={!state.canSubmitRestore}>
            {state.restoreDialog.submit}
          </Button>
        </div>
        {state.inProgress && <ProgressStatus text={state.restoreDialog.progress} />}
      </form>
    </div>
  )
}

export function ChangePasswordDialog({ view, state }: DialogProps) {
  const titleId = useId()
  const bodyId = useId()
  const ruleId = useId()
  const currentInput = useRefocusOnError(state)
  return (
    <ModalDialog labelledBy={titleId} describedBy={bodyId} onKeyDown={closeOnEscape(view)}>
      <form className="flex flex-col gap-3" onSubmit={submitWith(() => view.submitChangePassword())} noValidate>
        <p id={titleId} className="text-sm font-semibold">
          {state.changePasswordDialog.title}
        </p>
        <p id={bodyId} className="text-sm text-muted-foreground">
          {state.changePasswordDialog.body}
        </p>
        <PasswordField
          ref={currentInput}
          label={state.changePasswordDialog.currentLabel}
          autoComplete="current-password"
          showCopyAction={false}
          value={state.currentPassword}
          onTextChange={(value) => view.editCurrentPassword(value)}
          disabled={state.inProgress}
          autoFocus
        />
        <PasswordField
          label={state.changePasswordDialog.newLabel}
          autoComplete="new-password"
          showCopyAction={false}
          value={state.newPassword}
          onTextChange={(value) => view.editNewPassword(value)}
          disabled={state.inProgress}
          aria-describedby={ruleId}
        />
        <p id={ruleId} className="text-xs text-muted-foreground">
          {state.changePasswordDialog.newPasswordRule}
        </p>
        <PasswordField
          label={state.changePasswordDialog.confirmLabel}
          autoComplete="new-password"
          showCopyAction={false}
          value={state.passwordConfirmation}
          onTextChange={(value) => view.editPasswordConfirmation(value)}
          disabled={state.inProgress}
        />
        {state.error && <Alert>{state.error}</Alert>}
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => view.cancelModal()} disabled={state.inProgress}>
            {state.changePasswordDialog.cancel}
          </Button>
          <Button type="submit" size="sm" loading={state.inProgress} disabled={!state.canSubmitChangePassword}>
            {state.changePasswordDialog.submit}
          </Button>
        </div>
        {state.inProgress && <ProgressStatus text={state.changePasswordDialog.progress} />}
      </form>
    </ModalDialog>
  )
}

export function DeleteAccountDialog({ view, state }: DialogProps) {
  const titleId = useId()
  const bodyId = useId()
  const passwordInput = useRefocusOnError(state)
  return (
    <ModalDialog labelledBy={titleId} describedBy={bodyId} onKeyDown={closeOnEscape(view)}>
      <form className="flex flex-col gap-3" onSubmit={submitWith(() => view.submitDeleteAccount())} noValidate>
        <p id={titleId} className="text-sm font-semibold text-destructive">
          {state.deleteAccountDialog.title}
        </p>
        <p id={bodyId} className="text-sm text-muted-foreground">
          {state.deleteAccountDialog.body}
        </p>
        <PasswordField
          ref={passwordInput}
          label={state.deleteAccountDialog.passwordLabel}
          autoComplete="current-password"
          showCopyAction={false}
          value={state.deletionPassword}
          onTextChange={(value) => view.editDeletionPassword(value)}
          disabled={state.inProgress}
          autoFocus
        />
        <TextField
          label={state.deleteAccountDialog.phraseLabel}
          autoComplete="off"
          spellCheck={false}
          value={state.deletionPhrase}
          onChange={(event) => view.editDeletionPhrase(event.target.value.normalize('NFC'))}
          disabled={state.inProgress}
        />
        {state.error && <Alert>{state.error}</Alert>}
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => view.cancelModal()} disabled={state.inProgress}>
            {state.deleteAccountDialog.cancel}
          </Button>
          <Button type="submit" variant="destructive" size="sm" loading={state.inProgress} disabled={!state.canSubmitDeletion}>
            {state.deleteAccountDialog.submit}
          </Button>
        </div>
        {state.inProgress && <ProgressStatus text={state.deleteAccountDialog.progress} />}
      </form>
    </ModalDialog>
  )
}
