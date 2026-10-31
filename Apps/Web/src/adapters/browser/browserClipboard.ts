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
  window.addEventListener('focus', retry)
  document.addEventListener('visibilitychange', onVisibility)
  return () => {
    window.removeEventListener('focus', retry)
    document.removeEventListener('visibilitychange', onVisibility)
  }
}
