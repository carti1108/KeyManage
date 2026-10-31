// 복사한 값이 클립보드에서 지워지기까지 남은 시간 (COM-001)
import { useEffect, useState, useSyncExternalStore } from 'react'
import { Alert } from '../components/Alert'
import { BurnTimerBadge } from '../components/BurnTimerBadge'
import type { ClipboardPort } from './clipboardPort'

const TICK_MS = 1_000

export interface ClipboardNoticeProps {
  clipboard: Pick<ClipboardPort, 'state' | 'subscribe'>
  now?: () => number
}

export function ClipboardNotice({ clipboard, now = () => Date.now() }: ClipboardNoticeProps) {
  const state = useSyncExternalStore(clipboard.subscribe, clipboard.state)
  const [, setTick] = useState(0)

  // 남은 초를 다시 계산하도록 세는 동안만 1초마다 다시 그린다
  useEffect(() => {
    if (state.status !== 'counting') return
    const id = window.setInterval(() => setTick((n) => n + 1), TICK_MS)
    return () => window.clearInterval(id)
  }, [state])

  if (state.status !== 'counting') return null
  const remaining = Math.max(0, Math.ceil((state.expiresAt - now()) / TICK_MS))

  return (
    <Alert variant="info" title="클립보드 보안 소각" actions={<BurnTimerBadge remainingSeconds={remaining} />}>
      보안을 위해 복사된 비밀번호가 30초 후 클립보드에서 자동 삭제됩니다.
    </Alert>
  )
}
