// 세션 요청을 한 번에 하나만 보내고, 실패를 정책 문구로 바꾸고, 서버 볼트 되돌림은 확인을 받아 다시 보낸다
import { useRef, useState } from 'react'
import { RevisionRollbackError } from '../../domain/vault/vaultSync'
import { describeError, type ErrorMessage } from '../messages/errorMessage'
import type { RollbackOption } from './sessionPort'

type Action = (options: RollbackOption) => Promise<void>

export function useSessionAction() {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<ErrorMessage | null>(null)
  const [rollbackAction, setRollbackAction] = useState<Action | null>(null)
  // 상태 갱신은 다음 렌더링에 반영되므로, 같은 틱의 두 번째 제출은 ref로 막는다
  const busy = useRef(false)

  async function run(action: Action, options: RollbackOption = {}): Promise<void> {
    if (busy.current) return
    busy.current = true
    setPending(true)
    setError(null)
    setRollbackAction(null)
    try {
      await action(options)
    } catch (caught) {
      if (caught instanceof RevisionRollbackError) setRollbackAction(() => action)
      else setError(describeError(caught))
    } finally {
      busy.current = false
      setPending(false)
    }
  }

  return {
    pending,
    error,
    /** 검사에서 걸린 문제처럼 요청 전에 정한 오류를 보인다 */
    showError: setError,
    run,
    rollback:
      rollbackAction === null
        ? null
        : {
            accept: () => void run(rollbackAction, { acceptRollback: true }),
            cancel: () => setRollbackAction(null),
          },
  }
}
