import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createInMemoryTabHub } from '../domain/tabs/inMemoryTabs'
import { PENDING_SYNC_RETRY_MS, startPendingSync } from './pendingSyncWiring'

function setup(pending = true) {
  const session = {
    retryPendingSync: vi.fn(async () => {}),
    hasUnsavedChanges: () => pending,
  }
  const stop = startPendingSync(session)
  return { session, stop }
}

function leavePage(): Event {
  const event = new Event('beforeunload', { cancelable: true })
  window.dispatchEvent(event)
  return event
}

describe('동기화 대기 재전송을 브라우저에 연결 (VLT-002)', () => {
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

  it('기기에 저장하지 못한 대기 내용이 있으면 탭을 닫거나 새로고침할 때 브라우저 떠남 확인을 띄운다', () => {
    const { stop } = setup(true)

    expect(leavePage().defaultPrevented).toBe(true)
    stop()
  })

  it('저장하지 못한 대기 내용이 없으면 떠남 확인을 띄우지 않는다', () => {
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

describe('여러 탭의 대기 내용 재전송 (ADR-WEB-001)', () => {
  it('다른 탭이 재전송 잠금을 잡고 있으면 이번 차례를 건너뛴다', async () => {
    const hub = createInMemoryTabHub()
    let release!: () => void
    const held = hub.connect().lock.run('sync', () => new Promise<void>((resolve) => (release = resolve)))
    const session = { retryPendingSync: vi.fn(async () => {}), hasUnsavedChanges: () => false }
    const stop = startPendingSync(session, hub.connect())

    window.dispatchEvent(new Event('online'))
    await Promise.resolve()

    expect(session.retryPendingSync).not.toHaveBeenCalled()
    release()
    await held
    window.dispatchEvent(new Event('online'))
    await Promise.resolve()
    expect(session.retryPendingSync).toHaveBeenCalledOnce()
    stop()
  })
})
