import { useEffect, useState } from 'react'
import { requestPersistentStorage } from '../adapters/browser/indexedDbDeviceStore'
import { SessionRoot } from '../ui/session/SessionRoot'
import { startClipboardBurn } from './clipboardWiring'
import { startPendingSync } from './pendingSyncWiring'
import { createServices, type Services } from './services'
import { useAutoLockSetting } from './useAutoLockSetting'
import { useNetworkOffline } from './useNetworkOffline'
import { usePageHidden } from './usePageHidden'

// 조립 지점(Composition Root): 화면은 ui에 두고, 여기서는 어댑터를 만들어 포트로 화면에 넘기는 일만 한다 (ADR-0004)
function App({ services }: { services?: Services }) {
  const [{ session, clipboard, tabs, autoLockSetting, backupFiles }] = useState(() => services ?? createServices())
  const pageHidden = usePageHidden()
  // 볼트가 열린 동안 브라우저가 끊기면 오프라인 경고를 보인다. 재연결 재전송은 startPendingSync가 맡는다 (VLT-002)
  const networkOffline = useNetworkOffline()
  // 기기 기록을 읽기 전에 로그인 화면을 그리지 않는다. 기록이 있으면 잠금 화면으로 시작한다 (SEC-003, ADR-0019)
  const [restored, setRestored] = useState(false)
  // 이 탭이 연 뒤 다른 탭에서 비밀번호가 바뀌었으면 로그인 화면에 알린다 (SEC-003 규칙 11)
  const [restoreError, setRestoreError] = useState<unknown>(null)

  useEffect(() => {
    let active = true
    requestPersistentStorage()
    session
      .restore()
      .catch((error: unknown) => {
        // 기기 기록을 읽지 못하면 로그인 화면에서 시작한다. 쓰기 실패는 쓸 때 알린다 (VLT-003)
        if (active) setRestoreError(error)
      })
      .finally(() => {
        if (active) setRestored(true)
      })
    return () => {
      active = false
    }
  }, [session])

  // 볼트가 열려 있는 동안만 자동 잠금 타이머가 돈다. 시간은 기기에 저장한 설정을 쓰고, 바꾸면 새 시간으로 다시 잰다 (SEC-003, ADR-WEB-002)
  // 다른 탭과 조작을 나눠 함께 연장하고, 보이는 탭의 시간 초과만 모든 탭에 퍼뜨린다 (ADR-WEB-001)
  const autoLock = useAutoLockSetting(session, tabs, autoLockSetting)
  // 잠기면 바로, 탭으로 돌아오면 비우지 못한 클립보드를 비운다 (COM-001)
  useEffect(() => startClipboardBurn(session, clipboard), [session, clipboard])
  // 서버에 올리지 못한 변경 사항을 재연결·30초마다 다시 보내고, 대기 중에 탭을 떠나면 확인을 받는다 (VLT-002)
  useEffect(() => startPendingSync(session, tabs), [session, tabs])

  if (!restored) return null
  return (
    <SessionRoot
      session={session}
      clipboard={clipboard}
      pageHidden={pageHidden}
      networkOffline={networkOffline}
      autoLock={{ timeoutMs: autoLock.timeoutMs, onChange: autoLock.change }}
      backupFiles={backupFiles}
      restoreError={restoreError}
    />
  )
}

export default App
