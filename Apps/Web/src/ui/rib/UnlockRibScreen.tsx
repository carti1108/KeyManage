// 잠금 화면 (SEC-003). 웹은 생체 인증이 없어 마스터 비밀번호로만 연다. 상태와 흐름은 공유 Kotlin UnlockRib가 맡는다
import { Lock } from 'lucide-react'
import type { WebUnlockView } from 'keymanage-web-session'
import type { FormEvent } from 'react'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ConfirmPrompt } from '../components/ConfirmPrompt'
import { PasswordField } from '../components/PasswordField'
import { PENDING_LOGOUT_CONFIRM, ROLLBACK_WARNING } from '../messages/vaultNotices'
import { useViewStore } from './ribsReact'

export function UnlockRibScreen({ view }: { view: WebUnlockView }) {
  const state = useViewStore(view.store)

  function submit(event: FormEvent) {
    event.preventDefault()
    view.unlock()
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-4">
      <Card padding="lg" className="flex w-full max-w-sm flex-col gap-4">
        <header className="flex flex-col items-center gap-2 text-center">
          <Lock aria-hidden className="size-8 text-primary" />
          <h1 className="text-xl font-bold">볼트가 잠겼습니다</h1>
          <p className="text-sm text-muted-foreground">{state.email}</p>
        </header>
        <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
          {/* 비밀번호 관리자가 계정을 알아보도록 숨은 사용자 이름을 둔다 */}
          <input type="email" autoComplete="username" value={state.email} readOnly hidden />
          <PasswordField
            label="마스터 비밀번호"
            autoComplete="current-password"
            value={state.password}
            onTextChange={(value) => view.updatePassword(value)}
            autoFocus
          />
          {state.isConfirmingRollback ? (
            <ConfirmPrompt
              title={ROLLBACK_WARNING.title}
              body={ROLLBACK_WARNING.body}
              confirmLabel={ROLLBACK_WARNING.accept}
              cancelLabel={ROLLBACK_WARNING.cancel}
              onConfirm={() => view.acceptRollback()}
              onCancel={() => view.cancelRollback()}
            />
          ) : (
            state.error && <Alert title={state.error.title ?? undefined}>{state.error.body}</Alert>
          )}
          <Button type="submit" loading={state.isLoading}>
            잠금 해제
          </Button>
        </form>
        {/* 올리지 못한 변경 사항이 있을 때 로그아웃 전에 묻는다 (VLT-002) */}
        {state.isConfirmingLogout && (
          <ConfirmPrompt
            title={PENDING_LOGOUT_CONFIRM.title}
            body={PENDING_LOGOUT_CONFIRM.body}
            confirmLabel={PENDING_LOGOUT_CONFIRM.confirm}
            cancelLabel={PENDING_LOGOUT_CONFIRM.cancel}
            onConfirm={() => view.confirmLogout()}
            onCancel={() => view.cancelLogout()}
          />
        )}
        <Button variant="ghost" size="sm" onClick={() => view.requestLogout()} disabled={state.isLoading}>
          로그아웃
        </Button>
      </Card>
    </main>
  )
}
