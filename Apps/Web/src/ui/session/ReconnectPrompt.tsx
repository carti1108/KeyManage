// 세션 없이 연 볼트에서 마스터 비밀번호를 다시 입력받아 로그인한다 (SEC-003, ADR-0019)
// 인증 해시를 메모리에 남기지 않으므로 다시 연결돼도 비밀번호를 한 번 더 받아야 동기화할 수 있다
import { RefreshCw } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { PasswordField } from '../components/PasswordField'
import { RECONNECT_PROMPT } from '../messages/vaultNotices'
import { useSave } from '../vault/useSave'

export function ReconnectPrompt({ email, onReconnect }: { email: string; onReconnect: (password: string) => Promise<void> }) {
  const [password, setPassword] = useState('')
  const action = useSave()

  function submit(event: FormEvent) {
    event.preventDefault()
    // 성공하든 실패하든 입력한 비밀번호를 칸에 남기지 않는다
    void action.run(() => onReconnect(password)).then(() => setPassword(''))
  }

  return (
    <form className="flex flex-col gap-3 rounded-md border border-border p-3" onSubmit={submit} noValidate>
      <p className="text-sm">{RECONNECT_PROMPT.body}</p>
      {/* 비밀번호 관리자가 계정을 알아보도록 숨은 사용자 이름을 둔다 */}
      <input type="email" autoComplete="username" value={email} readOnly hidden />
      <PasswordField label="마스터 비밀번호" autoComplete="current-password" value={password} onTextChange={setPassword} />
      {action.error && <Alert title={action.error.title}>{action.error.body}</Alert>}
      <Button type="submit" size="sm" loading={action.pending} icon={<RefreshCw aria-hidden className="size-4" />} className="self-start">
        {RECONNECT_PROMPT.action}
      </Button>
    </form>
  )
}
