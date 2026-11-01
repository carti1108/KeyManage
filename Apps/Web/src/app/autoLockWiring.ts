// 세션·자동 잠금·브라우저 이벤트를 잇는다. 볼트가 열려 있는 동안만 타이머가 돈다 (SEC-003)
// 같은 계정을 연 탭끼리 조작을 나눠 타이머를 함께 연장하고, 보이는 탭의 시간 초과만 모든 탭에 퍼뜨린다 (ADR-WEB-001)
import { bindAutoLockEvents, browserScheduler, isPageVisible } from '../adapters/browser/autoLockEvents'
import { DEFAULT_AUTO_LOCK_MS, createAutoLock } from '../domain/lock/autoLock'
import type { VaultSession } from '../domain/session/vaultSession'
import { createSingleTabFallback } from '../domain/tabs/inMemoryTabs'
import type { Tabs } from '../domain/tabs/tabs'

/** 조작을 다른 탭에 알리는 최소 간격. 키 입력·스크롤마다 보내지 않는다 */
export const ACTIVITY_BROADCAST_INTERVAL_MS = 1_000

export type LockableSession = Pick<VaultSession, 'lock' | 'onLock' | 'onUnlock' | 'state'>

/**
 * @param timeoutMs 자동 잠금 시간. 설정 화면이 생기기 전까지는 기본 5분
 * @param tabs 같은 브라우저의 다른 탭과 나누는 알림. 없으면 탭 하나
 * @returns 연결 해제 함수
 * @throws InvalidAutoLockTimeoutError
 */
export function startAutoLock(session: LockableSession, timeoutMs: number = DEFAULT_AUTO_LOCK_MS, tabs: Tabs = createSingleTabFallback()): () => void {
  const autoLock = createAutoLock({
    timeoutMs,
    now: () => Date.now(),
    scheduler: browserScheduler,
    isVisible: isPageVisible,
    // 보이는 탭의 시간 초과는 모든 탭이 같은 마지막 조작 시각을 가지므로 퍼뜨린다. 나머지는 이 탭만 잠근다
    onTimeout: (cause) => session.lock(cause === 'timer' ? 'allTabs' : 'thisTab'),
  })
  let lastBroadcast = Number.NEGATIVE_INFINITY
  function broadcastActivity() {
    lastBroadcast = Date.now()
    tabs.channel.post('activity')
  }
  const unbindEvents = bindAutoLockEvents({
    activity() {
      autoLock.activity()
      // 잠금 해제된 탭의 조작만 알린다. 받은 탭은 타이머만 다시 잰다
      if (session.state().status !== 'unlocked' || Date.now() - lastBroadcast < ACTIVITY_BROADCAST_INTERVAL_MS) return
      broadcastActivity()
    },
    hidden: () => autoLock.hidden(),
    visible: () => autoLock.visible(),
    suspended: () => autoLock.suspended(),
  })
  const offTabs = tabs.channel.subscribe((kind) => {
    if (kind === 'activity') autoLock.activity()
  })
  const offUnlock = session.onUnlock(() => {
    autoLock.start()
    // 잠금 해제도 조작이다. 숨겨진 다른 탭의 타이머가 곧 끝나 이 탭까지 잠그지 않게 함께 연장한다
    broadcastActivity()
  })
  const offLock = session.onLock(() => autoLock.stop())
  // 연결하는 시점에 이미 열려 있으면 알림을 기다리지 않고 바로 잰다
  if (session.state().status === 'unlocked') autoLock.start()
  return () => {
    offUnlock()
    offLock()
    offTabs()
    unbindEvents()
    autoLock.stop()
  }
}
