// 설정 화면 (Kotlin SettingsRib의 WebSettingsView). 계정 정보·자동 잠금·백업·비밀번호 변경·계정 삭제·세션 제어를 그린다
import { ArchiveRestore, Download, Globe, KeyRound, Lock, LogOut, Shield, Trash2, User } from 'lucide-react'
import { useEffect, useRef, type ReactNode, type RefObject } from 'react'
import type { WebSettingsView } from 'keymanage-web-session'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ConfirmPrompt } from '../components/ConfirmPrompt'
import { AutoLockSelect } from '../vault/AutoLockSelect'
import { ChangePasswordDialog, DeleteAccountDialog, RestoreDialog } from './SettingsRibDialogs'
import { useViewStore } from './ribsReact'

const ENTRY_BUTTON = 'w-full justify-start gap-2 h-7 px-2 text-xs font-normal whitespace-nowrap'

function SettingsSection({ title, icon, children }: { title: string; icon: ReactNode; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
        {icon}
        <h3>{title}</h3>
      </div>
      <div className="rounded-lg border border-border bg-card divide-y divide-border text-sm">{children}</div>
    </div>
  )
}

function SettingsRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-center justify-between p-3.5 gap-4">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium text-foreground truncate">{value}</span>
    </div>
  )
}

export function SettingsRibScreen({ view }: { view: WebSettingsView }) {
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
  const serverAddress = state.serverAddress === '' ? window.location.origin : state.serverAddress

  return (
    <section aria-labelledby="settings-heading" className="flex flex-col gap-6">
      <div>
        <h2 id="settings-heading" className="text-xl font-bold">
          설정
        </h2>
        <p className="text-sm text-muted-foreground">계정 및 보안 정책 상태를 확인합니다.</p>
      </div>

      <div className="flex flex-col gap-4">
        <SettingsSection title="계정" icon={<User aria-hidden className="size-4 text-primary" />}>
          <SettingsRow label="이메일" value={state.email} />
          <SettingsRow label="동기화 상태" value={state.syncStatus} />
        </SettingsSection>

        <SettingsSection title="보안 정책" icon={<Shield aria-hidden className="size-4 text-primary" />}>
          <div className="p-3.5">
            <AutoLockSelect timeoutMs={state.autoLockTimeoutMs} optionsMs={state.autoLockOptionsMs} onChange={(ms) => view.selectAutoLock(ms)} />
          </div>
          <SettingsRow label={state.securityPolicy.clipboardLabel} value={state.securityPolicy.clipboardValue} />
          <SettingsRow label={state.securityPolicy.encryptionLabel} value={state.securityPolicy.encryptionValue} />
        </SettingsSection>

        <SettingsSection title="서버" icon={<Globe aria-hidden className="size-4 text-primary" />}>
          <SettingsRow label="서버 주소" value={serverAddress} />
        </SettingsSection>

        <Card className="p-4 flex flex-col gap-3 bg-muted/20">
          <section className="flex flex-col gap-1.5" aria-label="백업">
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

          <section className="flex flex-col gap-1.5" aria-label="계정 관리">
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
        </Card>

        <Card className="p-4 flex flex-col sm:flex-row items-center justify-between gap-3 bg-muted/20">
          <div>
            <h4 className="text-sm font-medium">세션 제어</h4>
            <p className="text-xs text-muted-foreground">볼트를 잠그거나 계정에서 로그아웃합니다.</p>
          </div>
          <div className="flex gap-2 w-full sm:w-auto">
            <Button variant="outline" size="sm" className="flex-1 sm:flex-initial" icon={<Lock aria-hidden className="size-4" />} onClick={() => view.requestLock()}>
              잠그기
            </Button>
            <Button variant="destructive" size="sm" className="flex-1 sm:flex-initial" icon={<LogOut aria-hidden className="size-4" />} onClick={() => view.requestLogout()}>
              로그아웃
            </Button>
          </div>
        </Card>
      </div>

      {state.modal === 'changePassword' && <ChangePasswordDialog view={view} state={state} />}
      {state.modal === 'deleteAccount' && <DeleteAccountDialog view={view} state={state} />}
    </section>
  )
}
