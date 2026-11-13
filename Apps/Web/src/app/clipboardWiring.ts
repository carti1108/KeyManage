// 클립보드 소각을 세션 잠금과 브라우저 이벤트에 잇는다 (COM-001, SEC-003)
import { bindClipboardRetry } from '../adapters/browser/browserClipboard'
import type { ClipboardBurner } from '../domain/clipboard/clipboardBurner'
import type { VaultSession } from '../domain/session/sessionTypes'

/** @returns 연결 해제 함수 */
export function startClipboardBurn(session: Pick<VaultSession, 'onLock'>, burner: Pick<ClipboardBurner, 'clear' | 'retry'>): () => void {
  // 잠기면(로그아웃 포함) 30초를 기다리지 않고 바로 비운다
  const offLock = session.onLock(() => void burner.clear())
  const unbind = bindClipboardRetry(burner)
  return () => {
    offLock()
    unbind()
  }
}
