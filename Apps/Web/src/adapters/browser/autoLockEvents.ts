// 브라우저의 조작·화면 전환 이벤트를 자동 잠금에 연결한다 (SEC-003)
import type { AutoLock } from '../../domain/lock/autoLock'

const ACTIVITY_EVENTS = ['keydown', 'pointerdown', 'wheel', 'touchstart'] as const

/** @returns 연결 해제 함수 */
export function bindAutoLockEvents(autoLock: Pick<AutoLock, 'activity' | 'hidden' | 'visible'>): () => void {
  const onActivity = () => autoLock.activity()
  const onVisibility = () => (document.visibilityState === 'hidden' ? autoLock.hidden() : autoLock.visible())
  // 잦은 이벤트라 스크롤 성능을 해치지 않도록 passive로 단다
  const options = { passive: true, capture: true }

  for (const type of ACTIVITY_EVENTS) window.addEventListener(type, onActivity, options)
  document.addEventListener('visibilitychange', onVisibility)

  return () => {
    for (const type of ACTIVITY_EVENTS) window.removeEventListener(type, onActivity, options)
    document.removeEventListener('visibilitychange', onVisibility)
  }
}

/** setTimeout 기반 스케줄러 */
export const browserScheduler = {
  schedule(delayMs: number, run: () => void) {
    const id = window.setTimeout(run, delayMs)
    return () => window.clearTimeout(id)
  },
}
