import { describe, expect, it } from 'vitest'
import { AUTO_LOCK_OPTIONS_MS, DEFAULT_AUTO_LOCK_MS } from './autoLock'
import { parseAutoLockSetting } from './autoLockSetting'

describe('자동 잠금 설정 저장값 검증 (SEC-003, ADR-WEB-002)', () => {
  it.each(Object.values(AUTO_LOCK_OPTIONS_MS))('선택지 %i는 그대로 쓴다', (ms) => {
    expect(parseAutoLockSetting(String(ms))).toBe(ms)
  })

  it('"즉시"(0)도 선택지다', () => {
    expect(parseAutoLockSetting('0')).toBe(0)
  })

  it.each([
    ['값 없음', null],
    ['빈 문자열', ''],
    ['음수', '-60000'],
    ['음수 0', '-0'],
    ['숫자 아님', 'abc'],
    ['NaN', 'NaN'],
    ['소수', '300000.5'],
    ['선택지가 아닌 정수', '120000'],
    ['앞뒤 공백', ' 60000 '],
    ['지수 표기', '6e4'],
    ['Infinity', 'Infinity'],
  ])('%s이면 기본 5분', (_name, raw) => {
    expect(parseAutoLockSetting(raw)).toBe(DEFAULT_AUTO_LOCK_MS)
  })
})
