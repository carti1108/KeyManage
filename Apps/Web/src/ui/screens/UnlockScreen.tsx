// 잠금 화면 (SEC-003). 웹은 생체 인증이 없어 마스터 비밀번호로만 연다
import { Lock } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { PasswordField } from '../components/PasswordField'
import type { RollbackOption } from '../session/sessionPort'
import { SessionFeedback } from '../session/SessionFeedback'
import { useSessionAction } from '../session/useSessionAction'

export interface UnlockScreenProps {
  email: string
  onUnlock: (password: string, options: RollbackOption) => Promise<void>
  onLogout: () => Promise<void>
}

export function UnlockScreen({ email, onUnlock, onLogout }: UnlockScreenProps) {
  const [password, setPassword] = useState('')
  const action = useSessionAction()

  function submit(event: FormEvent) {
    event.preventDefault()
    void action.run((options) => onUnlock(password, options))
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-4">
      <Card padding="lg" className="flex w-full max-w-sm flex-col gap-4">
        <header className="flex flex-col items-center gap-2 text-center">
          <Lock aria-hidden className="size-8 text-primary" />
          <h1 className="text-xl font-bold">볼트가 잠겼습니다</h1>
          <p className="text-sm text-muted-foreground">{email}</p>
        </header>
        <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
          {/* 비밀번호 관리자가 계정을 알아보도록 숨은 사용자 이름을 둔다 */}
          <input type="email" autoComplete="username" value={email} readOnly hidden />
          <PasswordField
            label="마스터 비밀번호"
            autoComplete="current-password"
            value={password}
            onTextChange={setPassword}
            autoFocus
          />
          <SessionFeedback action={action} />
          <Button type="submit" loading={action.pending}>
            잠금 해제
          </Button>
        </form>
        <Button variant="ghost" size="sm" onClick={() => void onLogout()} disabled={action.pending}>
          로그아웃
        </Button>
      </Card>
    </main>
  )
}
