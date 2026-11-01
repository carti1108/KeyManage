// 자동 잠금 시간 설정 (SEC-003, ADR-WEB-002). 기기마다 저장하고 계정과 묶지 않는다
import { DEFAULT_AUTO_LOCK_MS, isAutoLockOption } from './autoLock'

/** 자동 잠금 시간을 기기에 두는 포트. 읽기·쓰기는 동기이고 실패해도 던지지 않는다 */
export interface AutoLockSettingStore {
  /** 저장값. 없거나 선택지가 아니거나 읽지 못하면 기본 5분 */
  read(): number
  /**
   * 바꾼 값을 저장한다. 저장하지 못하면 이번 실행에만 쓴다
   * @throws InvalidAutoLockTimeoutError 선택지가 아닐 때
   */
  write(timeoutMs: number): void
  /** 다른 탭이 바꾼 값을 받는다. 이 탭의 write는 알리지 않는다 */
  subscribe(listener: (timeoutMs: number) => void): () => void
}

// 밀리초를 십진 정수로만 받는다. 공백·부호·소수·지수 표기는 저장한 적 없는 값이다
const CANONICAL_MS = /^(0|[1-9]\d*)$/

/** 저장한 문자열을 선택지로 바꾼다. 그 밖의 값은 기본 5분 */
export function parseAutoLockSetting(raw: string | null): number {
  if (raw === null || !CANONICAL_MS.test(raw)) return DEFAULT_AUTO_LOCK_MS
  const timeoutMs = Number(raw)
  return isAutoLockOption(timeoutMs) ? timeoutMs : DEFAULT_AUTO_LOCK_MS
}
