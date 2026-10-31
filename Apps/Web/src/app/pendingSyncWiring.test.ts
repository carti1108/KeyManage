import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PENDING_SYNC_RETRY_MS, startPendingSync } from './pendingSyncWiring'

function setup(pending = true) {
  const session = {
    retryPendingSync: vi.fn(async () => {}),
    hasPendingSync: () => pending,
  }
  const stop = startPendingSync(session)
  return { session, stop }
}

function leavePage(): Event {
  const event = new Event('beforeunload', { cancelable: true })
  window.dispatchEvent(event)
  return event
}

describe('동기화 대기 재전송을 브라우저에 연결 (VLT-001)', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('네트워크가 다시 연결되면 바로 다시 보낸다', () => {
    const { session, stop } = setup()

    window.dispatchEvent(new Event('online'))

    expect(session.retryPendingSync).toHaveBeenCalledOnce()
    stop()
  })

  it('30초마다 다시 보낸다', () => {
    const { session, stop } = setup()

    vi.advanceTimersByTime(PENDING_SYNC_RETRY_MS - 1)
    expect(session.retryPendingSync).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(session.retryPendingSync).toHaveBeenCalledOnce()
    vi.advanceTimersByTime(PENDING_SYNC_RETRY_MS)
    expect(session.retryPendingSync).toHaveBeenCalledTimes(2)
    stop()
  })

  it('대기 중이면(잠겼거나 세션 만료 뒤 남은 것 포함) 탭을 닫거나 새로고침할 때 브라우저 떠남 확인을 띄운다', () => {
    const { stop } = setup(true)

    expect(leavePage().defaultPrevented).toBe(true)
    stop()
  })

  it('대기가 없으면 떠남 확인을 띄우지 않는다', () => {
    const { stop } = setup(false)

    expect(leavePage().defaultPrevented).toBe(false)
    stop()
  })

  it('연결을 끊으면 더는 다시 보내지 않고 떠남 확인도 띄우지 않는다', () => {
    const { session, stop } = setup()

    stop()
    window.dispatchEvent(new Event('online'))
    vi.advanceTimersByTime(PENDING_SYNC_RETRY_MS)

    expect(session.retryPendingSync).not.toHaveBeenCalled()
    expect(leavePage().defaultPrevented).toBe(false)
  })
})
