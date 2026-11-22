// 사이드바 아래 상자 (Kotlin SettingsRib의 WebSettingsView). 자동 잠금·백업·비밀번호 변경·계정 삭제와 그 대화상자를 그린다.
// 설정 탭(SettingsRibScreen)과 같은 뷰를 보지만, 동작과 대화상자는 여기서만 그려 두 번 뜨지 않게 한다
import { ArchiveRestore, Download, KeyRound, Trash2 } from 'lucide-react'
import { useEffect, useRef, type RefObject } from 'react'
import type { WebSettingsView } from 'keymanage-web-session'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { ConfirmPrompt } from '../components/ConfirmPrompt'
import { AutoLockSelect } from '../vault/AutoLockSelect'
import { ChangePasswordDialog, DeleteAccountDialog, RestoreDialog } from './SettingsRibDialogs'
import { useViewStore } from './ribsReact'

const ENTRY_BUTTON = 'w-full justify-start gap-2 h-7 px-2 text-caption font-normal whitespace-nowrap'

export function SettingsSidebarBox({ view }: { view: WebSettingsView }) {
  const state = useViewStore(view.store)
  const exportButton = useRef<HTMLButtonElement>(null)
  const restoreButton = useRef<HTMLButtonElement>(null)
  const changePasswordButton = useRef<HTMLButtonElement>(null)
  const deleteButton = useRef<HTMLButtonElement>(null)
  const openers: Readonly<Record<string, RefObject<HTMLButtonElement | null>>> = {
    exportConfirm: exportButton,
    restorePassword: restoreButton,
    changePassword: changePasswordButton,
    deleteAccount: deleteButton,
  }

  // 모달이 닫히면 연 버튼으로 포커스를 돌린다. 진행 중에는 버튼이 막혀 있으므로 끝난 뒤 옮긴다
  const modalToReturnTo = useRef<string | null>(null)
  const previousModal = useRef('none')
  useEffect(() => {
    if (previousModal.current !== 'none' && state.modal === 'none') modalToReturnTo.current = previousModal.current
    previousModal.current = state.modal
    if (state.modal !== 'none' || state.inProgress || modalToReturnTo.current === null) return
    openers[modalToReturnTo.current]?.current?.focus()
    modalToReturnTo.current = null
    // eslint-disable-next-line react-hooks/exhaustive-deps -- openers는 매번 새로 만드는 ref 묶음이다
  }, [state.modal, state.inProgress])

  // 모달이 열려 있으면 오류는 모달 안에 보이고, 아니면 버튼 아래에 보인다
  const showsErrorBelow = state.error !== null && state.modal === 'none'

  return (
    <div className="rounded-lg bg-muted/30 p-3 flex flex-col gap-3 border border-border/50">
      <AutoLockSelect timeoutMs={state.autoLockTimeoutMs} optionsMs={state.autoLockOptionsMs} onChange={(ms) => view.selectAutoLock(ms)} />
      <section className="flex flex-col gap-2" aria-label="백업">
        <Button
          ref={exportButton}
          variant="outline"
          size="sm"
          className={ENTRY_BUTTON}
          icon={<Download aria-hidden className="size-3.5 shrink-0" />}
          disabled={!state.canExportBackup}
          onClick={() => view.startExport()}
        >
          백업 내보내기
        </Button>
        <Button
          ref={restoreButton}
          variant="outline"
          size="sm"
          className={ENTRY_BUTTON}
          icon={<ArchiveRestore aria-hidden className="size-3.5 shrink-0" />}
          disabled={!state.canRestoreBackup}
          onClick={() => view.startRestore()}
        >
          백업에서 복원
        </Button>
        {state.modal === 'exportConfirm' && (
          <div
            onKeyDown={(event) => {
              if (event.key !== 'Escape') return
              event.preventDefault()
              view.cancelModal()
            }}
          >
            <ConfirmPrompt
              tone="notice"
              title={state.exportNotice.title}
              body={state.exportNotice.body}
              confirmLabel={state.exportNotice.confirm}
              cancelLabel={state.exportNotice.cancel}
              pending={state.inProgress}
              onConfirm={() => view.confirmExport()}
              onCancel={() => view.cancelModal()}
            />
          </div>
        )}
        {state.modal === 'restorePassword' && <RestoreDialog view={view} state={state} />}
      </section>

      <section className="flex flex-col gap-2" aria-label="계정 관리">
        {/* 오프라인이어도 눌러 안내를 받아야 하므로, 켜고 끄는 판단은 State(canStart...)가 한다 */}
        <Button
          ref={changePasswordButton}
          variant="outline"
          size="sm"
          className={ENTRY_BUTTON}
          icon={<KeyRound aria-hidden className="size-3.5 shrink-0" />}
          disabled={!state.canStartChangePassword}
          onClick={() => view.startChangePassword()}
        >
          {state.changePasswordDialog.title}
        </Button>
        <Button
          ref={deleteButton}
          variant="outline"
          size="sm"
          className={`${ENTRY_BUTTON} text-destructive border-destructive/40 hover:bg-destructive/10`}
          icon={<Trash2 aria-hidden className="size-3.5 shrink-0" />}
          disabled={!state.canStartDeleteAccount}
          onClick={() => view.startDeleteAccount()}
        >
          {state.deleteAccountDialog.title}
        </Button>
      </section>

      {showsErrorBelow && <Alert>{state.error}</Alert>}
      {state.notice !== null && <Alert variant={state.noticeKind === 'error' ? 'error' : 'info'}>{state.notice}</Alert>}

      {state.modal === 'changePassword' && <ChangePasswordDialog view={view} state={state} />}
      {state.modal === 'deleteAccount' && <DeleteAccountDialog view={view} state={state} />}
    </div>
  )
}
