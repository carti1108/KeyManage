import { describe, expect, it } from 'vitest'
import { isWeak, scorePassword } from './passwordStrength'

describe('scorePassword (GEN-001)', () => {
  it.each([
    ['123456', 19],
    ['abc~~~~~', 40],
    ['Aa1!Aa1!Aa1!', 77],
    ['Aa1!Aa1!Aa1!Aa1!', 100],
  ])('플랫폼 공통 샘플 %s → %i점', (password, score) => {
    expect(scorePassword(password)).toBe(score)
  })

  it('빈 문자열은 0점', () => {
    expect(scorePassword('')).toBe(0)
  })

  it('그 외 문자는 몇 종류가 있어도 풀에 7만 한 번 더한다', () => {
    // 소문자 26 + 그 외 7 = 33. 8 × log₂33 = 40.35
    expect(scorePassword('ab~~€€한한')).toBe(40)
  })

  it('길이는 코드포인트로 센다', () => {
    // 이모지 4개: UTF-16으로 8단위지만 코드포인트는 4. 4 × log₂7 = 11.2
    expect(scorePassword('🔑🔑🔑🔑')).toBe(11)
  })

  it('60점 미만이면 취약하다', () => {
    expect(isWeak(59)).toBe(true)
    expect(isWeak(60)).toBe(false)
  })
})
