import { useEffect } from 'react'
import { ClipboardNotice } from '../ui/clipboard/ClipboardNotice'
import { AuthRibScreen } from '../ui/rib/AuthRibScreen'
import { MainRibScreen } from '../ui/rib/MainRibScreen'
import { RibSlot } from '../ui/rib/ribsReact'
import { UnlockRibScreen } from '../ui/rib/UnlockRibScreen'
import { PrivacyCover } from '../ui/screens/PrivacyCover'
import { startClipboardBurn } from './clipboardWiring'
import type { KotlinApp } from './kotlinApp'
import { startPendingSync } from './pendingSyncWiring'
import { useAutoLockSetting } from './useAutoLockSetting'
import { usePageHidden } from './usePageHidden'

// 조립 지점(Composition Root): 화면 상태는 공유 RIB(Kotlin)이 정하고, React는 rootSlot의 화면을 그린다 (ADR-0004, domain-migration-plan 11.4)
// 앱 수명주기 배선(자동 잠금·클립보드 소각·대기 재전송·탭 가림)은 RIB이 아니라 여기서 같은 세션에 붙인다
const rootScreens = {
  auth: (view: Parameters<typeof AuthRibScreen>[0]['view']) => <AuthRibScreen view={view} />,
  unlock: (view: Parameters<typeof UnlockRibScreen>[0]['view']) => <UnlockRibScreen view={view} />,
  main: (view: Parameters<typeof MainRibScreen>[0]['view']) => <MainRibScreen view={view} />,
}

function App({ kotlinApp }: { kotlinApp: KotlinApp }) {
  const { app, session, tabs, clipboard, autoLockSetting } = kotlinApp
  const pageHidden = usePageHidden()

  // 볼트가 열려 있는 동안만 자동 잠금 타이머가 돈다. 설정 화면(Kotlin)이 바꾼 시간도 따라간다 (SEC-003, ADR-WEB-001, ADR-WEB-002)
  useAutoLockSetting(session, tabs, autoLockSetting)
  // 잠기면 바로, 탭으로 돌아오면 비우지 못한 클립보드를 비운다 (COM-001)
  useEffect(() => startClipboardBurn(session, clipboard), [session, clipboard])
  // 서버에 올리지 못한 변경 사항을 재연결·30초마다 다시 보내고, 대기 중에 탭을 떠나면 확인을 받는다 (VLT-002)
  useEffect(() => startPendingSync(session, tabs), [session, tabs])

  return (
    <>
      {/* 가려진 동안에는 보조 기술과 키보드도 화면에 닿지 않게 한다 */}
      <div inert={pageHidden} aria-hidden={pageHidden || undefined}>
        <RibSlot slot={app.rootSlot} screens={rootScreens} />
        <ClipboardNotice clipboard={clipboard} />
      </div>
      {/* 볼트뿐 아니라 평문으로 펼친 마스터 비밀번호도 앱 전환 화면에 비치지 않게 모든 화면을 덮는다 (SEC-003) */}
      {pageHidden && <PrivacyCover />}
    </>
  )
}

export default App
