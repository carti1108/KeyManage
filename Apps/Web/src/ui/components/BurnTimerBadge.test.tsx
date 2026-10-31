import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { BurnTimerBadge } from './BurnTimerBadge'

describe('BurnTimerBadge', () => {
  it('남은 초를 보이고 보조 기술에 의미를 알린다', () => {
    render(<BurnTimerBadge remainingSeconds={30} />)

    expect(screen.getByText('30s')).toBeInTheDocument()
    expect(screen.getByRole('timer')).toHaveAccessibleName('클립보드 자동 삭제까지 30초')
  })

  it('0초에 닿으면 onBurned를 한 번만 부른다', () => {
    const onBurned = vi.fn()
    const { rerender } = render(<BurnTimerBadge remainingSeconds={1} onBurned={onBurned} />)
    expect(onBurned).not.toHaveBeenCalled()

    rerender(<BurnTimerBadge remainingSeconds={0} onBurned={onBurned} />)
    rerender(<BurnTimerBadge remainingSeconds={0} onBurned={onBurned} />)

    expect(onBurned).toHaveBeenCalledOnce()
  })

  it('남은 시간을 0~전체 시간 범위로 맞춘다', () => {
    const { rerender } = render(<BurnTimerBadge remainingSeconds={45} totalSeconds={30} />)
    expect(screen.getByText('30s')).toBeInTheDocument()

    rerender(<BurnTimerBadge remainingSeconds={-3} totalSeconds={30} />)
    expect(screen.getByText('0s')).toBeInTheDocument()
  })
})
