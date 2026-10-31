// 브라우저 클립보드 쓰기와, 비우지 못한 클립보드를 다시 비울 기회(탭으로 돌아옴) 알림 (COM-001)
import type { ClipboardBurner, ClipboardWriter } from '../../domain/clipboard/clipboardBurner'

/** 탭이 활성 상태가 아니면 브라우저가 거절한다(Promise 실패) */
export const browserClipboard: ClipboardWriter = {
  write: (text) => navigator.clipboard.writeText(text),
}

/** @returns 연결 해제 함수 */
export function bindClipboardRetry(burner: Pick<ClipboardBurner, 'retry'>): () => void {
  const retry = () => void burner.retry()
  const onVisibility = () => {
    if (document.visibilityState === 'visible') retry()
  }
  // Safari 등은 사용자 조작이 있어야 클립보드에 쓸 수 있어 누르거나 키를 칠 때도 다시 시도한다. 비울 것이 없으면 아무것도 하지 않는다
  const userEvents = ['pointerdown', 'keydown'] as const
  const options = { capture: true, passive: true }
  window.addEventListener('focus', retry)
  document.addEventListener('visibilitychange', onVisibility)
  for (const type of userEvents) window.addEventListener(type, retry, options)
  return () => {
    window.removeEventListener('focus', retry)
    document.removeEventListener('visibilitychange', onVisibility)
    for (const type of userEvents) window.removeEventListener(type, retry, options)
  }
}
