import { afterEach, describe, expect, it, vi } from 'vitest'
import { startClipboardBurn } from './clipboardWiring'

function setup() {
  const lockListeners = new Set<() => void>()
  const session = { onLock: (listener: () => void) => (lockListeners.add(listener), () => lockListeners.delete(listener)) }
  const burner = { clear: vi.fn(async () => {}), retry: vi.fn(async () => {}) }
  const stop = startClipboardBurn(session, burner)
  return { burner, stop, lock: () => lockListeners.forEach((listener) => listener()), lockListeners }
}

function setVisibility(state: 'hidden' | 'visible') {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state })
  document.dispatchEvent(new Event('visibilitychange'))
}

describe('클립보드 소각을 세션과 브라우저에 연결 (COM-001)', () => {
  afterEach(() => setVisibility('visible'))

  it('볼트가 잠기면 바로 비운다', () => {
    const { burner, lock, stop } = setup()

    lock()

    expect(burner.clear).toHaveBeenCalledOnce()
    stop()
  })

  it('탭으로 돌아오면(포커스·다시 보임) 비우지 못한 것을 다시 비운다', () => {
    const { burner, stop } = setup()

    window.dispatchEvent(new Event('focus'))
    setVisibility('hidden')
    setVisibility('visible')

    expect(burner.retry).toHaveBeenCalledTimes(2)
    stop()
  })

  it('연결을 끊으면 더는 반응하지 않는다', () => {
    const { burner, stop, lockListeners } = setup()

    stop()
    window.dispatchEvent(new Event('focus'))

    expect(burner.retry).not.toHaveBeenCalled()
    expect(lockListeners.size).toBe(0)
  })
})
