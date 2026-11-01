import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useNetworkOffline } from './useNetworkOffline'

function setOnline(online: boolean) {
  vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(online)
  window.dispatchEvent(new Event(online ? 'online' : 'offline'))
}

describe('useNetworkOffline (VLT-002)', () => {
  afterEach(() => vi.restoreAllMocks())

  it('브라우저의 오프라인 전환과 재연결을 따라간다', () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    const { result } = renderHook(() => useNetworkOffline())
    expect(result.current).toBe(false)

    act(() => setOnline(false))
    expect(result.current).toBe(true)

    act(() => setOnline(true))
    expect(result.current).toBe(false)
  })

  it('시작할 때 이미 오프라인이면 처음부터 오프라인으로 본다', () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    const { result } = renderHook(() => useNetworkOffline())

    expect(result.current).toBe(true)
  })
})
