import { describe, expect, it } from 'vitest'
import {
  DEFAULT_GENERATOR_OPTIONS,
  InvalidGeneratorOptionsError,
  SYMBOLS,
  generatePassword,
  type GeneratorOptions,
} from './passwordGenerator'

const ALL: GeneratorOptions = { length: 16, uppercase: true, lowercase: true, digits: true, symbols: true }

const has = {
  uppercase: (s: string) => /[A-Z]/.test(s),
  lowercase: (s: string) => /[a-z]/.test(s),
  digits: (s: string) => /[0-9]/.test(s),
  symbols: (s: string) => [...s].some((c) => SYMBOLS.includes(c)),
}

describe('generatePassword (GEN-001)', () => {
  it('기본값은 16자, 네 문자군 모두', () => {
    expect(DEFAULT_GENERATOR_OPTIONS).toEqual(ALL)
    expect(generatePassword(DEFAULT_GENERATOR_OPTIONS)).toHaveLength(16)
  })

  it('특수기호는 정책이 정한 26자다', () => {
    expect(SYMBOLS).toBe('!@#$%^&*()_+-=[]{}|;:,.<>?')
    expect([...SYMBOLS]).toHaveLength(26)
  })

  it('1,000번 만들어도 고른 문자군이 모두 들어 있고 고르지 않은 문자는 없다', () => {
    for (let i = 0; i < 1000; i++) {
      const password = generatePassword({ ...ALL, length: 8 })
      expect(password).toHaveLength(8)
      expect(has.uppercase(password) && has.lowercase(password) && has.digits(password) && has.symbols(password)).toBe(true)
    }
    for (let i = 0; i < 200; i++) {
      const password = generatePassword({ length: 12, uppercase: false, lowercase: true, digits: true, symbols: false })
      expect(password).toMatch(/^[a-z0-9]{12}$/)
    }
  })

  it.each([8, 64])('길이 %i는 허용한다', (length) => {
    expect(generatePassword({ ...ALL, length })).toHaveLength(length)
  })

  it.each([7, 65, 12.5, Number.NaN])('길이 %s는 막는다', (length) => {
    expect(() => generatePassword({ ...ALL, length })).toThrow(expect.objectContaining({ reason: 'length' }))
  })

  it('문자군을 하나도 고르지 않으면 막는다', () => {
    const none = { length: 16, uppercase: false, lowercase: false, digits: false, symbols: false }

    expect(() => generatePassword(none)).toThrow(InvalidGeneratorOptionsError)
    expect(() => generatePassword(none)).toThrow(expect.objectContaining({ reason: 'noCharset' }))
  })

  it('빠진 문자군이 있으면 버리고 다시 뽑는다(거부 샘플링)', () => {
    // 풀은 대문자·소문자·숫자·특수기호 순서다. 소문자+숫자면 0~25는 소문자, 26~35는 숫자
    // 첫 시도는 모두 소문자라 버리고, 둘째 시도에서 숫자가 섞인다
    const draws = [0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 26]
    const random = () => draws.shift()!

    const password = generatePassword({ length: 8, uppercase: false, lowercase: true, digits: true, symbols: false }, random)

    expect(password).toBe('abcdefg0')
    expect(draws).toEqual([])
  })

  it('나머지 연산 편향이 생기는 큰 난수는 버린다', () => {
    // 2^32 = 4294967296. 풀 36이면 4294967292 이상은 편향 구간이라 버린다
    const draws = [4294967295, 26, 0, 1, 2, 3, 4, 5, 6]
    const random = () => draws.shift()!

    expect(generatePassword({ length: 8, uppercase: false, lowercase: true, digits: true, symbols: false }, random)).toBe('0abcdefg')
  })
})
