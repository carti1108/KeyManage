// 무작위 비밀번호 생성 (GEN-001, ADR-0005). 서버로 보내거나 기록하지 않는다

export const MIN_GENERATED_LENGTH = 8
export const MAX_GENERATED_LENGTH = 64

export const UPPERCASE = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
export const LOWERCASE = 'abcdefghijklmnopqrstuvwxyz'
export const DIGITS = '0123456789'
export const SYMBOLS = '!@#$%^&*()_+-=[]{}|;:,.<>?'

export interface GeneratorOptions {
  readonly length: number
  readonly uppercase: boolean
  readonly lowercase: boolean
  readonly digits: boolean
  readonly symbols: boolean
}

export const DEFAULT_GENERATOR_OPTIONS: GeneratorOptions = { length: 16, uppercase: true, lowercase: true, digits: true, symbols: true }

/** 생성을 막는 옵션. length: 8~64 정수가 아님, noCharset: 문자군을 하나도 고르지 않음 */
export class InvalidGeneratorOptionsError extends Error {
  readonly reason: 'length' | 'noCharset'

  constructor(reason: 'length' | 'noCharset') {
    super(reason === 'length' ? '길이는 8~64 정수여야 함' : '문자군을 하나 이상 골라야 함')
    this.name = 'InvalidGeneratorOptionsError'
    this.reason = reason
  }
}

/** 0 이상 2^32 미만의 균등한 정수를 돌려준다 */
export type RandomSource = () => number

const UINT32_RANGE = 2 ** 32

const cryptoRandom: RandomSource = () => globalThis.crypto.getRandomValues(new Uint32Array(1))[0]

// 0 이상 n 미만 균등 정수. 2^32가 n으로 나누어떨어지지 않으면 끝부분 값이 나머지 연산에서 더 자주 나오므로 버린다
function uniformIndex(n: number, random: RandomSource): number {
  const limit = UINT32_RANGE - (UINT32_RANGE % n)
  for (;;) {
    const value = random()
    if (value < limit) return value % n
  }
}

/** @throws InvalidGeneratorOptionsError */
export function generatePassword(options: GeneratorOptions, random: RandomSource = cryptoRandom): string {
  const { length } = options
  if (!Number.isInteger(length) || length < MIN_GENERATED_LENGTH || length > MAX_GENERATED_LENGTH) {
    throw new InvalidGeneratorOptionsError('length')
  }
  const charsets = [
    options.uppercase && UPPERCASE,
    options.lowercase && LOWERCASE,
    options.digits && DIGITS,
    options.symbols && SYMBOLS,
  ].filter((set): set is string => typeof set === 'string')
  if (charsets.length === 0) throw new InvalidGeneratorOptionsError('noCharset')

  // 문자군마다 한 글자씩 강제로 넣으면 위치가 예측되므로, 전체 풀에서 균등하게 뽑고 빠진 문자군이 있으면 통째로 다시 뽑는다
  const pool = charsets.join('')
  for (;;) {
    let password = ''
    for (let i = 0; i < length; i++) password += pool[uniformIndex(pool.length, random)]
    if (charsets.every((set) => [...password].some((char) => set.includes(char)))) return password
  }
}
