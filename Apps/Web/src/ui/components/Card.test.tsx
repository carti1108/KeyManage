import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Card } from './Card'

describe('Card', () => {
  it('기본 카드는 내용을 담는 영역이며 버튼이 아니다', () => {
    render(<Card>항목</Card>)

    expect(screen.getByText('항목')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('누를 수 있는 카드는 버튼으로 노출되어 키보드로도 고를 수 있다', () => {
    const onClick = vi.fn()
    render(
      <Card interactive onClick={onClick}>
        github.com
      </Card>,
    )

    const card = screen.getByRole('button', { name: 'github.com' })
    fireEvent.click(card)

    expect(onClick).toHaveBeenCalledOnce()
    expect(card).toHaveAttribute('type', 'button')
  })
})
