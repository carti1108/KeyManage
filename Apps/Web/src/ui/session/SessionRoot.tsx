// 세션 상태에 따라 로그인·잠금·볼트 화면을 고른다
import { useCallback, useEffect, useState } from 'react'
import { SessionExpiredError } from '../../domain/session/vaultSession'
import { describeError } from '../messages/errorMessage'
import { AuthScreen } from '../screens/AuthScreen'
import { PrivacyCover } from '../screens/PrivacyCover'
import { UnlockScreen } from '../screens/UnlockScreen'
import { VaultHomeScreen } from '../screens/VaultHomeScreen'
import type { RollbackOption, SessionPort } from './sessionPort'

export interface SessionRootProps {
  session: SessionPort
  /** 탭이 백그라운드로 감 */
  pageHidden: boolean
}

export function SessionRoot({ session, pageHidden }: SessionRootProps) {
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
    try {
      await session.logout()
    } catch {
      // 이 기기의 세션은 이미 지워졌다. 서버 토큰 폐기만 실패한 것이라 만료(30일)를 기다린다 (SEC-002)
    } finally {
      setNotice(null)
      refresh()
    }
  }

  if (state.status === 'signedOut') {
    return (
      <AuthScreen
        notice={notice}
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
    <>
      {/* 가려진 동안에는 보조 기술과 키보드도 볼트 화면에 닿지 않게 한다 */}
      <div inert={pageHidden} aria-hidden={pageHidden || undefined}>
        <VaultHomeScreen
          email={state.email}
          itemCount={state.vault.data.items.length}
          readOnly={state.vault.readOnly}
          offline={offline}
          onLock={() => session.lock()}
          onLogout={() => void logout()}
        />
      </div>
      {pageHidden && <PrivacyCover />}
    </>
  )
}
