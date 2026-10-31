// 볼트 값을 복사하고, 실패하면 정책 문구를 보인다 (COM-001)
import { useState } from 'react'
import type { ClipboardPort } from './clipboardPort'

const COPY_FAILED = '클립보드에 복사하지 못했습니다.'

export function useCopy(clipboard: Pick<ClipboardPort, 'copy'>) {
  const [error, setError] = useState<string | null>(null)

  async function copy(text: string): Promise<void> {
    setError(null)
    try {
      await clipboard.copy(text)
    } catch {
      // 탭이 활성 상태가 아니거나 권한이 없으면 브라우저가 거절한다
      setError(COPY_FAILED)
    }
  }

  return { copy, error }
}
