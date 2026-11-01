import { useEffect, useState } from 'react'
import { requestPersistentStorage } from '../adapters/browser/indexedDbDeviceStore'
import { DEFAULT_AUTO_LOCK_MS } from '../domain/lock/autoLock'
import { SessionRoot } from '../ui/session/SessionRoot'
import { startAutoLock } from './autoLockWiring'
import { startClipboardBurn } from './clipboardWiring'
import { startPendingSync } from './pendingSyncWiring'
import { createServices, type Services } from './services'
import { usePageHidden } from './usePageHidden'

// 조립 지점(Composition Root): 화면은 ui에 두고, 여기서는 어댑터를 만들어 포트로 화면에 넘기는 일만 한다 (ADR-0004)
function App({ services }: { services?: Services }) {
  const [{ session, clipboard, tabs }] = useState(() => services ?? createServices())
  const pageHidden = usePageHidden()
  // 기기 기록을 읽기 전에 로그인 화면을 그리지 않는다. 기록이 있으면 잠금 화면으로 시작한다 (SEC-003, ADR-0019)
  const [restored, setRestored] = useState(false)

  useEffect(() => {
    let active = true
    requestPersistentStorage()
    session
      .restore()
      .catch(() => {
        // 기기 기록을 읽지 못하면 로그인 화면에서 시작한다. 쓰기 실패는 쓸 때 알린다 (VLT-003)
      })
      .finally(() => {
        if (active) setRestored(true)
      })
    return () => {
      active = false
    }
  }, [session])

  // 볼트가 열려 있는 동안만 자동 잠금 타이머가 돈다. 설정 화면이 생기기 전까지는 기본 5분 (SEC-003)
  // 다른 탭과 조작을 나눠 함께 연장하고, 보이는 탭의 시간 초과만 모든 탭에 퍼뜨린다 (ADR-WEB-001)
  useEffect(() => startAutoLock(session, DEFAULT_AUTO_LOCK_MS, tabs), [session, tabs])
  // 잠기면 바로, 탭으로 돌아오면 비우지 못한 클립보드를 비운다 (COM-001)
  useEffect(() => startClipboardBurn(session, clipboard), [session, clipboard])
  // 서버에 올리지 못한 변경 사항을 재연결·30초마다 다시 보내고, 대기 중에 탭을 떠나면 확인을 받는다 (VLT-002)
  useEffect(() => startPendingSync(session), [session])

  if (!restored) return null
  return <SessionRoot session={session} clipboard={clipboard} pageHidden={pageHidden} />
}

export default App
