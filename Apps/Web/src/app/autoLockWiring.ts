// 세션·자동 잠금·브라우저 이벤트를 잇는다. 볼트가 열려 있는 동안만 타이머가 돈다 (SEC-003)
import { bindAutoLockEvents, browserScheduler } from '../adapters/browser/autoLockEvents'
import { DEFAULT_AUTO_LOCK_MS, createAutoLock } from '../domain/lock/autoLock'
import type { VaultSession } from '../domain/session/vaultSession'

export type LockableSession = Pick<VaultSession, 'lock' | 'onLock' | 'onUnlock' | 'state'>

/**
 * @param timeoutMs 자동 잠금 시간. 설정 화면이 생기기 전까지는 기본 5분
 * @returns 연결 해제 함수
 * @throws InvalidAutoLockTimeoutError
 */
export function startAutoLock(session: LockableSession, timeoutMs: number = DEFAULT_AUTO_LOCK_MS): () => void {
  const autoLock = createAutoLock({ timeoutMs, now: () => Date.now(), scheduler: browserScheduler, onTimeout: () => session.lock() })
  const unbindEvents = bindAutoLockEvents(autoLock)
  const offUnlock = session.onUnlock(() => autoLock.start())
  const offLock = session.onLock(() => autoLock.stop())
  // 연결하는 시점에 이미 열려 있으면 알림을 기다리지 않고 바로 잰다
  if (session.state().status === 'unlocked') autoLock.start()
  return () => {
    offUnlock()
    offLock()
    unbindEvents()
    autoLock.stop()
  }
}
