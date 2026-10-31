// 세션 요청 결과(오류, 서버 볼트 되돌림 확인)를 보여 준다
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { ROLLBACK_WARNING } from '../messages/vaultNotices'
import type { useSessionAction } from './useSessionAction'

export function SessionFeedback({ action }: { action: ReturnType<typeof useSessionAction> }) {
  if (action.rollback) {
    return (
      <Alert
        title={ROLLBACK_WARNING.title}
        actions={
          <>
            <Button variant="destructive" size="sm" onClick={action.rollback.accept}>
              {ROLLBACK_WARNING.accept}
            </Button>
            <Button variant="outline" size="sm" onClick={action.rollback.cancel}>
              {ROLLBACK_WARNING.cancel}
            </Button>
          </>
        }
      >
        {ROLLBACK_WARNING.body}
      </Alert>
    )
  }
  if (action.error) return <Alert title={action.error.title}>{action.error.body}</Alert>
  return null
}
