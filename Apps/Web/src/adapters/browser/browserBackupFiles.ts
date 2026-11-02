// 백업 파일 저장(브라우저 다운로드)과 고르기(파일 선택 창) (VLT-004)
// 데스크톱 웹뷰(WebView2)도 같은 다운로드 방식을 쓴다. 파일에는 암호문만 있다
import type { BackupFileSource, BackupFiles } from '../../domain/backup/backupFile'

/** 다운로드가 Blob 주소를 읽기 시작할 시간을 둔 뒤 해제한다. 바로 해제하면 일부 브라우저가 내려받지 못한다 */
export const REVOKE_AFTER_MS = 60_000

export const browserBackupFiles: BackupFiles = {
  save({ name, text }) {
    const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }))
    const link = document.createElement('a')
    link.href = url
    link.download = name
    link.rel = 'noopener'
    link.hidden = true
    // Firefox는 문서에 붙은 링크만 누를 수 있다
    document.body.append(link)
    try {
      link.click()
    } finally {
      link.remove()
      setTimeout(() => URL.revokeObjectURL(url), REVOKE_AFTER_MS)
    }
  },

  /**
   * cancel 이벤트가 없는 브라우저에서 취소하면 끝나지 않는다. 시간으로 취소를 짐작하면 네트워크 드라이브처럼
   * change가 늦게 오는 정상 선택을 놓치므로, 화면이 기다리는 동안 버튼을 막지 않고 최신 선택만 쓴다
   */
  pick() {
    return new Promise<BackupFileSource | null>((resolve) => {
      const input = document.createElement('input')
      input.type = 'file'
      input.accept = '.json,application/json'
      const finish = (file: BackupFileSource | null) => {
        input.removeEventListener('change', onChange)
        input.removeEventListener('cancel', onCancel)
        resolve(file)
      }
      const onChange = () => finish(input.files?.[0] ?? null)
      // 선택 창을 닫으면 cancel 이벤트가 온다(Chromium·WebView2, Safari 16.4+, Firefox 91+)
      const onCancel = () => finish(null)
      input.addEventListener('change', onChange)
      input.addEventListener('cancel', onCancel)
      input.click()
    })
  },
}
