// 동기화 대기 내용을 재연결·주기마다 다시 보내고, 기기에 저장하지 못한 대기 내용이 있으면 탭을 떠날 때 확인을 받는다 (VLT-001)
import type { VaultSession } from '../domain/session/vaultSession'

/** 대기 내용 재전송 간격 (VLT-001) */
export const PENDING_SYNC_RETRY_MS = 30_000

export type PendingSyncSession = Pick<VaultSession, 'retryPendingSync' | 'hasUnsavedChanges'>

/** @returns 연결 해제 함수 */
export function startPendingSync(session: PendingSyncSession): () => void {
  // 대기가 없거나 분명한 거절 뒤 멈춘 상태면 세션이 요청 없이 끝낸다. 결과는 onSyncChange로 알린다
  const retry = () => void session.retryPendingSync()
  const onBeforeUnload = (event: BeforeUnloadEvent) => {
    // 대기 내용은 기기에 저장되므로, 아직 저장하지 못했을(쓰는 중·실패) 때만 묻는다 (VLT-001)
    if (!session.hasUnsavedChanges()) return
    // 브라우저 기본 떠남 확인을 띄운다. 문구는 브라우저가 정한다
    event.preventDefault()
    event.returnValue = ''
  }

  window.addEventListener('online', retry)
  window.addEventListener('beforeunload', onBeforeUnload)
  const interval = window.setInterval(retry, PENDING_SYNC_RETRY_MS)

  return () => {
    window.removeEventListener('online', retry)
    window.removeEventListener('beforeunload', onBeforeUnload)
    window.clearInterval(interval)
  }
}
