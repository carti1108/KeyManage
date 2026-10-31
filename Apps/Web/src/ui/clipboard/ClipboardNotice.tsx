// 복사한 값이 클립보드에서 지워지기까지 남은 시간 (COM-001)
import { useEffect, useState, useSyncExternalStore } from 'react'
import { Alert } from '../components/Alert'
import { BurnTimerBadge } from '../components/BurnTimerBadge'
import { Button } from '../components/Button'
import type { ClipboardPort } from './clipboardPort'

const TICK_MS = 1_000

export interface ClipboardNoticeProps {
  clipboard: Pick<ClipboardPort, 'clear' | 'state' | 'subscribe'>
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

  // 알림 영역은 늘 두고 안의 내용만 바꾼다. 내용과 함께 새로 생긴 영역은 화면 낭독기가 읽지 않는 경우가 있다
  return <div role="status">{renderContent()}</div>

  function renderContent() {
    if (state.status === 'pendingBurn') {
      // 비우기 버튼은 사용자 조작이라 Safari처럼 조작 없이는 쓰기를 막는 브라우저에서도 비울 수 있다
      return (
        <Alert
          title="클립보드 보안 소각"
          live={false}
          actions={
            <Button size="sm" variant="destructive" onClick={() => void clipboard.clear()}>
              지금 비우기
            </Button>
          }
        >
          클립보드를 아직 비우지 못했습니다. 이 화면을 누르면 다시 비웁니다.
        </Alert>
      )
    }
    if (state.status !== 'counting') return null
    const remaining = Math.max(0, Math.ceil((state.expiresAt - now()) / TICK_MS))
    return (
      <Alert variant="info" title="클립보드 보안 소각" live={false} actions={<BurnTimerBadge remainingSeconds={remaining} />}>
        보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다.
      </Alert>
    )
  }
}
