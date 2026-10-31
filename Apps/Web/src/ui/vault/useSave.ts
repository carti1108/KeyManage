// 볼트 저장을 한 번에 하나만 보내고 실패는 정책 문구로 바꾼다
import { useRef, useState } from 'react'
import { describeError, type ErrorMessage } from '../messages/errorMessage'

export function useSave() {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<ErrorMessage | null>(null)
  // 상태 갱신은 다음 렌더링에 반영되므로, 같은 틱의 두 번째 저장은 ref로 막는다
  const busy = useRef(false)

  /** @returns 저장했는지. 진행 중이라 무시했거나 실패하면 false */
  async function run(save: () => Promise<void>): Promise<boolean> {
    if (busy.current) return false
    busy.current = true
    setPending(true)
    setError(null)
    try {
      await save()
      return true
    } catch (caught) {
      setError(describeError(caught))
      return false
    } finally {
      busy.current = false
      setPending(false)
    }
  }

  return { pending, error, showError: setError, run }
}
