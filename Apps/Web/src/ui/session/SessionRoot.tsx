// 세션 상태에 따라 로그인·잠금·볼트 화면을 고른다
import { useCallback, useEffect, useRef, useState } from 'react'
import { MasterPasswordChangedError } from '../../domain/auth/authService'
import { DeviceStorageError } from '../../domain/device/deviceRecord'
import { SessionExpiredError } from '../../domain/session/vaultSession'
import { ConfirmPrompt } from '../components/ConfirmPrompt'
import { Alert } from '../components/Alert'
import { describeError, type ErrorMessage } from '../messages/errorMessage'
import { DEVICE_STORAGE_FAILED, LOGOUT_CLEAR_FAILED, PENDING_LOGOUT_CONFIRM } from '../messages/vaultNotices'
import { AuthScreen } from '../screens/AuthScreen'
import { PrivacyCover } from '../screens/PrivacyCover'
import { UnlockScreen } from '../screens/UnlockScreen'
import { VaultHomeScreen } from '../screens/VaultHomeScreen'
import type { ClipboardPort } from '../clipboard/clipboardPort'
import { ClipboardNotice } from '../clipboard/ClipboardNotice'
import { VaultScreen } from '../vault/VaultScreen'
import { ReconnectPrompt } from './ReconnectPrompt'
import type { RollbackOption, SessionPort } from './sessionPort'

/** 로그인 화면으로 돌려보내며 이유를 알려야 하는 오류 (SEC-002, SEC-003) */
function sendsToSignIn(error: unknown): boolean {
  return error instanceof SessionExpiredError || error instanceof MasterPasswordChangedError
}

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
  // 대기 내용 재전송이 분명한 거절을 받았을 때의 문구 (VLT-002)
  const [syncError, setSyncError] = useState<ErrorMessage | null>(null)
  const [confirmingLogout, setConfirmingLogout] = useState(false)
  // 다른 탭의 대기 내용을 확인하는 동안 로그아웃을 다시 받지 않는다
  const checkingLogout = useRef(false)
  const refresh = useCallback(() => setState(session.state()), [session])

  useEffect(() => {
    const offLock = session.onLock(() => {
      // 다시 열었을 때 지난 확인 창·오류가 남지 않게 한다
      setConfirmingLogout(false)
      setSyncError(null)
      refresh()
    })
    const offUnlock = session.onUnlock(() => {
      // 잠금 화면에서 띄운 로그아웃 확인이 볼트 화면에 남지 않게 한다
      setConfirmingLogout(false)
      refresh()
    })
    const offSync = session.onSyncChange((error) => {
      refresh()
      if (error === null) {
        setOffline(false)
        setSyncError(null)
      } else if (error instanceof SessionExpiredError) {
        setNotice(describeError(error).body)
      } else if (!(error instanceof DeviceStorageError)) {
        // 기기 저장 실패는 세션 상태(storedOnDevice)로 경고한다. 다음 저장이 성공할 때까지 남아야 한다
        setSyncError(describeError(error))
      }
    })
    // 다른 탭의 병합·잠금·로그아웃으로 바뀐 상태를 다시 읽는다 (ADR-WEB-001)
    const offRemote = session.onRemoteChange(refresh)
    return () => {
      offLock()
      offUnlock()
      offSync()
      offRemote()
    }
  }, [session, refresh])

  async function logout() {
    // 세션은 이 기기 정리를 먼저 끝내고 서버에 토큰 폐기를 요청한다. 서버 응답을 기다리지 않고 화면을 바꾼다
    const request = session.logout()
    setNotice(null)
    setSyncError(null)
    setConfirmingLogout(false)
    refresh()
    try {
      await request
    } catch (error) {
      // 기기 기록을 지우지 못했으면 공용 기기에 볼트가 남으므로 알린다 (ADR-0019)
      if (error instanceof DeviceStorageError) setNotice(LOGOUT_CLEAR_FAILED)
      // 그 밖에는 서버 토큰 폐기만 실패했다. 이 기기는 이미 로그아웃됐고 서버 토큰은 만료(30일)를 기다린다 (SEC-002)
    }
  }

  /**
   * 올리지 못한 변경 사항이 있으면 사라진다고 먼저 알린다 (VLT-002).
   * 이 탭에 없어도 다른 탭이 기기 기록에 남겼을 수 있으니 기록을 확인한다 (ADR-WEB-001)
   */
  async function requestLogout(pendingSync: boolean) {
    if (pendingSync) {
      setConfirmingLogout(true)
      return
    }
    if (checkingLogout.current) return
    checkingLogout.current = true
    try {
      if (await session.pendingOnDevice()) setConfirmingLogout(true)
      else await logout()
    } finally {
      checkingLogout.current = false
    }
  }

  function logoutConfirm() {
    if (!confirmingLogout) return null
    return (
      <ConfirmPrompt
        title={PENDING_LOGOUT_CONFIRM.title}
        body={PENDING_LOGOUT_CONFIRM.body}
        confirmLabel={PENDING_LOGOUT_CONFIRM.confirm}
        cancelLabel={PENDING_LOGOUT_CONFIRM.cancel}
        onConfirm={() => void logout()}
        onCancel={() => setConfirmingLogout(false)}
      />
    )
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
              if (!sendsToSignIn(error)) throw error
              setNotice(describeError(error).body)
            }
            refresh()
          }}
          onLogout={async () => requestLogout(state.pendingSync)}
          logoutConfirm={logoutConfirm()}
        />
      )
    }

    return (
      <VaultHomeScreen
        email={state.email}
        readOnly={state.vault.readOnly}
        offline={offline || state.pendingSync}
        onLock={() => session.lock()}
        onLogout={() => void requestLogout(state.pendingSync)}
      >
        {logoutConfirm()}
        {syncError && <Alert title={syncError.title}>{syncError.body}</Alert>}
        {!state.storedOnDevice && <Alert>{DEVICE_STORAGE_FAILED}</Alert>}
        {!state.signedIn && (
          <ReconnectPrompt
            email={state.email}
            onReconnect={async (password, options) => {
              try {
                await session.reconnect(password, options)
                setSyncError(null)
              } catch (error) {
                if (!sendsToSignIn(error)) throw error
                setNotice(describeError(error).body)
              } finally {
                refresh()
              }
            }}
          />
        )}
        <ClipboardNotice clipboard={clipboard} />
        <VaultScreen
          vault={state.vault}
          clipboard={clipboard}
          onSave={async (data) => {
            try {
              await session.save(data)
              setSyncError(null)
            } finally {
              // 저장은 잠금 알림을 보내지 않으므로 직접 다시 읽는다. 거절돼도 변경 사항은 먼저 반영돼 있다
              refresh()
            }
          }}
        />
      </VaultHomeScreen>
    )
  }
}
