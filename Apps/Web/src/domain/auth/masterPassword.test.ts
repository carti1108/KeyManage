import { describe, expect, it } from 'vitest'
import { MIN_MASTER_PASSWORD_LENGTH, checkNewMasterPassword } from './masterPassword'

describe('checkNewMasterPassword (SEC-002)', () => {
  it('최소 길이는 12자다', () => {
    expect(MIN_MASTER_PASSWORD_LENGTH).toBe(12)
  })

  it('12자 미만이면 tooShort, 12자부터 통과한다', () => {
    expect(checkNewMasterPassword('a'.repeat(11), 'a'.repeat(11))).toBe('tooShort')
    expect(checkNewMasterPassword('a'.repeat(12), 'a'.repeat(12))).toBeNull()
  })

  it('길이는 UTF-16 단위가 아니라 코드포인트로 센다', () => {
    // 이모지는 UTF-16으로 2단위라 6개면 length가 12지만 코드포인트는 6개다
    const sixEmoji = '🔑'.repeat(6)
    expect(checkNewMasterPassword(sixEmoji, sixEmoji)).toBe('tooShort')
  })

  it('앞뒤 공백도 그대로 센다', () => {
    const padded = ` ${'a'.repeat(10)} `
    expect(checkNewMasterPassword(padded, padded)).toBeNull()
  })

  it('확인 입력이 다르면 mismatch', () => {
    expect(checkNewMasterPassword('a'.repeat(12), 'a'.repeat(13))).toBe('mismatch')
  })

  it('길이 부족을 불일치보다 먼저 알린다', () => {
    expect(checkNewMasterPassword('short', 'other')).toBe('tooShort')
  })
})
