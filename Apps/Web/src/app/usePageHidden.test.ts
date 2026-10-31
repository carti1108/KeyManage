import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { usePageHidden } from './usePageHidden'

function setVisibility(state: DocumentVisibilityState) {
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(state)
  document.dispatchEvent(new Event('visibilitychange'))
}

describe('usePageHidden', () => {
  afterEach(() => vi.restoreAllMocks())

  it('탭이 가려지고 다시 보이는 것을 따라간다', () => {
    const { result } = renderHook(() => usePageHidden())
    expect(result.current).toBe(false)

    act(() => setVisibility('hidden'))
    expect(result.current).toBe(true)

    act(() => setVisibility('visible'))
    expect(result.current).toBe(false)
  })
})
