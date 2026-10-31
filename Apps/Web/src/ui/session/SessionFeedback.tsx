// 세션 요청 결과(오류, 서버 볼트 되돌림 확인)를 보여 준다
import { useId } from 'react'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { ROLLBACK_WARNING } from '../messages/vaultNotices'
import type { useSessionAction } from './useSessionAction'

export function SessionFeedback({ action }: { action: ReturnType<typeof useSessionAction> }) {
  const titleId = useId()
  const bodyId = useId()

  if (action.rollback) {
    // 결정을 요구하는 확인이라 alertdialog로 알리고, 받아들이지 않는 쪽에 먼저 포커스를 둔다 (VLT-001)
    return (
      <div
        role="alertdialog"
        aria-labelledby={titleId}
        aria-describedby={bodyId}
        className="flex flex-col gap-1 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
      >
        <p id={titleId} className="font-semibold">
          {ROLLBACK_WARNING.title}
        </p>
        <p id={bodyId}>{ROLLBACK_WARNING.body}</p>
        <div className="mt-2 flex gap-2">
          <Button variant="outline" size="sm" onClick={action.rollback.cancel} autoFocus>
            {ROLLBACK_WARNING.cancel}
          </Button>
          <Button variant="destructive" size="sm" onClick={action.rollback.accept}>
            {ROLLBACK_WARNING.accept}
          </Button>
        </div>
      </div>
    )
  }
  if (action.error) return <Alert title={action.error.title}>{action.error.body}</Alert>
  return null
}
