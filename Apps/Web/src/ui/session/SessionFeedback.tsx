// 세션 요청 결과(오류, 서버 볼트 되돌림 확인)를 보여 준다
import { Alert } from '../components/Alert'
import { ConfirmPrompt } from '../components/ConfirmPrompt'
import { ROLLBACK_WARNING } from '../messages/vaultNotices'
import type { useSessionAction } from './useSessionAction'

export function SessionFeedback({ action }: { action: ReturnType<typeof useSessionAction> }) {
  if (action.rollback) {
    return (
      <ConfirmPrompt
        title={ROLLBACK_WARNING.title}
        body={ROLLBACK_WARNING.body}
        confirmLabel={ROLLBACK_WARNING.accept}
        cancelLabel={ROLLBACK_WARNING.cancel}
        onConfirm={action.rollback.accept}
        onCancel={action.rollback.cancel}
      />
    )
  }
  if (action.error) return <Alert title={action.error.title}>{action.error.body}</Alert>
  return null
}
