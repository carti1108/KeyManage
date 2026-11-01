// @vitest-environment node
// 브라우저 전역이 없는 환경(통합 테스트 등)에서도 조립 단계에서 예외를 내지 않는다
import { describe, expect, it } from 'vitest'
import { DEFAULT_AUTO_LOCK_MS } from '../../domain/lock/autoLock'
import { createLocalStorageAutoLockSetting } from './localStorageAutoLockSetting'

describe('createLocalStorageAutoLockSetting (window 없음)', () => {
  it('만들 때 예외를 내지 않고 기본 5분을 읽는다', () => {
    const setting = createLocalStorageAutoLockSetting()

    expect(setting.read()).toBe(DEFAULT_AUTO_LOCK_MS)
  })

  it('쓰면 이번 실행 값으로 쓰고, 구독과 해제는 아무 일도 하지 않는다', () => {
    const setting = createLocalStorageAutoLockSetting()

    setting.write(60_000)
    const off = setting.subscribe(() => {})
    off()

    expect(setting.read()).toBe(60_000)
  })
})
