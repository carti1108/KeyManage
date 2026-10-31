import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Button } from './Button'

describe('Button', () => {
  it('누르면 onClick을 부르고 폼 제출 버튼이 아니다', () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>저장</Button>)

    const button = screen.getByRole('button', { name: '저장' })
    fireEvent.click(button)

    expect(onClick).toHaveBeenCalledOnce()
    expect(button).toHaveAttribute('type', 'button')
  })

  it('비활성이면 누를 수 없다', () => {
    const onClick = vi.fn()
    render(<Button disabled onClick={onClick}>저장</Button>)

    fireEvent.click(screen.getByRole('button', { name: '저장' }))

    expect(onClick).not.toHaveBeenCalled()
  })

  it('로딩 중이면 바쁨 상태로 알리고 다시 누를 수 없다', () => {
    const onClick = vi.fn()
    render(<Button loading onClick={onClick}>저장</Button>)

    const button = screen.getByRole('button', { name: '저장' })
    fireEvent.click(button)

    expect(onClick).not.toHaveBeenCalled()
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
  })

  it('submit으로 지정하면 폼을 제출한다', () => {
    const onSubmit = vi.fn((e: { preventDefault(): void }) => e.preventDefault())
    render(
      <form onSubmit={onSubmit}>
        <Button type="submit">로그인</Button>
      </form>,
    )

    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(onSubmit).toHaveBeenCalledOnce()
  })
})
