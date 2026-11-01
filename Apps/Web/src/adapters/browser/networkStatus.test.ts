import { afterEach, describe, expect, it, vi } from 'vitest'
import { isNetworkOnline, subscribeNetworkStatus } from './networkStatus'

describe('networkStatus (VLT-002)', () => {
  afterEach(() => vi.restoreAllMocks())

  it('브라우저가 아는 연결 상태를 읽는다', () => {
    const onLine = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    expect(isNetworkOnline()).toBe(false)

    onLine.mockReturnValue(true)
    expect(isNetworkOnline()).toBe(true)
  })

  it('offline·online 이벤트마다 알리고, 해제하면 더 알리지 않는다', () => {
    const onChange = vi.fn()
    const unsubscribe = subscribeNetworkStatus(onChange)

    window.dispatchEvent(new Event('offline'))
    window.dispatchEvent(new Event('online'))
    expect(onChange).toHaveBeenCalledTimes(2)

    unsubscribe()
    window.dispatchEvent(new Event('offline'))
    expect(onChange).toHaveBeenCalledTimes(2)
  })
})
