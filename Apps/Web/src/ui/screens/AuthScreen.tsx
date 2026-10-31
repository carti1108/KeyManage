// 로그인·가입 화면 (SEC-002)
import { useState, type FormEvent } from 'react'
import { checkNewMasterPassword, type NewMasterPasswordProblem } from '../../domain/auth/masterPassword'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PasswordField } from '../components/PasswordField'
import { TextField } from '../components/TextField'
import type { RollbackOption } from '../session/sessionPort'
import { SessionFeedback } from '../session/SessionFeedback'
import { useSessionAction } from '../session/useSessionAction'

const PASSWORD_PROBLEMS: Record<NewMasterPasswordProblem, string> = {
  tooShort: '마스터 비밀번호는 12자 이상이어야 합니다.',
  mismatch: '비밀번호 확인이 일치하지 않습니다.',
}

export interface AuthScreenProps {
  /** 로그인 화면으로 돌아온 이유(세션 만료 등) */
  notice?: string | null
  onLogin: (email: string, password: string, options: RollbackOption) => Promise<void>
  onRegister: (email: string, password: string) => Promise<void>
}

export function AuthScreen({ notice, onLogin, onRegister }: AuthScreenProps) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const action = useSessionAction()
  const registering = mode === 'register'

  function submit(event: FormEvent) {
    event.preventDefault()
    if (registering) {
      const problem = checkNewMasterPassword(password, confirmation)
      if (problem) return action.showError({ body: PASSWORD_PROBLEMS[problem] })
      void action.run(() => onRegister(email, password))
    } else {
      void action.run((options) => onLogin(email, password, options))
    }
  }

  function switchMode() {
    setMode(registering ? 'login' : 'register')
    setConfirmation('')
    action.showError(null)
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
          <PasswordField
            label="마스터 비밀번호"
            autoComplete={registering ? 'new-password' : 'current-password'}
            value={password}
            onTextChange={setPassword}
            required
          />
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
