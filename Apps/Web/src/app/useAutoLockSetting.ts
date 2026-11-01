// 자동 잠금 시간 설정을 상태로 들고, 바뀌면 타이머를 새 시간으로 다시 연결한다 (SEC-003, ADR-WEB-002)
import { useCallback, useEffect, useState } from 'react'
import type { AutoLockSettingStore } from '../domain/lock/autoLockSetting'
import type { Tabs } from '../domain/tabs/tabs'
import { startAutoLock, type LockableSession } from './autoLockWiring'

export interface AutoLockSettingState {
  readonly timeoutMs: number
  /** @throws InvalidAutoLockTimeoutError 선택지가 아닐 때 */
  readonly change: (timeoutMs: number) => void
}

export function useAutoLockSetting(session: LockableSession, tabs: Tabs, setting: AutoLockSettingStore): AutoLockSettingState {
  const [timeoutMs, setTimeoutMs] = useState(() => setting.read())

  // 다른 탭에서 바꾼 값을 받는다
  useEffect(() => setting.subscribe(setTimeoutMs), [setting])
  // 시간이 바뀌면 이전 타이머를 풀고 다시 연결한다. 이미 열려 있으면 연결한 순간부터 잰다
  useEffect(() => startAutoLock(session, timeoutMs, tabs), [session, timeoutMs, tabs])

  const change = useCallback(
    (next: number) => {
      setting.write(next)
      setTimeoutMs(next)
    },
    [setting],
  )
  return { timeoutMs, change }
}
