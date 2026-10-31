import { describe, expect, it } from 'vitest'
import { bindAutoLockEvents } from './autoLockEvents'

function recorder() {
  const calls: string[] = []
  return {
    calls,
    autoLock: {
      activity: () => calls.push('activity'),
      hidden: () => calls.push('hidden'),
      visible: () => calls.push('visible'),
      suspended: () => calls.push('suspended'),
    },
  }
}

function setVisibility(state: 'hidden' | 'visible') {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state })
  document.dispatchEvent(new Event('visibilitychange'))
}

describe('브라우저 이벤트를 자동 잠금에 연결 (SEC-003)', () => {
  it('키 입력·포인터·스크롤·터치는 조작으로 알린다', () => {
    const { calls, autoLock } = recorder()
    const unbind = bindAutoLockEvents(autoLock)

    for (const type of ['keydown', 'pointerdown', 'wheel', 'touchstart']) window.dispatchEvent(new Event(type))

    expect(calls).toEqual(['activity', 'activity', 'activity', 'activity'])
    unbind()
  })

  it('페이지가 숨겨지고 다시 보이는 것을 알린다', () => {
    const { calls, autoLock } = recorder()
    const unbind = bindAutoLockEvents(autoLock)

    setVisibility('hidden')
    setVisibility('visible')

    expect(calls).toEqual(['hidden', 'visible'])
    unbind()
  })

  it('탭 동결(freeze)과 페이지 떠남(pagehide)을 알린다', () => {
    const { calls, autoLock } = recorder()
    const unbind = bindAutoLockEvents(autoLock)

    document.dispatchEvent(new Event('freeze'))
    window.dispatchEvent(new Event('pagehide'))

    expect(calls).toEqual(['suspended', 'suspended'])
    unbind()
  })

  it('연결을 풀면 더는 알리지 않는다', () => {
    const { calls, autoLock } = recorder()

    bindAutoLockEvents(autoLock)()
    window.dispatchEvent(new Event('keydown'))
    window.dispatchEvent(new Event('pagehide'))
    setVisibility('hidden')

    expect(calls).toEqual([])
  })
})
