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
