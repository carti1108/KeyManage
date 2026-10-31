// 세션 상태에 따라 로그인·잠금·볼트 화면을 고른다
import { useCallback, useEffect, useState } from 'react'
import { SessionExpiredError } from '../../domain/session/vaultSession'
import { describeError } from '../messages/errorMessage'
import { AuthScreen } from '../screens/AuthScreen'
import { PrivacyCover } from '../screens/PrivacyCover'
import { UnlockScreen } from '../screens/UnlockScreen'
import { VaultHomeScreen } from '../screens/VaultHomeScreen'
import type { ClipboardPort } from '../clipboard/clipboardPort'
import { ClipboardNotice } from '../clipboard/ClipboardNotice'
import { VaultScreen } from '../vault/VaultScreen'
import type { RollbackOption, SessionPort } from './sessionPort'

export interface SessionRootProps {
  session: SessionPort
  /** 복사하고 30초 뒤 비우는 클립보드 (COM-001) */
  clipboard: ClipboardPort
  /** 탭이 백그라운드로 감 */
  pageHidden: boolean
}

export function SessionRoot({ session, clipboard, pageHidden }: SessionRootProps) {
  // 잠기면 새 상태로 바꿔 평문 볼트를 들고 있던 이전 상태를 놓는다
  const [state, setState] = useState(() => session.state())
  const [notice, setNotice] = useState<string | null>(null)
  const [offline, setOffline] = useState(false)
  const refresh = useCallback(() => setState(session.state()), [session])

  useEffect(() => {
    const offLock = session.onLock(refresh)
    const offUnlock = session.onUnlock(refresh)
    return () => {
      offLock()
      offUnlock()
    }
  }, [session, refresh])

  async function logout() {
    // 세션은 이 기기 정리를 먼저 끝내고 서버에 토큰 폐기를 요청한다. 서버 응답을 기다리지 않고 화면을 바꾼다
    const request = session.logout()
    setNotice(null)
    refresh()
    try {
      await request
    } catch {
      // 서버 토큰 폐기만 실패했다. 이 기기는 이미 로그아웃됐고 서버 토큰은 만료(30일)를 기다린다 (SEC-002)
    }
  }

  return (
    <>
      {/* 가려진 동안에는 보조 기술과 키보드도 화면에 닿지 않게 한다 */}
      <div inert={pageHidden} aria-hidden={pageHidden || undefined}>
        {renderScreen()}
      </div>
      {/* 볼트뿐 아니라 평문으로 펼친 마스터 비밀번호도 앱 전환 화면에 비치지 않게 모든 화면을 덮는다 (SEC-003) */}
      {pageHidden && <PrivacyCover />}
    </>
  )

  function renderScreen() {
    if (state.status === 'signedOut') {
      return (
        <AuthScreen
          notice={notice}
          onDismissNotice={() => setNotice(null)}
          onLogin={async (email, password, options) => {
            await session.login(email, password, options)
            setNotice(null)
            setOffline(false)
            refresh()
          }}
          onRegister={async (email, password) => {
            await session.register(email, password)
            setNotice(null)
            setOffline(false)
            refresh()
          }}
        />
      )
    }

    if (state.status === 'locked') {
      return (
        <UnlockScreen
          email={state.email}
          onUnlock={async (password, options: RollbackOption) => {
            try {
              const result = await session.unlock(password, options)
              setOffline(result.offline)
            } catch (error) {
              if (!(error instanceof SessionExpiredError)) throw error
              setNotice(describeError(error).body)
            }
            refresh()
          }}
          onLogout={logout}
        />
      )
    }

    return (
      <VaultHomeScreen
        email={state.email}
        readOnly={state.vault.readOnly}
        offline={offline}
        onLock={() => session.lock()}
        onLogout={() => void logout()}
      >
        <ClipboardNotice clipboard={clipboard} />
        <VaultScreen
          vault={state.vault}
          clipboard={clipboard}
          onSave={async (data) => {
            await session.save(data)
            // 저장은 잠금 알림을 보내지 않으므로 직접 다시 읽는다
            refresh()
          }}
        />
      </VaultHomeScreen>
    )
  }
}
