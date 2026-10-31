// 비밀번호 강도 점수 (GEN-001). 실제로 들어 있는 문자군으로 풀 크기를 정한다
import { DIGITS, LOWERCASE, SYMBOLS, UPPERCASE } from './passwordGenerator'

const MAX_SCORE = 100
/** 이 점수 미만이면 취약. 경고만 하고 사용은 막지 않는다 */
export const WEAK_BELOW = 60
const OTHER_POOL_SIZE = 7

const KNOWN_SETS = [UPPERCASE, LOWERCASE, DIGITS, SYMBOLS]

/** min(100, floor(길이 × log₂(풀 크기))). 길이는 코드포인트 수, 빈 문자열은 0점 */
export function scorePassword(password: string): number {
  const chars = [...password]
  if (chars.length === 0) return 0
  let pool = 0
  for (const set of KNOWN_SETS) {
    if (chars.some((char) => set.includes(char))) pool += set.length
  }
  // 네 문자군에 없는 문자(한글, 이모지, ~ 등)는 몇 종류든 7을 한 번만 더한다
  if (chars.some((char) => !KNOWN_SETS.some((set) => set.includes(char)))) pool += OTHER_POOL_SIZE
  return Math.min(MAX_SCORE, Math.floor(chars.length * Math.log2(pool)))
}

export function isWeak(score: number): boolean {
  return score < WEAK_BELOW
}

const SEQUENCE_MIN_LENGTH = 3

/**
 * 3자리 이상 1씩 오르거나 내리는 숫자(123, 987)가 있는지. 점수와 별개로 안내만 한다 (GEN-001).
 * 9→0, 0→9로 넘어가는 경우는 보지 않고, 사이에 다른 문자가 끼면 끊긴 것으로 본다
 */
export function hasSequentialDigits(password: string): boolean {
  let run = 1
  let step = 0
  const chars = [...password]
  for (let i = 1; i < chars.length; i++) {
    const diff = DIGITS.includes(chars[i - 1]) && DIGITS.includes(chars[i]) ? Number(chars[i]) - Number(chars[i - 1]) : 0
    if (diff === 1 || diff === -1) {
      // 방향이 바뀌면(121) 직전 두 글자부터 새로 센다
      run = diff === step ? run + 1 : 2
      step = diff
      if (run >= SEQUENCE_MIN_LENGTH) return true
    } else {
      run = 1
      step = 0
    }
  }
  return false
}
