// 동기화 대기 내용을 재연결·주기마다 다시 보내고, 대기 중에 탭을 떠나면 확인을 받는다 (VLT-001)
import type { VaultSession } from '../domain/session/vaultSession'

/** 대기 내용 재전송 간격 (VLT-001) */
export const PENDING_SYNC_RETRY_MS = 30_000

export type PendingSyncSession = Pick<VaultSession, 'retryPendingSync'> & {
  state(): { readonly status: string; readonly pendingSync?: boolean }
}

/** @returns 연결 해제 함수 */
export function startPendingSync(session: PendingSyncSession): () => void {
  // 세션이 대기가 없거나 되돌림 확인 전이면 요청 없이 끝내고, 결과는 onSyncChange로 알린다
  const retry = () => void session.retryPendingSync()
  const onBeforeUnload = (event: BeforeUnloadEvent) => {
    const state = session.state()
    if (state.status !== 'unlocked' || state.pendingSync !== true) return
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
