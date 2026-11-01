// 세션 없이 연 볼트에서 마스터 비밀번호를 다시 입력받아 로그인한다 (SEC-003, ADR-0019)
// 인증 해시를 메모리에 남기지 않으므로 다시 연결돼도 비밀번호를 한 번 더 받아야 동기화할 수 있다
import { RefreshCw } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Button } from '../components/Button'
import { PasswordField } from '../components/PasswordField'
import { RECONNECT_PROMPT } from '../messages/vaultNotices'
import { SessionFeedback } from './SessionFeedback'
import type { RollbackOption } from './sessionPort'
import { useSessionAction } from './useSessionAction'

export interface ReconnectPromptProps {
  email: string
  onReconnect: (password: string, options: RollbackOption) => Promise<void>
}

export function ReconnectPrompt({ email, onReconnect }: ReconnectPromptProps) {
  const [password, setPassword] = useState('')
  const action = useSessionAction()

  function submit(event: FormEvent) {
    event.preventDefault()
    // 되돌림을 받아들일 때 다시 쓰도록 요청에 담고, 입력 칸에는 남기지 않는다
    const entered = password
    setPassword('')
    void action.run((options) => onReconnect(entered, options))
  }

  return (
    <form className="flex flex-col gap-3 rounded-md border border-border p-3" onSubmit={submit} noValidate>
      <p className="text-sm">{RECONNECT_PROMPT.body}</p>
      {/* 비밀번호 관리자가 계정을 알아보도록 숨은 사용자 이름을 둔다 */}
      <input type="email" autoComplete="username" value={email} readOnly hidden />
      <PasswordField label="마스터 비밀번호" autoComplete="current-password" value={password} onTextChange={setPassword} />
      <SessionFeedback action={action} />
      <Button type="submit" size="sm" loading={action.pending} icon={<RefreshCw aria-hidden className="size-4" />} className="self-start">
        {RECONNECT_PROMPT.action}
      </Button>
    </form>
  )
}
