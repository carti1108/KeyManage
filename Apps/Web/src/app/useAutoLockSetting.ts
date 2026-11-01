// 자동 잠금 시간 설정을 따라가고, 바뀌면 타이머를 새 시간으로 다시 연결한다 (SEC-003, ADR-WEB-002)
import { useCallback, useEffect, useReducer, useSyncExternalStore } from 'react'
import type { AutoLockSettingStore } from '../domain/lock/autoLockSetting'
import type { Tabs } from '../domain/tabs/tabs'
import { startAutoLock, type LockableSession } from './autoLockWiring'

export interface AutoLockSettingState {
  readonly timeoutMs: number
  /** @throws InvalidAutoLockTimeoutError 선택지가 아닐 때 */
  readonly change: (timeoutMs: number) => void
}

export function useAutoLockSetting(session: LockableSession, tabs: Tabs, setting: AutoLockSettingStore): AutoLockSettingState {
  // 저장소를 외부 상태로 읽는다. 구독한 직후에도 다시 읽으므로 첫 렌더와 구독 사이에 다른 탭이 바꾼 값도 놓치지 않는다
  const subscribe = useCallback((notify: () => void) => setting.subscribe(notify), [setting])
  const timeoutMs = useSyncExternalStore(subscribe, setting.read)
  // 이 탭의 write는 구독으로 알리지 않으므로 직접 다시 그린다. 저장에 실패해도 read는 이번 실행의 값을 돌려준다
  const [, rerender] = useReducer((n: number) => n + 1, 0)

  // 시간이 바뀌면 이전 타이머를 풀고 다시 연결한다. 이미 열려 있으면 연결한 순간부터 잰다
  useEffect(() => startAutoLock(session, timeoutMs, tabs), [session, timeoutMs, tabs])

  const change = useCallback(
    (next: number) => {
      setting.write(next)
      rerender()
    },
    [setting],
  )
  return { timeoutMs, change }
}
