// 브라우저의 조작·화면 전환 이벤트를 자동 잠금에 연결한다 (SEC-003)
import type { AutoLock } from '../../domain/lock/autoLock'

const ACTIVITY_EVENTS = ['keydown', 'pointerdown', 'wheel', 'touchstart'] as const

/** @returns 연결 해제 함수 */
export function bindAutoLockEvents(autoLock: Pick<AutoLock, 'activity' | 'hidden' | 'visible' | 'suspended'>): () => void {
  const onActivity = () => autoLock.activity()
  // 동결된 탭이나 뒤로 가기 캐시(bfcache)에 들어간 페이지는 타이머가 돌지 않으므로 바로 잠근다
  const onSuspend = () => autoLock.suspended()
  const onVisibility = () => (document.visibilityState === 'hidden' ? autoLock.hidden() : autoLock.visible())
  // 잦은 이벤트라 스크롤 성능을 해치지 않도록 passive로 단다
  const options = { passive: true, capture: true }

  for (const type of ACTIVITY_EVENTS) window.addEventListener(type, onActivity, options)
  document.addEventListener('visibilitychange', onVisibility)
  document.addEventListener('freeze', onSuspend)
  window.addEventListener('pagehide', onSuspend)

  return () => {
    for (const type of ACTIVITY_EVENTS) window.removeEventListener(type, onActivity, options)
    document.removeEventListener('visibilitychange', onVisibility)
    document.removeEventListener('freeze', onSuspend)
    window.removeEventListener('pagehide', onSuspend)
  }
}

/** 탭이 지금 화면에 보이는지 */
export function isPageVisible(): boolean {
  return document.visibilityState !== 'hidden'
}

/** setTimeout 기반 스케줄러 */
export const browserScheduler = {
  schedule(delayMs: number, run: () => void) {
    const id = window.setTimeout(run, delayMs)
    return () => window.clearTimeout(id)
  },
}
