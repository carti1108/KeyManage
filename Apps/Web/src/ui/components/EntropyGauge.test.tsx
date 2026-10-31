import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { EntropyGauge } from './EntropyGauge'

describe('EntropyGauge', () => {
  it.each([
    [0, '취약함'],
    [59, '취약함'],
    [60, '보통'],
    [79, '보통'],
    [80, '초강력'],
    [100, '초강력'],
  ])('%i점은 %s으로 표시한다 (GEN-001 경계)', (score, label) => {
    render(<EntropyGauge score={score} />)

    const meter = screen.getByRole('meter')
    expect(meter).toHaveAttribute('aria-valuenow', String(score))
    expect(meter).toHaveAttribute('aria-valuetext', `${score}점, ${label}`)
    expect(screen.getByText(label)).toBeInTheDocument()
  })

  it('범위를 벗어나거나 숫자가 아닌 점수는 0~100 정수로 맞춘다', () => {
    const { rerender } = render(<EntropyGauge score={150} />)
    expect(screen.getByRole('meter')).toHaveAttribute('aria-valuenow', '100')

    rerender(<EntropyGauge score={-5} />)
    expect(screen.getByRole('meter')).toHaveAttribute('aria-valuenow', '0')

    rerender(<EntropyGauge score={Number.NaN} />)
    expect(screen.getByRole('meter')).toHaveAttribute('aria-valuenow', '0')

    rerender(<EntropyGauge score={79.9} />)
    expect(screen.getByRole('meter')).toHaveAttribute('aria-valuenow', '79')
  })

  it('상태 문구를 숨겨도 보조 기술에는 상태를 알린다', () => {
    render(<EntropyGauge score={90} showLabel={false} />)

    expect(screen.queryByText('초강력')).not.toBeInTheDocument()
    expect(screen.getByRole('meter')).toHaveAttribute('aria-valuetext', '90점, 초강력')
  })
})
