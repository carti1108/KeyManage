import { useEffect, useState } from 'react'
import { SessionRoot } from '../ui/session/SessionRoot'
import { startAutoLock } from './autoLockWiring'
import { startClipboardBurn } from './clipboardWiring'
import { startPendingSync } from './pendingSyncWiring'
import { createServices, type Services } from './services'
import { usePageHidden } from './usePageHidden'

// 조립 지점(Composition Root): 화면은 ui에 두고, 여기서는 어댑터를 만들어 포트로 화면에 넘기는 일만 한다 (ADR-0004)
function App({ services }: { services?: Services }) {
  const [{ session, clipboard }] = useState(() => services ?? createServices())
  const pageHidden = usePageHidden()

  // 볼트가 열려 있는 동안만 자동 잠금 타이머가 돈다. 설정 화면이 생기기 전까지는 기본 5분 (SEC-003)
  useEffect(() => startAutoLock(session), [session])
  // 잠기면 바로, 탭으로 돌아오면 비우지 못한 클립보드를 비운다 (COM-001)
  useEffect(() => startClipboardBurn(session, clipboard), [session, clipboard])
  // 서버에 올리지 못한 변경 사항을 재연결·30초마다 다시 보내고, 대기 중에 탭을 떠나면 확인을 받는다 (VLT-001)
  useEffect(() => startPendingSync(session), [session])

  return <SessionRoot session={session} clipboard={clipboard} pageHidden={pageHidden} />
}

export default App
