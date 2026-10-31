// 화면 테스트용 클립보드. 실제 소각 타이머를 쓰되 쓰기는 기록만 하고 시간은 테스트가 정한다
import { vi } from 'vitest'
import { createClipboardBurner } from '../domain/clipboard/clipboardBurner'

export function createFakeClipboard() {
  const writes: string[] = []
  let time = 0
  let pending: (() => void) | null = null
  const writer = {
    write: vi.fn(async (text: string) => {
      writes.push(text)
    }),
  }
  const burner = createClipboardBurner({
    clipboard: writer,
    // 타이머는 테스트가 elapse로 직접 터뜨린다
    scheduler: { schedule: (_delay, run) => ((pending = run), () => (pending = null)) },
    now: () => time,
  })
  return {
    clipboard: burner,
    writer,
    writes,
    /** 시계를 옮긴다. burn이면 예약된 비우기를 실행한다 */
    elapse(ms: number, burn = false) {
      time += ms
      if (burn) pending?.()
    },
    now: () => time,
  }
}
