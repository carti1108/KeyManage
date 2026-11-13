// 로그인·가입 화면 (SEC-001, SEC-002). 상태와 문구 결정은 공유 Kotlin AuthRib가 맡고, 여기서는 그리고 입력만 보낸다
import type { WebAuthView } from 'keymanage-web-session'
import { useId, type FormEvent } from 'react'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ConfirmPrompt } from '../components/ConfirmPrompt'
import { EntropyGauge } from '../components/EntropyGauge'
import { PasswordField } from '../components/PasswordField'
import { TextField } from '../components/TextField'
import { useViewStore } from './ribsReact'

export function AuthRibScreen({ view }: { view: WebAuthView }) {
  const state = useViewStore(view.store)
  const setupHintId = useId()
  const registering = state.isRegistering

  function submit(event: FormEvent) {
    event.preventDefault()
    view.submit()
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-4">
      <Card padding="lg" className="flex w-full max-w-sm flex-col gap-4">
        <header className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold">KeyManage</h1>
          <p className="text-sm text-muted-foreground">{registering ? '새 계정 만들기' : '볼트에 로그인'}</p>
        </header>
        {state.notice && <Alert variant="info">{state.notice}</Alert>}
        <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
          <TextField
            label="이메일"
            type="email"
            autoComplete="username"
            value={state.email}
            onChange={(e) => view.updateEmail(e.target.value)}
            required
          />
          {registering && (
            // 잃어버리면 복구할 수 없다는 안내를 입력란 설명으로 읽어 준다 (SEC-001)
            <div id={setupHintId}>
              <Alert variant="info" live={false} title={state.masterPasswordSetup.title ?? undefined}>
                {state.masterPasswordSetup.body}
              </Alert>
            </div>
          )}
          <PasswordField
            label="마스터 비밀번호"
            autoComplete={registering ? 'new-password' : 'current-password'}
            value={state.password}
            onTextChange={(value) => view.updatePassword(value)}
            aria-describedby={registering ? setupHintId : undefined}
            required
          />
          {/* 가입 화면 강도 안내. 취약해도 가입은 막지 않는다 (SEC-002) */}
          {state.showsStrengthGauge && <EntropyGauge score={state.passwordStrengthScore} />}
          {registering && (
            <div role="status">
              {state.showsWeakPasswordWarning && (
                <p className="text-sm text-muted-foreground">{state.weakPasswordWarning}</p>
              )}
            </div>
          )}
          {registering && (
            <PasswordField
              label="마스터 비밀번호 확인"
              autoComplete="new-password"
              value={state.confirmation}
              onTextChange={(value) => view.updateConfirmation(value)}
              required
            />
          )}
          {state.isConfirmingRollback ? (
            <ConfirmPrompt
              title={state.rollbackWarning.title}
              body={state.rollbackWarning.body}
              confirmLabel={state.rollbackWarning.confirm}
              cancelLabel={state.rollbackWarning.cancel}
              onConfirm={() => view.acceptRollback()}
              onCancel={() => view.cancelRollback()}
            />
          ) : (
            state.error && <Alert title={state.error.title ?? undefined}>{state.error.body}</Alert>
          )}
          <Button type="submit" loading={state.isLoading}>
            {registering ? '가입' : '로그인'}
          </Button>
        </form>
        <Button variant="ghost" size="sm" onClick={() => view.switchMode()} disabled={state.isLoading}>
          {registering ? '이미 계정이 있어요' : '계정 만들기'}
        </Button>
      </Card>
    </main>
  )
}
