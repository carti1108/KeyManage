// 로그인·가입 화면 (SEC-001, SEC-002)
import { useId, useState, type FormEvent } from 'react'
import { checkNewMasterPassword } from '../../domain/auth/masterPassword'
import { isWeak, scorePassword } from '../../domain/generator/passwordStrength'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { EntropyGauge } from '../components/EntropyGauge'
import { PasswordField } from '../components/PasswordField'
import { TextField } from '../components/TextField'
import { MASTER_PASSWORD_SETUP, NEW_PASSWORD_PROBLEMS, WEAK_MASTER_PASSWORD_WARNING } from '../messages/authNotices'
import type { RollbackOption } from '../session/sessionPort'
import { SessionFeedback } from '../session/SessionFeedback'
import { useSessionAction } from '../session/useSessionAction'

export interface AuthScreenProps {
  /** 로그인 화면으로 돌아온 이유(세션 만료 등) */
  notice?: string | null
  /** 사용자가 다시 시도하면 지난 안내를 지운다 */
  onDismissNotice?: () => void
  onLogin: (email: string, password: string, options: RollbackOption) => Promise<void>
  onRegister: (email: string, password: string) => Promise<void>
}

export function AuthScreen({ notice, onDismissNotice, onLogin, onRegister }: AuthScreenProps) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const action = useSessionAction()
  const registering = mode === 'register'
  const setupHintId = useId()
  const showsStrength = registering && password !== ''
  const score = scorePassword(password)

  function submit(event: FormEvent) {
    event.preventDefault()
    onDismissNotice?.()
    if (registering) {
      const problem = checkNewMasterPassword(password, confirmation)
      if (problem) return action.showError({ body: NEW_PASSWORD_PROBLEMS[problem] })
      void action.run(() => onRegister(email, password))
    } else {
      void action.run((options) => onLogin(email, password, options))
    }
  }

  function switchMode() {
    setMode(registering ? 'login' : 'register')
    setConfirmation('')
    onDismissNotice?.()
    action.reset()
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-4">
      <Card padding="lg" className="flex w-full max-w-sm flex-col gap-4">
        <header className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold">KeyManage</h1>
          <p className="text-sm text-muted-foreground">{registering ? '새 계정 만들기' : '볼트에 로그인'}</p>
        </header>
        {notice && <Alert variant="info">{notice}</Alert>}
        <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
          <TextField
            label="이메일"
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          {registering && (
            // 잃어버리면 복구할 수 없다는 안내를 입력란 설명으로 읽어 준다 (SEC-001)
            <div id={setupHintId}>
              <Alert variant="info" live={false} title={MASTER_PASSWORD_SETUP.title}>
                {MASTER_PASSWORD_SETUP.body}
              </Alert>
            </div>
          )}
          <PasswordField
            label="마스터 비밀번호"
            autoComplete={registering ? 'new-password' : 'current-password'}
            value={password}
            onTextChange={setPassword}
            aria-describedby={registering ? setupHintId : undefined}
            required
          />
          {/* 가입 화면 강도 안내. 취약해도 가입은 막지 않는다 (SEC-002) */}
          {showsStrength && <EntropyGauge score={score} />}
          {registering && (
            <div role="status">
              {showsStrength && isWeak(score) && (
                <p className="text-sm text-muted-foreground">{WEAK_MASTER_PASSWORD_WARNING}</p>
              )}
            </div>
          )}
          {registering && (
            <PasswordField
              label="마스터 비밀번호 확인"
              autoComplete="new-password"
              value={confirmation}
              onTextChange={setConfirmation}
              required
            />
          )}
          <SessionFeedback action={action} />
          <Button type="submit" loading={action.pending}>
            {registering ? '가입' : '로그인'}
          </Button>
        </form>
        <Button variant="ghost" size="sm" onClick={switchMode} disabled={action.pending}>
          {registering ? '이미 계정이 있어요' : '계정 만들기'}
        </Button>
      </Card>
    </main>
  )
}
